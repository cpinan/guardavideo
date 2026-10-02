"""Reproducción en el navegador: stream con Range, duración real y archivos de prueba."""

import shutil
import sqlite3
from collections.abc import Callable
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.downloaders.debug import DebugDownloader
from app.media import make_test_video, probe_duration
from app.repositories import SCHEMA_VERSION, SqliteJobRepository
from tests.conftest import wait_for

ClientFactory = Callable[..., TestClient]
needs_ffmpeg = pytest.mark.skipif(not shutil.which("ffmpeg"), reason="requiere ffmpeg")
URL = "https://example.com/video.mp4"


@needs_ffmpeg
def test_probe_duration_reads_a_real_video(tmp_path: Path) -> None:
    video = tmp_path / "v.mp4"
    assert make_test_video(video, seconds=3)
    assert probe_duration(video) == pytest.approx(3.0, abs=0.3)


def test_probe_duration_is_none_for_garbage_or_missing(tmp_path: Path) -> None:
    bad = tmp_path / "bad.mp4"
    bad.write_bytes(b"no soy un video")
    assert probe_duration(bad) is None
    assert probe_duration(tmp_path / "no-existe.mp4") is None


def test_stream_supports_range_requests(make_client: ClientFactory) -> None:
    client = make_client()  # FakeDownloader escribe b"fake-video" (10 bytes)
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    wait_for(client, job_id, {"done"})

    full = client.get(f"/api/v1/jobs/{job_id}/stream")
    assert full.status_code == 200
    assert full.headers["content-type"] == "video/mp4"
    assert full.headers["accept-ranges"] == "bytes"
    assert not full.headers.get("content-disposition", "").startswith("attachment")  # en línea

    part = client.get(f"/api/v1/jobs/{job_id}/stream", headers={"Range": "bytes=5-"})
    assert part.status_code == 206 and part.content == b"video"
    assert part.headers["content-range"] == "bytes 5-9/10"


def test_download_endpoint_still_forces_attachment(make_client: ClientFactory) -> None:
    client = make_client()
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    wait_for(client, job_id, {"done"})
    assert (
        client.get(f"/api/v1/jobs/{job_id}/file")
        .headers["content-disposition"]
        .startswith("attachment")
    )


def test_stream_unavailable_until_done_and_unknown_job(make_client: ClientFactory) -> None:
    client = make_client()
    assert client.get("/api/v1/jobs/nope/stream").status_code == 404


@needs_ffmpeg
def test_finished_job_exposes_duration_and_plays(make_client: ClientFactory) -> None:
    client = make_client(DebugDownloader(tick=0.005), debug=True, allow_private_urls=False)
    job_id = client.post("/api/v1/jobs", json={"url": "http://debug.test/ok"}).json()["id"]
    body = wait_for(client, job_id, {"done"})

    assert body["duration_seconds"] == pytest.approx(12.0, abs=0.5)
    r = client.get(f"/api/v1/jobs/{job_id}/stream", headers={"Range": "bytes=0-99"})
    assert r.status_code == 206 and r.content[4:8] == b"ftyp"  # MP4 de verdad


def test_duration_is_none_when_it_cannot_be_read(make_client: ClientFactory) -> None:
    client = make_client()  # bytes que no son un video
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert wait_for(client, job_id, {"done"})["duration_seconds"] is None


def test_migrates_v2_database_to_v3(tmp_path: Path) -> None:
    db = tmp_path / "v2.db"
    conn = sqlite3.connect(db)
    conn.execute(
        "CREATE TABLE jobs (id TEXT PRIMARY KEY, url TEXT NOT NULL, quality INTEGER,"
        " status TEXT NOT NULL, percent REAL NOT NULL DEFAULT 0, title TEXT, error TEXT,"
        " retries INTEGER NOT NULL DEFAULT 0, file_path TEXT, size_bytes INTEGER,"
        " thumbnail_path TEXT, thumbnail_rev INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL,"
        " url_hash TEXT, content_hash TEXT)"
    )
    conn.execute(
        "INSERT INTO jobs (id, url, status, created_at, url_hash) VALUES"
        " ('v2', ?, 'done', '2026-01-01T00:00:00+00:00', 'h')",
        (URL,),
    )
    conn.execute("PRAGMA user_version=2")
    conn.commit()
    conn.close()

    repo = SqliteJobRepository(db)
    job = repo.get("v2")
    assert job.url_hash == "h" and job.duration_seconds is None  # datos intactos, columna nueva
    repo.update("v2", duration_seconds=61.5)
    assert repo.get("v2").duration_seconds == 61.5
    repo.close()
    assert sqlite3.connect(db).execute("PRAGMA user_version").fetchone()[0] == SCHEMA_VERSION == 4


@needs_ffmpeg
def test_recovery_backfills_missing_durations(tmp_path: Path) -> None:
    from app.domain import Job, Status
    from app.services.recovery import recover_jobs

    video = tmp_path / "old.mp4"
    assert make_test_video(video, seconds=2)
    repo = SqliteJobRepository(tmp_path / "b.db")
    repo.add(Job(id="old", url=URL, status=Status.done, file_path=video, url_hash="h"))
    recover_jobs(repo, tmp_path)
    assert repo.get("old").duration_seconds == pytest.approx(2.0, abs=0.3)
    repo.close()


def test_stats_counts_each_file_once(make_client: ClientFactory) -> None:
    client = make_client()
    assert client.get("/api/v1/jobs/stats").json()["videos"] == 0
    for n in (1, 2):  # mismo contenido por dos URLs -> un solo archivo
        job_id = client.post("/api/v1/jobs", json={"url": f"{URL}?m={n}"}).json()["id"]
        wait_for(client, job_id, {"done"})
    stats = client.get("/api/v1/jobs/stats").json()
    assert stats["videos"] == 1 and stats["bytes"] == len(b"fake-video")
    assert stats["disk_free_bytes"] > 0


def test_title_is_known_before_the_download_finishes(make_client: ClientFactory) -> None:
    import threading

    from tests.conftest import FakeDownloader

    class Titled(FakeDownloader):
        def download(self, url, dest_dir, job_id, quality, ctx):  # type: ignore[no-untyped-def]
            ctx.title("Título temprano")
            return super().download(url, dest_dir, job_id, quality, ctx)

    hold = threading.Event()
    fake = Titled(hold=hold)
    client = make_client(fake)
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert fake.started.wait(5)
    try:
        assert client.get(f"/api/v1/jobs/{job_id}").json()["title"] == "Título temprano"
    finally:
        hold.set()


def test_download_filename_keeps_readable_punctuation(make_client: ClientFactory) -> None:
    from urllib.parse import unquote

    client = make_client(DebugDownloader(tick=0.005), debug=True, allow_private_urls=False)
    job_id = client.post("/api/v1/jobs", json={"url": "http://debug.test/ok"}).json()["id"]
    wait_for(client, job_id, {"done"})
    disposition = unquote(client.get(f"/api/v1/jobs/{job_id}/file").headers["content-disposition"])
    assert "Debug ok.mp4" in disposition and "  " not in disposition
