"""MP4 fragmentado -> MP4 normal con el índice al inicio (arranque y saltos rápidos en <video>)."""

import shutil
import subprocess
from pathlib import Path
from typing import Any

import pytest

from app.domain import Job, Status
from app.downloaders import ytdlp
from app.downloaders.base import DownloadContext
from app.downloaders.ytdlp import YtDlpDownloader
from app.hashing import file_sha256
from app.media import faststart, is_fragmented_mp4, make_test_video, probe_duration
from app.repositories import SqliteJobRepository
from app.services.recovery import recover_jobs

needs_ffmpeg = pytest.mark.skipif(not shutil.which("ffmpeg"), reason="requiere ffmpeg")


def make_fragmented(dest: Path, seconds: int = 3) -> Path:
    """Como lo que deja yt-dlp con algunos HLS en fMP4: styp/moof/mdat repetidos."""
    subprocess.run(  # noqa: S603
        ["ffmpeg", "-y", "-loglevel", "error"]  # noqa: S607
        + ["-f", "lavfi", "-i", f"testsrc=duration={seconds}:size=160x120:rate=10"]
        + ["-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-g", "10"]
        + ["-movflags", "frag_keyframe+empty_moov+default_base_moof", str(dest)],
        check=True,
        capture_output=True,
    )
    return dest


def top_level_boxes(path: Path) -> list[str]:
    names, data = [], path.read_bytes()
    pos = 0
    while pos + 8 <= len(data):
        size = int.from_bytes(data[pos : pos + 4], "big")
        names.append(data[pos + 4 : pos + 8].decode("latin-1"))
        if size == 1:
            size = int.from_bytes(data[pos + 8 : pos + 16], "big")
        if size < 8:
            break
        pos += size
    return names


@needs_ffmpeg
def test_detects_fragmented_vs_regular(tmp_path: Path) -> None:
    regular = tmp_path / "regular.mp4"
    assert make_test_video(regular, seconds=2)
    assert not is_fragmented_mp4(regular)
    assert is_fragmented_mp4(make_fragmented(tmp_path / "frag.mp4"))


def test_non_mp4_or_garbage_is_not_fragmented(tmp_path: Path) -> None:
    junk = tmp_path / "junk.mp4"
    junk.write_bytes(b"\x00\x00")
    assert not is_fragmented_mp4(junk)
    assert not is_fragmented_mp4(tmp_path / "missing.mp4")


@needs_ffmpeg
def test_faststart_rewrites_in_place_with_index_first(tmp_path: Path) -> None:
    video = make_fragmented(tmp_path / "v.mp4")
    before = probe_duration(video)

    assert faststart(video)

    boxes = top_level_boxes(video)
    assert "moof" not in boxes
    assert boxes.index("moov") < boxes.index("mdat")
    assert probe_duration(video) == pytest.approx(before, abs=0.2)
    assert sorted(p.name for p in tmp_path.iterdir()) == ["v.mp4"]  # sin temporales


def test_faststart_failure_keeps_the_original(tmp_path: Path) -> None:
    junk = tmp_path / "v.mp4"
    junk.write_bytes(b"not a video")
    assert not faststart(junk)
    assert junk.read_bytes() == b"not a video"
    assert sorted(p.name for p in tmp_path.iterdir()) == ["v.mp4"]


@needs_ffmpeg
def test_ytdlp_result_is_rewritten_when_fragmented(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    sample = make_fragmented(tmp_path / "sample.mp4")

    class FragmentedYDL:
        def __init__(self, opts: dict[str, Any]) -> None:
            self.opts = opts

        def __enter__(self) -> "FragmentedYDL":
            return self

        def __exit__(self, *a: object) -> None:
            return None

        def extract_info(self, url: str, download: bool) -> dict[str, Any]:
            return {"title": "T"}

        def process_ie_result(self, info: dict[str, Any], download: bool) -> dict[str, Any]:
            out = Path(self.opts["outtmpl"].replace("%(ext)s", "mp4"))
            shutil.copy(sample, out)
            return {"title": "T", "requested_downloads": [{"filepath": str(out)}]}

    monkeypatch.setattr(ytdlp.yt_dlp, "YoutubeDL", FragmentedYDL)
    dest = tmp_path / "out"
    dest.mkdir()
    result = YtDlpDownloader().download(
        "https://site/v", dest, "job1", None, DownloadContext(report_percent=lambda p: None)
    )
    assert result.path == dest / "job1.mp4"
    assert not is_fragmented_mp4(result.path)


@needs_ffmpeg
def test_recovery_rewrites_old_fragmented_files_and_updates_size_and_hash(tmp_path: Path) -> None:
    video = make_fragmented(tmp_path / "old.mp4")
    repo = SqliteJobRepository(tmp_path / "r.db")
    old_hash = file_sha256(video)
    for job_id in ("a", "b"):  # dos jobs comparten el archivo (dedupe por contenido)
        repo.add(
            Job(
                id=job_id,
                url=f"https://x/{job_id}",
                status=Status.done,
                file_path=video,
                url_hash=job_id,
                size_bytes=video.stat().st_size,
                content_hash=old_hash,
            )
        )

    recover_jobs(repo, tmp_path)

    assert not is_fragmented_mp4(video)
    for job_id in ("a", "b"):
        job = repo.get(job_id)
        assert job.size_bytes == video.stat().st_size
        assert job.content_hash == file_sha256(video) != old_hash
    repo.close()
