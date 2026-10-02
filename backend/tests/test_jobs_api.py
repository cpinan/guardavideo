import json
import threading
from collections.abc import Callable
from urllib.parse import unquote

from fastapi.testclient import TestClient

from tests.conftest import FakeDownloader, wait_for

ClientFactory = Callable[..., TestClient]
URL = "https://example.com/video.mp4"


def test_create_job_returns_201_with_location(make_client: ClientFactory) -> None:
    client = make_client()
    r = client.post("/api/v1/jobs", json={"url": URL})
    assert r.status_code == 201
    body = r.json()
    assert r.headers["location"] == f"/api/v1/jobs/{body['id']}"
    assert set(body) == {
        "id",
        "url",
        "status",
        "percent",
        "title",
        "error",
        "retries",
        "thumbnail_version",
        "duration_seconds",
        "created_at",
        "size_bytes",
    }  # no filtra file_path


def test_job_completes_and_file_is_downloadable(make_client: ClientFactory) -> None:
    client = make_client()
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    body = wait_for(client, job_id, {"done"})
    assert body["percent"] == 100 and body["title"] == "Mi video"

    r = client.get(f"/api/v1/jobs/{job_id}/file")
    assert r.status_code == 200
    assert r.content == b"fake-video"
    assert "Mi video.mp4" in unquote(r.headers["content-disposition"])  # RFC 5987


def test_invalid_url_is_422(make_client: ClientFactory) -> None:
    assert make_client().post("/api/v1/jobs", json={"url": "no-es-url"}).status_code == 422


def test_invalid_quality_is_422(make_client: ClientFactory) -> None:
    r = make_client().post("/api/v1/jobs", json={"url": URL, "quality": 5})
    assert r.status_code == 422


def test_unknown_job_is_404_including_events(make_client: ClientFactory) -> None:
    client = make_client()
    assert client.get("/api/v1/jobs/nope").status_code == 404
    assert client.get("/api/v1/jobs/nope/events").status_code == 404  # antes de abrir el stream


def test_file_before_done_is_409(make_client: ClientFactory) -> None:
    hold = threading.Event()
    client = make_client(FakeDownloader(hold=hold))
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    try:
        assert client.get(f"/api/v1/jobs/{job_id}/file").status_code == 409
    finally:
        hold.set()


def test_downloader_failure_marks_job_error(make_client: ClientFactory) -> None:
    client = make_client(FakeDownloader(fail="sitio no soportado"))
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    body = wait_for(client, job_id, {"error"})
    assert body["error"] == "sitio no soportado"


def test_cancel_running_job(make_client: ClientFactory) -> None:
    hold = threading.Event()  # nunca se libera: el job queda "colgado" hasta cancelarlo
    fake = FakeDownloader(hold=hold)
    client = make_client(fake)
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    assert fake.started.wait(timeout=5)

    assert client.delete(f"/api/v1/jobs/{job_id}").status_code == 202
    wait_for(client, job_id, {"cancelled"})
    assert client.delete(f"/api/v1/jobs/{job_id}").status_code == 409  # ya terminó


def test_cancel_queued_job_never_runs(make_client: ClientFactory) -> None:
    hold = threading.Event()
    first = FakeDownloader(hold=hold)
    client = make_client(first, max_concurrent_jobs=1)  # un solo worker -> el 2º queda en cola
    client.post("/api/v1/jobs", json={"url": URL})
    assert first.started.wait(timeout=5)
    queued_id = client.post("/api/v1/jobs", json={"url": URL, "force": True}).json()["id"]

    assert client.delete(f"/api/v1/jobs/{queued_id}").status_code == 202
    assert client.get(f"/api/v1/jobs/{queued_id}").json()["status"] == "cancelled"
    hold.set()


def test_sse_streams_progress_until_terminal(make_client: ClientFactory) -> None:
    client = make_client()
    job_id = client.post("/api/v1/jobs", json={"url": URL}).json()["id"]
    with client.stream("GET", f"/api/v1/jobs/{job_id}/events") as r:
        assert r.headers["content-type"].startswith("text/event-stream")
        events = [
            json.loads(line.removeprefix("data: "))
            for line in r.iter_lines()
            if line.startswith("data: ")
        ]
    assert events[-1]["status"] == "done"
    percents = [e["percent"] for e in events]
    assert percents == sorted(percents)  # el progreso nunca retrocede


def test_ssrf_is_rejected_at_submit(make_client: ClientFactory) -> None:
    client = make_client()
    client.app.state.job_service._settings.allow_private_urls = False  # type: ignore[attr-defined]
    r = client.post("/api/v1/jobs", json={"url": "http://127.0.0.1/secret.m3u8"})
    assert r.status_code == 400


def test_health(make_client: ClientFactory) -> None:
    body = make_client().get("/health").json()
    assert body["status"] == "ok" and "ffmpeg" in body
