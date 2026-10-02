"""Pruebas del descargador HLS. Las de integración generan streams reales con ffmpeg."""

import functools
import http.server
import shutil
import subprocess
import threading
from collections.abc import Iterator
from pathlib import Path

import m3u8
import pytest
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from app.downloaders.base import DownloadContext
from app.downloaders.hls import HlsDownloader, decrypt_aes128, pick_variant
from app.exceptions import DownloadCancelledError

MASTER = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
low.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080
high.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
mid.m3u8
"""


# ---------- unitarias (rápidas, sin red) ----------
def test_decrypt_roundtrip() -> None:
    key, iv, plain = b"k" * 16, b"i" * 16, b"hola segmento" * 50
    padder = padding.PKCS7(128).padder()
    enc = Cipher(algorithms.AES(key), modes.CBC(iv)).encryptor()
    ciphertext = enc.update(padder.update(plain) + padder.finalize()) + enc.finalize()
    assert decrypt_aes128(ciphertext, key, iv) == plain


def test_pick_variant_highest_bitrate_by_default() -> None:
    master = m3u8.loads(MASTER, uri="https://x.test/master.m3u8")
    assert pick_variant(master, None).uri == "high.m3u8"


@pytest.mark.parametrize(
    "wanted,expected", [(360, "low.m3u8"), (700, "mid.m3u8"), (1080, "high.m3u8")]
)
def test_pick_variant_closest_height(wanted: int, expected: str) -> None:
    master = m3u8.loads(MASTER, uri="https://x.test/master.m3u8")
    assert pick_variant(master, wanted).uri == expected


def test_supports_only_m3u8() -> None:
    d = HlsDownloader()
    assert d.supports("https://a.test/x/index.m3u8?t=1")
    assert not d.supports("https://a.test/watch?v=1")


# ---------- integración (ffmpeg + servidor HTTP local) ----------
needs_ffmpeg = pytest.mark.skipif(not shutil.which("ffmpeg"), reason="requiere ffmpeg")


def _ffmpeg(*args: str) -> None:
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *args], check=True)


@pytest.fixture(scope="module")
def hls_server(tmp_path_factory: pytest.TempPathFactory) -> Iterator[str]:
    if not shutil.which("ffmpeg"):
        pytest.skip("requiere ffmpeg")
    root = tmp_path_factory.mktemp("hls")
    src = ["-f", "lavfi", "-i", "testsrc=duration=6:size=320x180:rate=25"]
    enc_args = [
        "-c:v",
        "libx264",
        "-g",
        "50",
        "-f",
        "hls",
        "-hls_time",
        "2",
        "-hls_playlist_type",
        "vod",
    ]
    _ffmpeg(
        *src, *enc_args, "-hls_segment_filename", str(root / "seg%d.ts"), str(root / "plain.m3u8")
    )

    (root / "enc").mkdir()
    (root / "enc" / "enc.key").write_bytes(bytes(range(16)))
    (root / "keyinfo.txt").write_text(
        f"enc.key\n{root / 'enc' / 'enc.key'}\n00112233445566778899aabbccddeeff\n"
    )
    _ffmpeg(
        *src,
        *enc_args,
        "-hls_key_info_file",
        str(root / "keyinfo.txt"),
        "-hls_segment_filename",
        str(root / "enc" / "seg%d.ts"),
        str(root / "enc" / "index.m3u8"),
    )

    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args: object) -> None:
            pass

    handler = functools.partial(Quiet, directory=str(root))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()


def _duration(path: Path) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True,
        text=True,
        check=True,
    )
    return float(out.stdout)


@pytest.mark.integration
@needs_ffmpeg
@pytest.mark.parametrize("playlist", ["plain.m3u8", "enc/index.m3u8"])
def test_downloads_plain_and_aes128_streams(hls_server: str, tmp_path: Path, playlist: str) -> None:
    progress: list[float] = []
    ctx = DownloadContext(report_percent=progress.append)
    result = HlsDownloader().download(f"{hls_server}/{playlist}", tmp_path, "job1", None, ctx)

    assert result.path.exists()
    assert _duration(result.path) == pytest.approx(6.0, abs=0.5)
    assert progress[-1] == 100 and progress == sorted(progress)


@pytest.mark.integration
@needs_ffmpeg
def test_cancel_before_start_raises(hls_server: str, tmp_path: Path) -> None:
    ctx = DownloadContext(report_percent=lambda p: None)
    ctx.cancel.set()
    with pytest.raises(DownloadCancelledError):
        HlsDownloader().download(f"{hls_server}/plain.m3u8", tmp_path, "job2", None, ctx)
    assert not list(tmp_path.glob("job2.*"))  # no deja archivos a medias
