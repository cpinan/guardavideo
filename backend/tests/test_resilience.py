"""Reintentos de red y miniaturas. Los fallos se inyectan en un servidor HTTP local."""

import functools
import http.server
import shutil
import subprocess
import threading
from collections.abc import Iterator
from pathlib import Path

import httpx
import pytest

from app.downloaders.base import DownloadContext
from app.downloaders.hls import HlsDownloader
from app.thumbnails import extract_frame, make_test_card, thumb_path

needs_ffmpeg = pytest.mark.skipif(not shutil.which("ffmpeg"), reason="requiere ffmpeg")


class Flaky:
    """Estado compartido del servidor: cuántas peticiones a segmentos deben fallar todavía."""

    def __init__(self) -> None:
        self.failures_left = 0
        self.status = 503
        self.hits = 0


@pytest.fixture(scope="module")
def flaky_server(tmp_path_factory: pytest.TempPathFactory) -> Iterator[tuple[str, Flaky]]:
    if not shutil.which("ffmpeg"):
        pytest.skip("requiere ffmpeg")
    root = tmp_path_factory.mktemp("flaky")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i"]
        + ["testsrc=duration=4:size=320x180:rate=25", "-c:v", "libx264", "-g", "50", "-f", "hls"]
        + ["-hls_time", "2", "-hls_playlist_type", "vod", str(root / "index.m3u8")],
        check=True,
    )
    state = Flaky()

    class Handler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args: object) -> None:
            pass

        def do_GET(self) -> None:  # noqa: N802
            if self.path.endswith(".ts"):
                state.hits += 1
                if state.failures_left > 0:
                    state.failures_left -= 1
                    self.send_error(state.status)
                    return
            super().do_GET()

    server = http.server.ThreadingHTTPServer(
        ("127.0.0.1", 0), functools.partial(Handler, directory=str(root))
    )
    threading.Thread(target=server.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{server.server_address[1]}", state
    server.shutdown()


def fast(retries: int = 5) -> HlsDownloader:
    return HlsDownloader(workers=1, retries=retries, backoff=0.01)


@pytest.mark.integration
@needs_ffmpeg
def test_recovers_after_transient_failures(flaky_server: tuple[str, Flaky], tmp_path: Path) -> None:
    base, state = flaky_server
    state.failures_left, state.status = 3, 503
    attempts: list[int] = []
    ctx = DownloadContext(report_percent=lambda p: None, report_retry=attempts.append)

    result = fast().download(f"{base}/index.m3u8", tmp_path, "r1", None, ctx)

    assert result.path.exists()
    assert max(attempts) >= 1  # avisó que estaba reintentando
    assert attempts[-1] == 0  # y avisó que se recuperó


@pytest.mark.integration
@needs_ffmpeg
def test_gives_up_after_exhausting_retries(flaky_server: tuple[str, Flaky], tmp_path: Path) -> None:
    base, state = flaky_server
    state.failures_left, state.status = 100, 503
    ctx = DownloadContext(report_percent=lambda p: None)

    with pytest.raises(httpx.HTTPStatusError):
        fast(retries=2).download(f"{base}/index.m3u8", tmp_path, "r2", None, ctx)
    state.failures_left = 0


@pytest.mark.integration
@needs_ffmpeg
def test_client_errors_are_not_retried(flaky_server: tuple[str, Flaky], tmp_path: Path) -> None:
    base, state = flaky_server
    state.failures_left, state.status, state.hits = 100, 404, 0
    ctx = DownloadContext(report_percent=lambda p: None)

    with pytest.raises(httpx.HTTPStatusError):
        fast().download(f"{base}/index.m3u8", tmp_path, "r3", None, ctx)
    assert state.hits <= 2  # un 404 no mejora reintentando (con reintentos serían >= 6)
    state.failures_left = 0


@pytest.mark.integration
@needs_ffmpeg
def test_cancel_interrupts_backoff(flaky_server: tuple[str, Flaky], tmp_path: Path) -> None:
    from app.exceptions import DownloadCancelledError

    base, state = flaky_server
    state.failures_left, state.status = 100, 503
    ctx = DownloadContext(report_percent=lambda p: None, report_retry=lambda n: ctx.cancel.set())

    with pytest.raises(DownloadCancelledError):
        HlsDownloader(workers=1, retries=5, backoff=30).download(
            f"{base}/index.m3u8", tmp_path, "r4", None, ctx
        )  # con backoff de 30 s, solo termina rápido si la cancelación despierta la espera
    state.failures_left = 0


@pytest.mark.integration
@needs_ffmpeg
def test_hls_emits_early_and_final_thumbnail(
    flaky_server: tuple[str, Flaky], tmp_path: Path
) -> None:
    base, state = flaky_server
    state.failures_left = 0
    seen: list[Path] = []
    ctx = DownloadContext(report_percent=lambda p: None, report_thumbnail=seen.append)

    fast().download(f"{base}/index.m3u8", tmp_path, "t1", None, ctx)

    assert len(seen) == 2  # temprana (1er segmento) + definitiva (video final)
    assert thumb_path(tmp_path, "t1").read_bytes()[:2] == b"\xff\xd8"  # JPEG


@needs_ffmpeg
def test_extract_frame_and_fallback_offsets(tmp_path: Path) -> None:
    card = tmp_path / "card.jpg"
    assert make_test_card(card)
    out = tmp_path / "out.jpg"
    assert extract_frame(card, out, offsets=(99.0, 0.0))  # el 1er offset no existe: usa el 2º
    assert out.stat().st_size > 0
    assert not list(tmp_path.glob("*.tmp.jpg"))  # sin restos


def test_extract_frame_returns_false_for_garbage(tmp_path: Path) -> None:
    bad = tmp_path / "bad.bin"
    bad.write_bytes(b"not a video")
    assert extract_frame(bad, tmp_path / "x.jpg") is False
