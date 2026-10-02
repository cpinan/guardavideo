"""Puerto + adaptadores. Hoy: memoria (tests) y SQLite (historial persistente)."""

import sqlite3
import threading
from datetime import datetime
from pathlib import Path
from typing import Any, Protocol

from app.domain import Job, Status
from app.exceptions import JobNotFoundError

_FIELDS = frozenset(Job.model_fields)


def _check_fields(changes: dict[str, Any]) -> None:
    unknown = set(changes) - _FIELDS
    if unknown:
        raise ValueError(f"Campos desconocidos: {sorted(unknown)}")


class JobRepository(Protocol):
    def add(self, job: Job) -> None: ...
    def get(self, job_id: str) -> Job: ...
    def list(self) -> list[Job]: ...
    def update(self, job_id: str, **changes: Any) -> Job: ...
    def delete(self, job_id: str) -> None: ...


class InMemoryJobRepository:
    """Thread-safe: protege con lock y entrega copias, nunca el objeto compartido."""

    def __init__(self) -> None:
        self._jobs: dict[str, Job] = {}
        self._lock = threading.Lock()

    def add(self, job: Job) -> None:
        with self._lock:
            self._jobs[job.id] = job

    def get(self, job_id: str) -> Job:
        with self._lock:
            try:
                return self._jobs[job_id].model_copy()
            except KeyError:
                raise JobNotFoundError(job_id) from None

    def list(self) -> list[Job]:
        with self._lock:
            return [j.model_copy() for j in self._jobs.values()]

    def update(self, job_id: str, **changes: Any) -> Job:
        _check_fields(changes)
        with self._lock:
            try:
                updated = self._jobs[job_id].model_copy(update=changes)
            except KeyError:
                raise JobNotFoundError(job_id) from None
            self._jobs[job_id] = updated
            return updated.model_copy()

    def delete(self, job_id: str) -> None:
        with self._lock:
            if self._jobs.pop(job_id, None) is None:
                raise JobNotFoundError(job_id)


# (columna, tipo SQL). `id` es la clave; el orden de inserción (rowid) define el de la lista.
_COLUMNS: list[tuple[str, str]] = [
    ("id", "TEXT PRIMARY KEY"),
    ("url", "TEXT NOT NULL"),
    ("quality", "INTEGER"),
    ("status", "TEXT NOT NULL"),
    ("percent", "REAL NOT NULL DEFAULT 0"),
    ("title", "TEXT"),
    ("error", "TEXT"),
    ("retries", "INTEGER NOT NULL DEFAULT 0"),
    ("file_path", "TEXT"),
    ("size_bytes", "INTEGER"),
    ("thumbnail_path", "TEXT"),
    ("thumbnail_rev", "INTEGER NOT NULL DEFAULT 0"),
    ("created_at", "TEXT NOT NULL"),
]
# v2: hashes para evitar descargas repetidas (columnas añadidas con ALTER TABLE)
_COLUMNS_V2: list[tuple[str, str]] = [("url_hash", "TEXT"), ("content_hash", "TEXT")]
# v3: duración del video
_COLUMNS_V3: list[tuple[str, str]] = [("duration_seconds", "REAL")]
# v4: favoritos y vistas
_COLUMNS_V4: list[tuple[str, str]] = [
    ("starred", "INTEGER NOT NULL DEFAULT 0"),
    ("view_count", "INTEGER NOT NULL DEFAULT 0"),
    ("last_viewed_at", "TEXT"),
]
_NAMES = [name for name, _ in [*_COLUMNS, *_COLUMNS_V2, *_COLUMNS_V3, *_COLUMNS_V4]]
SCHEMA_VERSION = 4


def _to_row(job: Job) -> tuple[Any, ...]:
    return (
        job.id,
        job.url,
        job.quality,
        job.status.value,
        job.percent,
        job.title,
        job.error,
        job.retries,
        str(job.file_path) if job.file_path else None,
        job.size_bytes,
        str(job.thumbnail_path) if job.thumbnail_path else None,
        job.thumbnail_rev,
        job.created_at.isoformat(),
        job.url_hash,
        job.content_hash,
        job.duration_seconds,
        int(job.starred),
        job.view_count,
        job.last_viewed_at.isoformat() if job.last_viewed_at else None,
    )


def _from_row(row: sqlite3.Row) -> Job:
    return Job(
        id=row["id"],
        url=row["url"],
        quality=row["quality"],
        status=Status(row["status"]),
        percent=row["percent"],
        title=row["title"],
        error=row["error"],
        retries=row["retries"],
        file_path=Path(row["file_path"]) if row["file_path"] else None,
        size_bytes=row["size_bytes"],
        thumbnail_path=Path(row["thumbnail_path"]) if row["thumbnail_path"] else None,
        thumbnail_rev=row["thumbnail_rev"],
        created_at=datetime.fromisoformat(row["created_at"]),
        url_hash=row["url_hash"],
        content_hash=row["content_hash"],
        duration_seconds=row["duration_seconds"],
        starred=bool(row["starred"]),
        view_count=row["view_count"],
        last_viewed_at=datetime.fromisoformat(row["last_viewed_at"])
        if row["last_viewed_at"]
        else None,
    )


class SqliteJobRepository:
    """Historial persistente en SQLite (stdlib, sin dependencias).

    Una sola conexión protegida por lock (los workers escriben desde varios hilos); WAL para que
    una lectura externa (p. ej. `sqlite3 hlsweb.db`) no bloquee las escrituras. Cada método
    entrega copias, igual que el adaptador en memoria.
    """

    def __init__(self, path: Path | str) -> None:
        if str(path) != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._migrate()
        updates = ", ".join(f"{n}=excluded.{n}" for n in _NAMES if n != "id")
        marks = ", ".join("?" for _ in _NAMES)
        self._upsert = (
            f"INSERT INTO jobs ({', '.join(_NAMES)}) VALUES ({marks}) "  # noqa: S608 - constantes
            f"ON CONFLICT(id) DO UPDATE SET {updates}"
        )

    def _migrate(self) -> None:
        version = self._conn.execute("PRAGMA user_version").fetchone()[0]
        if version > SCHEMA_VERSION:
            raise RuntimeError(f"La base de datos es más nueva (v{version}) que esta app")
        if version < 1:
            cols = ", ".join(f"{n} {t}" for n, t in _COLUMNS)
            self._conn.execute(f"CREATE TABLE IF NOT EXISTS jobs ({cols})")  # noqa: S608
            self._conn.execute("CREATE INDEX IF NOT EXISTS jobs_status ON jobs(status)")
            self._conn.execute("PRAGMA user_version=1")
        if version < 2:  # noqa: PLR2004
            for name, sql_type in _COLUMNS_V2:
                self._conn.execute(f"ALTER TABLE jobs ADD COLUMN {name} {sql_type}")  # noqa: S608
            self._conn.execute("CREATE INDEX IF NOT EXISTS jobs_url_hash ON jobs(url_hash)")
            self._conn.execute("PRAGMA user_version=2")
        if version < 3:  # noqa: PLR2004
            for name, sql_type in _COLUMNS_V3:
                self._conn.execute(f"ALTER TABLE jobs ADD COLUMN {name} {sql_type}")  # noqa: S608
            self._conn.execute("PRAGMA user_version=3")
        if version < 4:  # noqa: PLR2004
            for name, sql_type in _COLUMNS_V4:
                self._conn.execute(f"ALTER TABLE jobs ADD COLUMN {name} {sql_type}")  # noqa: S608
            self._conn.execute(f"PRAGMA user_version={SCHEMA_VERSION}")

    def add(self, job: Job) -> None:
        with self._lock:
            self._conn.execute(self._upsert, _to_row(job))

    def _fetch(self, job_id: str) -> Job:
        row = self._conn.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
        if row is None:
            raise JobNotFoundError(job_id)
        return _from_row(row)

    def get(self, job_id: str) -> Job:
        with self._lock:
            return self._fetch(job_id)

    def list(self) -> list[Job]:
        with self._lock:
            rows = self._conn.execute("SELECT * FROM jobs ORDER BY rowid").fetchall()
        return [_from_row(r) for r in rows]

    def update(self, job_id: str, **changes: Any) -> Job:
        _check_fields(changes)
        with self._lock:
            updated = self._fetch(job_id).model_copy(update=changes)
            self._conn.execute(self._upsert, _to_row(updated))
            return updated

    def delete(self, job_id: str) -> None:
        with self._lock:
            if self._conn.execute("DELETE FROM jobs WHERE id=?", (job_id,)).rowcount == 0:
                raise JobNotFoundError(job_id)

    def close(self) -> None:
        with self._lock:
            self._conn.close()
