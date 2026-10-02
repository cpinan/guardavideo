"""Modelo de dominio, independiente de HTTP."""

from datetime import UTC, datetime
from enum import StrEnum
from pathlib import Path

from pydantic import BaseModel, Field


class Status(StrEnum):
    queued = "queued"
    running = "running"
    done = "done"
    error = "error"
    cancelled = "cancelled"


TERMINAL = {Status.done, Status.error, Status.cancelled}


class Job(BaseModel):
    id: str
    url: str
    quality: int | None = None
    status: Status = Status.queued
    percent: float = 0
    title: str | None = None
    error: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    size_bytes: int | None = None  # tamaño del archivo final
    url_hash: str | None = None  # sha256(url canónica + calidad): detecta "ya lo descargué"
    content_hash: str | None = None  # sha256 del archivo: detecta contenido idéntico
    retries: int = 0  # reintentos de red en curso (la UI muestra "Reconectando…")
    file_path: Path | None = None  # interno: nunca se expone al cliente
    thumbnail_path: Path | None = None  # interno
    thumbnail_rev: int = 0  # sube con cada miniatura nueva (invalida la caché del navegador)

    @property
    def thumbnail_version(self) -> int:
        """0 = sin miniatura; si existe, cambia cada vez que se reemplaza."""
        return self.thumbnail_rev if self.thumbnail_path else 0

    @property
    def is_terminal(self) -> bool:
        return self.status in TERMINAL
