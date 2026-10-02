"""Lo específico de SQLite: persistencia entre reinicios, migraciones y recuperación."""

import sqlite3
import threading
from collections.abc import Callable
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.domain import Job, Status
from app.hashing import url_key
from app.repositories import SCHEMA_VERSION, SqliteJobRepository
from app.services.recovery import INTERRUPTED, MISSING_FILE, recover_jobs
from tests.conftest import FakeDownloader, wait_for

ClientFactory = Callable[..., TestClient]
URL = "https://example.com/video.mp4"


def test_data_survives_reopening_the_database(tmp_path: Path) -> None:
    db = tmp_path / "sub" / "h.db"  # también crea la carpeta
    first = SqliteJobRepository(db)
    first.add(Job(id="a", url=URL, title="Guardado"))
    first.update("a", percent=42)
    first.close()

    second = SqliteJobRepository(db)
    assert second.get("a").title == "Guardado" and second.get("a").percent == 42
    second.close()


def test_migrates_v1_database_keeping_data(tmp_path: Path) -> None:
    db = tmp_path / "old.db"
    conn = sqlite3.connect(db)
    conn.execute(
        "CREATE TABLE jobs (id TEXT PRIMARY KEY, url TEXT NOT NULL, quality INTEGER,"
        " status TEXT NOT NULL, percent REAL NOT NULL DEFAULT 0, title TEXT, error TEXT,"
        " retries INTEGER NOT NULL DEFAULT 0, file_path TEXT, size_bytes INTEGER,"
        " thumbnail_path TEXT, thumbnail_rev INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)"
    )
    conn.execute(
        "INSERT INTO jobs (id, url, status, created_at) VALUES ('old', ?, 'done', '2026-01-01T00:00:00+00:00')",
        (URL,),
    )
    conn.execute("PRAGMA user_version=1")
    conn.commit()
    conn.close()

    repo = SqliteJobRepository(db)
    job = repo.get("old")
    assert job.status == Status.done and job.url_hash is None  # columna nueva, vacía
    repo.update("old", url_hash="h")
    assert repo.get("old").url_hash == "h"
    repo.close()
    assert sqlite3.connect(db).execute("PRAGMA user_version").fetchone()[0] == SCHEMA_VERSION


def test_refuses_a_database_from_the_future(tmp_path: Path) -> None:
    db = tmp_path / "new.db"
    conn = sqlite3.connect(db)
    conn.execute(f"PRAGMA user_version={SCHEMA_VERSION + 1}")
    conn.close()
    with pytest.raises(RuntimeError, match="más nueva"):
        SqliteJobRepository(db)


def test_concurrent_writers_do_not_corrupt_or_lose_rows(tmp_path: Path) -> None:
    repo = SqliteJobRepository(tmp_path / "c.db")
    for i in range(10):
        repo.add(Job(id=str(i), url=URL))

    def bump(job_id: str) -> None:
        for p in range(40):
            repo.update(job_id, percent=p)

    threads = [threading.Thread(target=bump, args=(str(i),)) for i in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert [j.percent for j in repo.list()] == [39] * 10
    repo.close()


# ---------- recuperación al arrancar ----------
def test_recover_marks_interrupted_missing_and_backfills(tmp_path: Path) -> None:
    repo = SqliteJobRepository(tmp_path / "r.db")
    real = tmp_path / "ok.mp4"
    real.write_bytes(b"x")
    repo.add(Job(id="run", url=URL, status=Status.running, percent=40))
    repo.add(Job(id="que", url=URL, status=Status.queued))
    repo.add(Job(id="ok", url=URL, status=Status.done, file_path=real, url_hash="h"))
    repo.add(Job(id="gone", url=URL, status=Status.done, file_path=tmp_path / "borrado.mp4"))
    (tmp_path / "run.part").write_bytes(b"partial")

    assert recover_jobs(repo, tmp_path) == 3  # run, que, gone

    assert repo.get("run").error == INTERRUPTED and repo.get("que").status == Status.error
    assert repo.get("gone").error == MISSING_FILE
    assert repo.get("ok").status == Status.done and repo.get("ok").file_path == real
    assert repo.get("run").url_hash == url_key(URL, None)
    assert not (tmp_path / "run.part").exists()
    repo.close()


def test_recover_forgets_missing_thumbnail(tmp_path: Path) -> None:
    repo = SqliteJobRepository(tmp_path / "t.db")
    repo.add(
        Job(id="a", url=URL, status=Status.error, thumbnail_path=tmp_path / "x.jpg", url_hash="h")
    )
    recover_jobs(repo, tmp_path)
    assert repo.get("a").thumbnail_path is None
    repo.close()


# ---------- a través de la API: reinicio del servidor ----------
def restart(make_client: ClientFactory, tmp_path: Path, **kw: object) -> TestClient:
    return make_client(db_path=tmp_path / "shared.db", **kw)


def test_finished_downloads_survive_a_restart(make_client: ClientFactory, tmp_path: Path) -> None:
    first = restart(make_client, tmp_path)
    job_id = first.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    wait_for(first, job_id, {"done"})
    first.close()  # apaga el servidor

    second = restart(make_client, tmp_path)
    body = second.get(f"/api/v1/jobs/{job_id}").json()
    assert body["status"] == "done" and body["size_bytes"] == len(b"fake-video")
    assert second.get(f"/api/v1/jobs/{job_id}/file").content == b"fake-video"  # el archivo sigue


def test_running_job_becomes_retryable_error_after_restart(
    make_client: ClientFactory, tmp_path: Path
) -> None:
    hold = threading.Event()
    fake = FakeDownloader(hold=hold)
    first = restart(make_client, tmp_path)
    first.app.state.job_service._downloaders = [fake]  # type: ignore[attr-defined]
    job_id = first.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert fake.started.wait(5)
    hold.set()  # deja terminar al worker para poder apagar
    first.close()

    # simula el crash: el registro quedó "running" en disco
    repo = SqliteJobRepository(tmp_path / "shared.db")
    repo.update(job_id, status=Status.running, percent=50, file_path=None)
    repo.close()

    second = restart(make_client, tmp_path)
    assert second.get(f"/api/v1/jobs/{job_id}").json()["error"] == INTERRUPTED
    assert second.post(f"/api/v1/jobs/{job_id}/retry").status_code == 202  # job cargado de la BD
    wait_for(second, job_id, {"done"})


# ---------- eliminar del historial ----------
def test_delete_removes_record_and_files(make_client: ClientFactory, tmp_path: Path) -> None:
    client = make_client()
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    wait_for(client, job_id, {"done"})
    path = client.app.state.job_service.get(job_id).file_path  # type: ignore[attr-defined]
    assert path.exists()

    assert client.delete(f"/api/v1/jobs/{job_id}/record").status_code == 204
    assert not path.exists()
    assert client.get(f"/api/v1/jobs/{job_id}").status_code == 404
    assert client.delete(f"/api/v1/jobs/{job_id}/record").status_code == 404


def test_delete_active_job_is_409(make_client: ClientFactory) -> None:
    hold = threading.Event()
    fake = FakeDownloader(hold=hold)
    client = make_client(fake)
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert fake.started.wait(5)
    try:
        assert client.delete(f"/api/v1/jobs/{job_id}/record").status_code == 409
    finally:
        hold.set()


def test_delete_never_touches_files_outside_download_dir(
    make_client: ClientFactory, tmp_path: Path
) -> None:
    client = make_client()
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    wait_for(client, job_id, {"done"})
    outside = tmp_path.parent / f"{tmp_path.name}-outside.txt"
    outside.write_text("no me borres")
    client.app.state.job_service._repo.update(job_id, file_path=outside)  # type: ignore[attr-defined]

    assert client.delete(f"/api/v1/jobs/{job_id}/record").status_code == 204
    assert outside.read_text() == "no me borres"
