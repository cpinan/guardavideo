from collections.abc import Callable

import pytest
from fastapi.testclient import TestClient

from app.downloaders.debug import SCENARIOS, DebugDownloader
from app.downloaders.hls import HlsDownloader
from app.downloaders.ytdlp import YtDlpDownloader
from tests.conftest import wait_for

ClientFactory = Callable[..., TestClient]


def debug_client(make_client: ClientFactory) -> TestClient:
    return make_client(DebugDownloader(tick=0.005), debug=True, allow_private_urls=False)


def create(client: TestClient, scenario: str) -> str:
    r = client.post("/api/v1/jobs", json={"url": f"http://debug.test/{scenario}"})
    assert r.status_code == 201, r.text
    return str(r.json()["id"])


def test_debug_urls_are_only_routed_in_debug_mode() -> None:
    d = DebugDownloader()
    assert d.supports("http://debug.test/ok")
    assert not d.supports("http://example.com/debug.test")
    assert not HlsDownloader().supports("http://debug.test/ok")
    assert YtDlpDownloader().supports("http://debug.test/ok")  # el respaldo lo aceptaría...


def test_without_debug_mode_the_host_is_blocked_by_ssrf_guard(make_client: ClientFactory) -> None:
    client = make_client(DebugDownloader(tick=0.005), allow_private_urls=False)
    r = client.post("/api/v1/jobs", json={"url": "http://debug.test/ok"})
    assert r.status_code == 400  # ...por eso en producción la protección SSRF lo rechaza


def test_ok_scenario_finishes_with_thumbnail(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    job_id = create(client, "ok")
    body = wait_for(client, job_id, {"done"})
    assert body["title"] == "Debug · ok"
    if body["thumbnail_version"]:  # requiere ffmpeg
        r = client.get(f"/api/v1/jobs/{job_id}/thumbnail")
        assert r.status_code == 200 and r.headers["content-type"] == "image/jpeg"


@pytest.mark.parametrize("scenario", ["fail", "late-fail", "network"])
def test_failing_scenarios_end_in_error(make_client: ClientFactory, scenario: str) -> None:
    client = debug_client(make_client)
    body = wait_for(client, create(client, scenario), {"error"})
    assert body["error"] and body["retries"] == 0
    assert body["thumbnail_version"] == 0  # no queda una miniatura de una descarga fallida


def test_flaky_scenario_recovers(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    body = wait_for(client, create(client, "flaky"), {"done"}, timeout=10)
    assert body["retries"] == 0 and body["percent"] == 100


def test_stall_scenario_hangs_until_cancelled(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    job_id = create(client, "stall")
    wait_for(client, job_id, {"running"})
    assert client.delete(f"/api/v1/jobs/{job_id}").status_code == 202
    wait_for(client, job_id, {"cancelled"})


def test_unknown_scenario_is_a_clear_error(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    body = wait_for(client, create(client, "nope"), {"error"})
    assert "Escenario desconocido" in body["error"]


def test_health_reports_debug_flag(make_client: ClientFactory) -> None:
    assert debug_client(make_client).get("/health").json()["debug"] is True
    assert make_client().get("/health").json()["debug"] is False


def test_every_scenario_is_documented() -> None:
    assert set(SCENARIOS) == {"ok", "slow", "fail", "late-fail", "flaky", "network", "stall"}


def test_retry_endpoint_requeues_failed_job(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    job_id = create(client, "fail")
    wait_for(client, job_id, {"error"})

    r = client.post(f"/api/v1/jobs/{job_id}/retry")
    assert r.status_code == 202 and r.json()["error"] is None
    assert wait_for(client, job_id, {"error"})["id"] == job_id  # vuelve a fallar: misma id


def test_retry_rejects_active_and_done_jobs(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    done = create(client, "ok")
    wait_for(client, done, {"done"})
    assert client.post(f"/api/v1/jobs/{done}/retry").status_code == 409

    stalled = create(client, "stall")
    wait_for(client, stalled, {"running"})
    assert client.post(f"/api/v1/jobs/{stalled}/retry").status_code == 409
    client.delete(f"/api/v1/jobs/{stalled}")
    assert client.post("/api/v1/jobs/nope/retry").status_code == 404


def test_retry_after_cancel_runs_exactly_once(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    job_id = create(client, "stall")
    wait_for(client, job_id, {"running"})
    client.delete(f"/api/v1/jobs/{job_id}")
    wait_for(client, job_id, {"cancelled"})

    assert client.post(f"/api/v1/jobs/{job_id}/retry").status_code == 202
    wait_for(client, job_id, {"running"})
    client.delete(f"/api/v1/jobs/{job_id}")
    wait_for(client, job_id, {"cancelled"})


def test_thumbnail_404_when_missing(make_client: ClientFactory) -> None:
    client = debug_client(make_client)
    job_id = create(client, "fail")
    wait_for(client, job_id, {"error"})
    assert client.get(f"/api/v1/jobs/{job_id}/thumbnail").status_code == 404
