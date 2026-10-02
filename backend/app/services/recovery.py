"""Reconciliación al arrancar: la base de datos puede no coincidir con lo que hay en disco."""

import logging
from pathlib import Path

from app.domain import Status
from app.hashing import url_key
from app.media import probe_duration
from app.repositories import JobRepository

logger = logging.getLogger(__name__)

INTERRUPTED = "Interrumpido: el servidor se reinició mientras descargaba"
MISSING_FILE = "El archivo ya no existe en disco"


def recover_jobs(repo: JobRepository, download_dir: Path) -> int:
    """Deja el historial coherente. Devuelve cuántos jobs se corrigieron.

    - En cola/descargando: ningún worker los tiene tras un reinicio -> error (se pueden reintentar).
    - Listo pero sin archivo: el usuario lo borró a mano -> error, para poder volver a bajarlo.
    - Miniatura desaparecida: se olvida la ruta.
    """
    fixed = 0
    for job in repo.list():
        if job.url_hash is None:  # filas anteriores a la migración v2
            job = repo.update(job.id, url_hash=url_key(job.url, job.quality))  # noqa: PLW2901
        if job.status in (Status.queued, Status.running):
            for partial in [
                *download_dir.glob(f"{job.id}.*"),
                *download_dir.glob(f"{job.id}_thumb*"),
            ]:
                partial.unlink(missing_ok=True)
            repo.update(
                job.id,
                status=Status.error,
                error=INTERRUPTED,
                retries=0,
                thumbnail_path=None,
                file_path=None,
            )
            fixed += 1
        elif job.status == Status.done and (job.file_path is None or not job.file_path.exists()):
            repo.update(
                job.id, status=Status.error, error=MISSING_FILE, file_path=None, thumbnail_path=None
            )
            fixed += 1
        elif job.thumbnail_path is not None and not job.thumbnail_path.exists():
            repo.update(job.id, thumbnail_path=None)
            fixed += 1
        # videos descargados antes de guardar la duración (migración v3)
        if (
            job.status == Status.done
            and job.duration_seconds is None
            and job.file_path
            and (seconds := probe_duration(job.file_path)) is not None
        ):
            repo.update(job.id, duration_seconds=seconds)
    if fixed:
        logger.info("recuperación: %d job(s) corregidos", fixed)
    return fixed
