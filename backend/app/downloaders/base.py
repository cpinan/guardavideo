"""Contrato de un descargador. Agregar una fuente = una clase nueva que cumpla el Protocol."""

import threading
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Protocol

from app.exceptions import DownloadCancelledError


@dataclass
class DownloadContext:
    """Lo que el servicio le da al descargador: reportar progreso y detectar cancelación."""

    report_percent: Callable[[float], None]
    cancel: threading.Event = field(default_factory=threading.Event)
    report_retry: Callable[[int], None] = lambda attempt: None
    report_thumbnail: Callable[[Path], None] = lambda path: None
    report_title: Callable[[str], None] = lambda title: None
    _last: float = field(default=-1.0, init=False, repr=False)

    def check_cancelled(self) -> None:
        if self.cancel.is_set():
            raise DownloadCancelledError

    def retrying(self, attempt: int) -> None:
        """Avisa que hubo un fallo de red recuperable (attempt=0 significa que ya se recuperó)."""
        self.report_retry(attempt)

    def title(self, title: str) -> None:
        """Avisa el título apenas se conoce (la UI lo muestra mientras descarga)."""
        self.report_title(title)

    def thumbnail(self, path: Path) -> None:
        """Avisa que hay una miniatura nueva (JPEG) para mostrar mientras dura la descarga."""
        self.report_thumbnail(path)

    def sleep(self, seconds: float) -> None:
        """Espera interrumpible: cancelar el job despierta al hilo de inmediato."""
        self.cancel.wait(seconds)
        self.check_cancelled()

    def report(self, percent: float) -> None:
        p = round(min(max(percent, 0.0), 100.0), 1)
        if p != self._last:  # evita escribir en el repositorio si no cambió
            self._last = p
            self.report_percent(p)


@dataclass(frozen=True)
class DownloadResult:
    path: Path
    title: str | None


class Downloader(Protocol):
    name: str

    def supports(self, url: str) -> bool: ...

    def download(
        self, url: str, dest_dir: Path, job_id: str, quality: int | None, ctx: DownloadContext
    ) -> DownloadResult: ...
