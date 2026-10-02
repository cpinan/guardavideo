import threading
from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.exceptions import DownloadCancelledError
from app.hashing import canonical_url, file_sha256, url_key
from tests.conftest import FakeDownloader, wait_for

ClientFactory = Callable[..., TestClient]
URL = "https://example.com/video.mp4"


# ---------- huellas ----------
def test_canonical_url_ignores_case_of_host_and_fragment_only() -> None:
    assert canonical_url("HTTPS://Example.COM/a?x=1#frag") == "https://example.com/a?x=1"
    assert canonical_url("https://example.com/A") != canonical_url("https://example.com/a")
    assert canonical_url("https://e.com/a?b=1&c=2") != canonical_url("https://e.com/a?c=2&b=1")


def test_url_key_depends_on_quality_and_is_stable() -> None:
    assert url_key(URL, None) == url_key(URL + "#x", None)
    assert url_key(URL, 720) != url_key(URL, 1080)
    assert url_key(URL, None) == url_key(URL, 0)
    assert len(url_key(URL, None)) == 64


def test_file_sha256_matches_known_value_and_streams(tmp_path: Path) -> None:
    f = tmp_path / "f.bin"
    f.write_bytes(b"abc")
    assert file_sha256(f) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    big = tmp_path / "big.bin"
    big.write_bytes(b"x" * (3 * 1024 * 1024 + 7))  # varios bloques
    assert len(file_sha256(big)) == 64


def test_file_sha256_can_be_cancelled(tmp_path: Path) -> None:
    f = tmp_path / "f.bin"
    f.write_bytes(b"abc")
    cancel = threading.Event()
    cancel.set()
    with pytest.raises(DownloadCancelledError):
        file_sha256(f, cancel)


# ---------- por URL ----------
def post(client: TestClient, url: str = URL, **extra: object) -> tuple[int, dict[str, Any]]:
    r = client.post("/api/v1/jobs", json={"url": url, **extra})
    return r.status_code, r.json()


def test_same_url_is_not_downloaded_twice(make_client: ClientFactory) -> None:
    client = make_client()
    code, first = post(client)
    assert code == 201
    wait_for(client, first["id"], {"done"})

    code, second = post(client, URL + "#otro-fragmento")
    assert code == 200 and second["id"] == first["id"]  # 200 = reutilizado
    assert len(client.get("/api/v1/jobs").json()) == 1


def test_same_url_while_in_progress_returns_the_running_job(make_client: ClientFactory) -> None:
    hold = threading.Event()
    fake = FakeDownloader(hold=hold)
    client = make_client(fake)
    _, first = post(client)
    assert fake.started.wait(5)
    try:
        code, second = post(client)
        assert code == 200 and second["id"] == first["id"]
    finally:
        hold.set()


def test_different_quality_is_a_different_download(make_client: ClientFactory) -> None:
    client = make_client()
    _, a = post(client, quality=720)
    wait_for(client, a["id"], {"done"})
    code, b = post(client, quality=1080)
    assert code == 201 and b["id"] != a["id"]


def test_force_downloads_again(make_client: ClientFactory) -> None:
    client = make_client()
    _, a = post(client)
    wait_for(client, a["id"], {"done"})
    code, b = post(client, force=True)
    assert code == 201 and b["id"] != a["id"]


def test_failed_job_is_not_reused(make_client: ClientFactory) -> None:
    client = make_client(FakeDownloader(fail="boom"))
    _, a = post(client)
    wait_for(client, a["id"], {"error"})
    code, b = post(client)
    assert code == 201 and b["id"] != a["id"]


def test_redownloads_when_the_file_was_deleted_from_disk(make_client: ClientFactory) -> None:
    client = make_client()
    _, a = post(client)
    wait_for(client, a["id"], {"done"})
    client.app.state.job_service.get(a["id"]).file_path.unlink()  # type: ignore[attr-defined]
    code, b = post(client)
    assert code == 201 and b["id"] != a["id"]


# ---------- por contenido ----------
def test_identical_content_from_different_urls_is_stored_once(
    make_client: ClientFactory,
) -> None:
    client = make_client()  # FakeDownloader escribe siempre los mismos bytes
    _, a = post(client, URL + "?mirror=1")
    wait_for(client, a["id"], {"done"})
    _, b = post(client, URL + "?mirror=2")
    done_b = wait_for(client, b["id"], {"done"})

    service = client.app.state.job_service  # type: ignore[attr-defined]
    ja, jb = service.get(a["id"]), service.get(b["id"])
    assert ja.content_hash == jb.content_hash and len(ja.content_hash) == 64
    assert ja.file_path == jb.file_path  # los dos apuntan al mismo archivo
    assert len(list(Path(service._settings.download_dir).glob("*.mp4"))) == 1
    assert done_b["size_bytes"] == len(b"fake-video")
    assert client.get(f"/api/v1/jobs/{b['id']}/file").content == b"fake-video"


def test_shared_file_is_deleted_only_with_its_last_job(make_client: ClientFactory) -> None:
    client = make_client()
    _, a = post(client, URL + "?mirror=1")
    wait_for(client, a["id"], {"done"})
    _, b = post(client, URL + "?mirror=2")
    wait_for(client, b["id"], {"done"})
    path = client.app.state.job_service.get(a["id"]).file_path  # type: ignore[attr-defined]

    client.delete(f"/api/v1/jobs/{a['id']}/record")
    assert path.exists()  # b todavía lo usa
    assert client.get(f"/api/v1/jobs/{b['id']}/file").status_code == 200
    client.delete(f"/api/v1/jobs/{b['id']}/record")
    assert not path.exists()
