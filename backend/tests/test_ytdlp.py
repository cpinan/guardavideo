"""yt-dlp se simula: sin red. Se comprueba la traducción de opciones, progreso y cancelación."""

from pathlib import Path
from typing import Any

import pytest

from app.downloaders import ytdlp
from app.downloaders.base import DownloadContext
from app.downloaders.ytdlp import YtDlpDownloader
from app.exceptions import DownloadCancelledError


class FakeYDL:
    last_opts: dict[str, Any] = {}
    events: list[dict[str, Any]] = []

    def __init__(self, opts: dict[str, Any]) -> None:
        FakeYDL.last_opts = opts
        self.opts = opts

    def __enter__(self) -> "FakeYDL":
        return self

    def __exit__(self, *a: object) -> None:
        return None

    def extract_info(self, url: str, download: bool) -> dict[str, Any]:
        return {"requested_formats": [{}, {}]}  # video + audio

    def process_ie_result(self, info: dict[str, Any], download: bool) -> dict[str, Any]:
        for ev in FakeYDL.events:
            self.opts["progress_hooks"][0](ev)
        out = Path(self.opts["outtmpl"].replace("%(ext)s", "mp4"))
        out.write_bytes(b"v")
        return {"title": "T", "requested_downloads": [{"filepath": str(out)}]}


@pytest.fixture(autouse=True)
def fake_ydl(monkeypatch: pytest.MonkeyPatch) -> None:
    FakeYDL.events = []
    monkeypatch.setattr(ytdlp.yt_dlp, "YoutubeDL", FakeYDL)


def run(tmp_path: Path, quality: int | None, ctx: DownloadContext) -> Any:
    return YtDlpDownloader().download("https://site/v", tmp_path, "job1", quality, ctx)


def test_supports_everything_as_fallback() -> None:
    assert YtDlpDownloader().supports("https://anything.example/x")


def test_options_respect_quality_and_never_allow_drm(tmp_path: Path) -> None:
    run(tmp_path, 720, DownloadContext(report_percent=lambda p: None))
    opts = FakeYDL.last_opts
    assert opts["allow_unplayable_formats"] is False
    assert "[height<=720]" in opts["format"]
    assert opts["noplaylist"] is True


def test_no_quality_means_no_height_filter(tmp_path: Path) -> None:
    run(tmp_path, None, DownloadContext(report_percent=lambda p: None))
    assert "height" not in FakeYDL.last_opts["format"]


def test_progress_is_split_across_streams_and_ends_at_100(tmp_path: Path) -> None:
    FakeYDL.events = [
        {"status": "downloading", "downloaded_bytes": 50, "total_bytes": 100},
        {"status": "finished"},
        {"status": "downloading", "downloaded_bytes": 50, "total_bytes": 100},
    ]
    seen: list[float] = []
    result = run(tmp_path, None, DownloadContext(report_percent=seen.append))
    assert seen[0] == 25.0  # mitad del primer stream de dos
    assert 75.0 in seen
    assert seen[-1] == 100
    assert result.title == "T" and result.path.exists()


def test_cancel_aborts_from_progress_hook(tmp_path: Path) -> None:
    FakeYDL.events = [{"status": "downloading", "downloaded_bytes": 1, "total_bytes": 100}]
    ctx = DownloadContext(report_percent=lambda p: None)
    ctx.cancel.set()
    with pytest.raises(DownloadCancelledError):
        run(tmp_path, None, ctx)
