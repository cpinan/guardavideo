"""Casos de uso. No conoce FastAPI: se puede probar sin HTTP."""

import asyncio
import logging
import shutil
import threading
import uuid
from collections.abc import AsyncIterator, Sequence
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from pathlib import Path

from app.config import Settings
from app.domain import Job, Status
from app.downloaders.base import DownloadContext, Downloader
from app.downloaders.debug import is_debug_url
from app.downloaders.registry import select_downloader
from app.exceptions import DownloadCancelledError, InvalidJobStateError, JobNotFoundError
from app.hashing import file_sha256, url_key
from app.media import probe_duration
from app.repositories import JobRepository
from app.security import assert_public_url

logger = logging.getLogger(__name__)


class JobService:
    def __init__(
        self, repo: JobRepository, downloaders: Sequence[Downloader], settings: Settings
    ) -> None:
        self._repo = repo
        self._downloaders = downloaders
        self._settings = settings
        self._executor = ThreadPoolExecutor(
            max_workers=settings.max_concurrent_jobs, thread_name_prefix="job"
        )
        self._cancel_events: dict[str, threading.Event] = {}
        self._generation: dict[str, int] = {}  # invalida tareas viejas tras un retry
        self._lock = threading.Lock()  # protege las transiciones de estado

    # ---- API del servicio ----
    def submit(self, url: str, quality: int | None) -> Job:
        if not (self._settings.debug and is_debug_url(url)):  # escenarios simulados: sin red
            assert_public_url(url, allow_private=self._settings.allow_private_urls)  # falla rápido
        job = Job(
            id=uuid.uuid4().hex[:12], url=url, quality=quality, url_hash=url_key(url, quality)
        )
        self._repo.add(job)
        self._cancel_events[job.id] = threading.Event()
        self._generation[job.id] = 0
        self._executor.submit(self._run, job.id, 0)
        return job

    def find_existing(self, url: str, quality: int | None) -> Job | None:
        """Un job de la misma URL+calidad que ya está en curso o ya tiene su archivo en disco."""
        key = url_key(url, quality)
        for job in reversed(self._repo.list()):
            if job.url_hash != key:
                continue
            if job.status in (Status.queued, Status.running):
                return job
            if job.status == Status.done and job.file_path and job.file_path.exists():
                return job
        return None

    def request_download(
        self, url: str, quality: int | None, *, force: bool = False
    ) -> tuple[Job, bool]:
        """Encola una descarga, salvo que ya exista: devuelve (job, reutilizado)."""
        if not force and (existing := self.find_existing(url, quality)):
            logger.info("reutiliza %s para %s", existing.id, url)
            return existing, True
        return self.submit(url, quality), False

    def retry(self, job_id: str) -> Job:
        """Vuelve a encolar un job fallido o cancelado, conservando su id y su URL."""
        with self._lock:
            job = self._repo.get(job_id)
            if job.status not in (Status.error, Status.cancelled):
                raise InvalidJobStateError(
                    f"Solo se reintenta un job fallido o cancelado ({job.status.value})"
                )
            gen = self._generation.get(job_id, 0) + 1  # jobs cargados de la BD no tienen entrada
            self._generation[job_id] = gen
            self._cancel_events[job_id] = threading.Event()
            self._repo.update(
                job_id,
                status=Status.queued,
                percent=0,
                error=None,
                retries=0,
                file_path=None,
                thumbnail_path=None,
            )
            self._executor.submit(self._run, job_id, gen)
            return self._repo.get(job_id)

    def get(self, job_id: str) -> Job:
        return self._repo.get(job_id)

    def list(self) -> list[Job]:
        return self._repo.list()

    def cancel(self, job_id: str) -> Job:
        with self._lock:
            job = self._repo.get(job_id)
            if job.is_terminal:
                raise InvalidJobStateError(f"El job ya terminó ({job.status.value})")
            self._cancel_events[job_id].set()
            if job.status == Status.queued:
                self._repo.update(job_id, status=Status.cancelled)
            return self._repo.get(job_id)  # si está running, el worker lo marcará al abortar

    def thumbnail_for(self, job_id: str) -> Path:
        job = self._repo.get(job_id)
        if job.thumbnail_path is None or not job.thumbnail_path.exists():
            raise JobNotFoundError(f"{job_id}: sin miniatura")
        return job.thumbnail_path

    def set_starred(self, job_id: str, starred: bool) -> Job:
        job = self._repo.get(job_id)
        if job.status != Status.done:
            raise InvalidJobStateError("Solo los videos terminados se pueden marcar como favoritos")
        return self._repo.update(job_id, starred=starred)

    def record_view(self, job_id: str) -> Job:
        """Suma una reproducción (la UI la manda una vez por sesión de reproductor)."""
        with self._lock:  # lectura + escritura atómica: dos pestañas no pisan el contador
            job = self._repo.get(job_id)
            if job.status != Status.done:
                raise InvalidJobStateError("El video aún no está listo")
            return self._repo.update(
                job_id, view_count=job.view_count + 1, last_viewed_at=datetime.now(UTC)
            )

    def stats(self) -> dict[str, int]:
        """Resumen de la biblioteca. Un archivo compartido por varios jobs se cuenta una vez."""
        files: dict[Path, int] = {}
        for job in self._repo.list():
            if job.status == Status.done and job.file_path is not None and job.file_path.exists():
                files[job.file_path] = job.file_path.stat().st_size
        folder = Path(self._settings.download_dir)
        return {
            "videos": len(files),
            "bytes": sum(files.values()),
            "disk_free_bytes": shutil.disk_usage(folder).free if folder.exists() else 0,
        }

    def delete(self, job_id: str) -> None:
        """Quita del historial un job terminado, junto con su video y su miniatura."""
        with self._lock:
            job = self._repo.get(job_id)
            if not job.is_terminal:
                raise InvalidJobStateError("Cancela la descarga antes de eliminarla")
            root = Path(self._settings.download_dir).resolve()
            shared = job.file_path is not None and any(
                o.id != job_id and o.file_path == job.file_path for o in self._repo.list()
            )  # el mismo archivo puede servir a varios jobs (contenido idéntico)
            for path in (None if shared else job.file_path, job.thumbnail_path):
                # defensivo: nunca borrar fuera de la carpeta de descargas, aunque la BD lo diga
                if path is not None and path.resolve().is_relative_to(root):
                    path.unlink(missing_ok=True)
            self._repo.delete(job_id)  # (sin _cleanup: borraría un archivo compartido)
            self._cancel_events.pop(job_id, None)
            self._generation.pop(job_id, None)

    def file_for(self, job_id: str) -> Job:
        job = self._repo.get(job_id)
        if job.status != Status.done or job.file_path is None:
            raise InvalidJobStateError("La descarga aún no termina")
        return job

    async def watch(
        self, job_id: str, poll: float = 0.3, heartbeat: float = 15.0
    ) -> AsyncIterator[Job | None]:
        """Emite el job cuando cambia, y None como latido para mantener viva la conexión."""
        last: Job | None = None
        idle = 0.0
        while True:
            job = self._repo.get(job_id)
            if job != last:
                last, idle = job, 0.0
                yield job
                if job.is_terminal:
                    return
            elif idle >= heartbeat:
                idle = 0.0
                yield None
            await asyncio.sleep(poll)
            idle += poll

    def shutdown(self) -> None:
        for ev in self._cancel_events.values():
            ev.set()
        self._executor.shutdown(wait=True, cancel_futures=True)

    # ---- Ejecución en el hilo del worker ----
    def _run(self, job_id: str, gen: int) -> None:
        with self._lock:
            if self._generation.get(job_id) != gen:
                return  # tarea vieja: el job fue reencolado con otro retry
            cancel = self._cancel_events[job_id]
            if cancel.is_set():
                self._repo.update(job_id, status=Status.cancelled)
                return
            self._repo.update(job_id, status=Status.running)
        job = self._repo.get(job_id)

        def report(percent: float) -> None:
            self._repo.update(job_id, percent=percent)

        def report_retry(attempt: int) -> None:
            self._repo.update(job_id, retries=attempt)

        def report_title(title: str) -> None:
            self._repo.update(job_id, title=title[:200])

        def report_thumbnail(path: Path) -> None:
            rev = self._repo.get(job_id).thumbnail_rev + 1
            self._repo.update(job_id, thumbnail_path=path, thumbnail_rev=rev)

        ctx = DownloadContext(
            report_percent=report,
            cancel=cancel,
            report_retry=report_retry,
            report_thumbnail=report_thumbnail,
            report_title=report_title,
        )
        try:
            downloader = select_downloader(job.url, self._downloaders)
            logger.info("job %s: %s -> %s", job_id, downloader.name, job.url)
            result = downloader.download(
                job.url, self._settings.download_dir, job_id, job.quality, ctx
            )
            path, digest = self._dedupe_by_content(job_id, result.path, cancel)
            self._repo.update(
                job_id,
                status=Status.done,
                percent=100,
                retries=0,
                size_bytes=path.stat().st_size,
                duration_seconds=probe_duration(path),
                title=result.path.stem if result.title is None else result.title,
                file_path=path,
                content_hash=digest,
            )
        except DownloadCancelledError:
            self._finish(job_id, Status.cancelled)
        except Exception as e:  # noqa: BLE001 - cualquier fallo se reporta en el job
            if cancel.is_set():
                self._finish(job_id, Status.cancelled)
            else:
                logger.exception("job %s falló", job_id)
                self._finish(job_id, Status.error, error=str(e)[:300])

    def _dedupe_by_content(
        self, job_id: str, path: Path, cancel: threading.Event
    ) -> tuple[Path, str]:
        """Si otro job ya tiene un archivo idéntico (mismo SHA-256), conserva solo uno."""
        digest = file_sha256(path, cancel)
        for other in self._repo.list():
            twin = other.file_path
            if (
                other.id != job_id
                and other.status == Status.done
                and other.content_hash == digest
                and twin is not None
                and twin.exists()
            ):
                logger.info(
                    "job %s: contenido idéntico a %s; se reutiliza el archivo", job_id, other.id
                )
                path.unlink(missing_ok=True)
                return twin, digest
        return path, digest

    def _finish(self, job_id: str, status: Status, error: str | None = None) -> None:
        self._cleanup(job_id)
        self._repo.update(job_id, status=status, error=error, retries=0, thumbnail_path=None)

    def _cleanup(self, job_id: str) -> None:
        """Borra restos parciales (.part, archivos temporales de yt-dlp)."""
        folder = Path(self._settings.download_dir)
        for p in [*folder.glob(f"{job_id}.*"), *folder.glob(f"{job_id}_thumb*")]:
            p.unlink(missing_ok=True)
