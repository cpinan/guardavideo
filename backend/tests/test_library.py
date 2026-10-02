"""Favoritos y vistas: viven en la BD, así que valen desde cualquier dispositivo."""

import sqlite3
import threading
from collections.abc import Callable
from pathlib import Path

from fastapi.testclient import TestClient

from app.repositories import SCHEMA_VERSION, SqliteJobRepository
from tests.conftest import FakeDownloader, wait_for

ClientFactory = Callable[..., TestClient]
URL = "https://example.com/video.mp4"


def done_job(client: TestClient, url: str = URL) -> str:
    job_id = client.post("/api/v1/jobs", json={"url": url}).json()["id"]
    wait_for(client, job_id, {"done"})
    return str(job_id)


def test_new_jobs_start_unstarred_and_unviewed(make_client: ClientFactory) -> None:
    client = make_client()
    body = client.get(f"/api/v1/jobs/{done_job(client)}").json()
    assert body["starred"] is False and body["view_count"] == 0 and body["last_viewed_at"] is None


def test_star_and_unstar(make_client: ClientFactory) -> None:
    client = make_client()
    job_id = done_job(client)
    r = client.patch(f"/api/v1/jobs/{job_id}", json={"starred": True})
    assert r.status_code == 200 and r.json()["starred"] is True
    assert client.get(f"/api/v1/jobs/{job_id}").json()["starred"] is True
    assert (
        client.patch(f"/api/v1/jobs/{job_id}", json={"starred": False}).json()["starred"] is False
    )


def test_star_validates_body_and_state(make_client: ClientFactory) -> None:
    hold = threading.Event()
    fake = FakeDownloader(hold=hold)
    client = make_client(fake)
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert fake.started.wait(5)
    try:
        assert client.patch(f"/api/v1/jobs/{job_id}", json={"starred": True}).status_code == 409
        assert (
            client.patch(f"/api/v1/jobs/{job_id}", json={"starred": "tal vez"}).status_code == 422
        )
        assert client.patch("/api/v1/jobs/nope", json={"starred": True}).status_code == 404
    finally:
        hold.set()


def test_record_view_counts_and_stamps(make_client: ClientFactory) -> None:
    client = make_client()
    job_id = done_job(client)
    first = client.post(f"/api/v1/jobs/{job_id}/view").json()
    second = client.post(f"/api/v1/jobs/{job_id}/view").json()
    assert (first["view_count"], second["view_count"]) == (1, 2)
    assert second["last_viewed_at"] is not None


def test_record_view_requires_a_finished_job(make_client: ClientFactory) -> None:
    hold = threading.Event()
    fake = FakeDownloader(hold=hold)
    client = make_client(fake)
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert fake.started.wait(5)
    try:
        assert client.post(f"/api/v1/jobs/{job_id}/view").status_code == 409
        assert client.post("/api/v1/jobs/nope/view").status_code == 404
    finally:
        hold.set()


def test_concurrent_views_are_not_lost(make_client: ClientFactory) -> None:
    client = make_client()
    job_id = done_job(client)
    service = client.app.state.job_service  # type: ignore[attr-defined]
    threads = [threading.Thread(target=service.record_view, args=(job_id,)) for _ in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert service.get(job_id).view_count == 20


def test_stars_and_views_survive_a_restart(make_client: ClientFactory, tmp_path: Path) -> None:
    first = make_client(db_path=tmp_path / "lib.db")
    job_id = done_job(first)
    first.patch(f"/api/v1/jobs/{job_id}", json={"starred": True})
    first.post(f"/api/v1/jobs/{job_id}/view")
    first.close()

    second = make_client(db_path=tmp_path / "lib.db")
    body = second.get(f"/api/v1/jobs/{job_id}").json()
    assert body["starred"] is True and body["view_count"] == 1 and body["last_viewed_at"]


def test_migrates_v3_database_to_v4(tmp_path: Path) -> None:
    db = tmp_path / "v3.db"
    conn = sqlite3.connect(db)
    conn.execute(
        "CREATE TABLE jobs (id TEXT PRIMARY KEY, url TEXT NOT NULL, quality INTEGER,"
        " status TEXT NOT NULL, percent REAL NOT NULL DEFAULT 0, title TEXT, error TEXT,"
        " retries INTEGER NOT NULL DEFAULT 0, file_path TEXT, size_bytes INTEGER,"
        " thumbnail_path TEXT, thumbnail_rev INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL,"
        " url_hash TEXT, content_hash TEXT, duration_seconds REAL)"
    )
    conn.execute(
        "INSERT INTO jobs (id, url, status, created_at, duration_seconds) VALUES"
        " ('v3', ?, 'done', '2026-01-01T00:00:00+00:00', 12.5)",
        (URL,),
    )
    conn.execute("PRAGMA user_version=3")
    conn.commit()
    conn.close()

    repo = SqliteJobRepository(db)
    job = repo.get("v3")
    assert job.duration_seconds == 12.5 and job.starred is False and job.view_count == 0
    repo.update("v3", starred=True, view_count=3)
    assert repo.get("v3").starred is True and repo.get("v3").view_count == 3
    repo.close()
    assert sqlite3.connect(db).execute("PRAGMA user_version").fetchone()[0] == SCHEMA_VERSION == 4
