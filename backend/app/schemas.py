"""Contrato público de la API (lo que ve el cliente)."""

from datetime import datetime
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, HttpUrl

from app.domain import Status


class JobCreate(BaseModel):
    url: HttpUrl
    quality: Annotated[int | None, Field(ge=144, le=4320)] = None
    force: bool = False  # descargar de nuevo aunque ya exista


class JobUpdate(BaseModel):
    starred: bool


class Stats(BaseModel):
    videos: int  # archivos distintos en disco
    bytes: int
    disk_free_bytes: int


class JobRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    url: str
    status: Status
    percent: float
    title: str | None
    error: str | None
    retries: int
    created_at: datetime
    size_bytes: int | None
    duration_seconds: float | None
    starred: bool
    view_count: int
    last_viewed_at: datetime | None
    thumbnail_version: int
