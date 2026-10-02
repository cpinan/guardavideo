import threading
import time
from collections.abc import Callable, Iterator
from contextlib import ExitStack
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.downloaders.base import DownloadContext, DownloadResult
from app.main import create_app


class FakeDownloader:
    """Doble de prueba: sin red ni ffmpeg. Se puede pausar para probar cancelación."""

    name = "fake"

    def __init__(
        self, steps: int = 4, hold: threading.Event | None = None, fail: str | None = None
    ):
        self.steps, self.hold, self.fail = steps, hold, fail
        self.started = threading.Event()

    def supports(self, url: str) -> bool:
        return True

    def download(
        self, url: str, dest_dir: Path, job_id: str, quality: int | None, ctx: DownloadContext
    ) -> DownloadResult:
        self.started.set()
        for i in range(self.steps):
            ctx.check_cancelled()
            while self.hold is not None and i == 1 and not self.hold.is_set():
                ctx.check_cancelled()
                time.sleep(0.01)
            ctx.report((i + 1) / self.steps * 100)
        if self.fail:
            raise RuntimeError(self.fail)
        path = dest_dir / f"{job_id}.mp4"
        path.write_bytes(b"fake-video")
        return DownloadResult(path, "Mi video")


@pytest.fixture
def make_client(tmp_path: Path) -> Iterator[Callable[..., TestClient]]:
    """Fábrica de clientes: cada test decide qué descargador y qué settings usar."""
    with ExitStack() as stack:

        def factory(downloader: FakeDownloader | None = None, **settings: object) -> TestClient:
            settings.setdefault("allow_private_urls", True)
            cfg = Settings(download_dir=tmp_path, **settings)  # type: ignore[arg-type]
            app = create_app(cfg, [downloader or FakeDownloader()])
            return stack.enter_context(TestClient(app))  # ejecuta el lifespan

        yield factory


def wait_for(client: TestClient, job_id: str, statuses: set[str], timeout: float = 5.0) -> dict:
    """Espera activa con timeout (sin sleeps fijos): evita tests lentos o intermitentes."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        body = client.get(f"/api/v1/jobs/{job_id}").json()
        if body["status"] in statuses:
            return body
        time.sleep(0.01)
    raise AssertionError(f"El job no llegó a {statuses}")
