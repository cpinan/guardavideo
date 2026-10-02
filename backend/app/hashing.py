"""Huellas SHA-256: detectan descargas repetidas (por URL) y archivos idénticos (por contenido)."""

import hashlib
import threading
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

from app.exceptions import DownloadCancelledError

CHUNK = 1024 * 1024


def canonical_url(url: str) -> str:
    """Normaliza lo que no cambia el recurso: esquema/host en minúsculas y sin #fragmento.

    La ruta y la query se respetan tal cual (en URLs firmadas importan el orden y los valores).
    """
    parts = urlsplit(url.strip())
    return urlunsplit((parts.scheme.lower(), parts.netloc.lower(), parts.path, parts.query, ""))


def url_key(url: str, quality: int | None) -> str:
    """Misma URL + misma calidad pedida = mismo resultado esperado."""
    return hashlib.sha256(f"{canonical_url(url)}|{quality or 0}".encode()).hexdigest()


def file_sha256(path: Path, cancel: threading.Event | None = None) -> str:
    """SHA-256 en bloques (memoria acotada). Cancelar el job interrumpe el cálculo."""
    digest = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(CHUNK):
            if cancel is not None and cancel.is_set():
                raise DownloadCancelledError
            digest.update(chunk)
    return digest.hexdigest()
