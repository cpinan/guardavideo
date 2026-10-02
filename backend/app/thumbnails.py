"""Miniaturas: un fotograma JPEG por job. Es un extra: nunca debe romper una descarga."""

import logging
import shutil
import subprocess  # noqa: S404
from collections.abc import Sequence
from pathlib import Path
from tempfile import TemporaryDirectory

import httpx

from app.exceptions import UrlNotAllowedError
from app.security import assert_public_url

logger = logging.getLogger(__name__)

WIDTH = 480
MAX_REMOTE_BYTES = 3_000_000


def thumb_path(dest_dir: Path, job_id: str) -> Path:
    return dest_dir / f"{job_id}_thumb.jpg"


def _ffmpeg(args: list[str]) -> bool:
    try:
        proc = subprocess.run(  # noqa: S603
            ["ffmpeg", "-y", "-loglevel", "error", *args],  # noqa: S607
            capture_output=True,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    return proc.returncode == 0


def extract_frame(src: Path, dest: Path, offsets: Sequence[float] = (1.0, 0.0)) -> bool:
    """Guarda un fotograma de `src` (video o imagen). Prueba cada offset hasta que uno funcione."""
    if not shutil.which("ffmpeg"):
        return False
    tmp = dest.with_suffix(".tmp.jpg")  # escritura atómica: nunca se sirve un JPEG a medias
    for offset in offsets:
        tmp.unlink(missing_ok=True)
        seek = ["-ss", str(offset)] if offset > 0 else []  # -ss en una imagen no produce nada
        ok = _ffmpeg(
            [*seek, "-i", str(src), "-frames:v", "1", "-update", "1"]
            + ["-vf", f"scale={WIDTH}:-2", str(tmp)]
        )
        if ok and tmp.exists() and tmp.stat().st_size > 0:
            tmp.replace(dest)
            return True
    tmp.unlink(missing_ok=True)
    return False


def fetch_remote(url: str, dest: Path, *, allow_private: bool = False) -> bool:
    """Descarga la miniatura que publica el sitio y la normaliza a JPEG. Falla en silencio."""
    try:
        assert_public_url(url, allow_private=allow_private)
        with (
            TemporaryDirectory() as tmp,
            httpx.Client(follow_redirects=False, timeout=10.0) as client,
            client.stream("GET", url) as r,
        ):
            r.raise_for_status()
            raw = Path(tmp) / "remote"
            size = 0
            with raw.open("wb") as out:
                for chunk in r.iter_bytes():
                    size += len(chunk)
                    if size > MAX_REMOTE_BYTES:
                        return False
                    out.write(chunk)
            return extract_frame(raw, dest, offsets=(0.0,))
    except (httpx.HTTPError, UrlNotAllowedError, OSError) as e:
        logger.info("miniatura remota no disponible: %s", e)
        return False


def make_test_card(dest: Path, seed: int = 0) -> bool:
    """Imagen sintética para los escenarios de depuración (sin red)."""
    if not shutil.which("ffmpeg"):
        return False
    return _ffmpeg(
        ["-f", "lavfi", "-i", f"testsrc=size={WIDTH}x270:rate=1:decimals={seed % 3}"]
        + ["-frames:v", "1", str(dest)]
    )
