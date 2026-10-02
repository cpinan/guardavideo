"""Utilidades de medios con ffmpeg/ffprobe. Son extras: nunca deben romper una descarga."""

import shutil
import subprocess  # noqa: S404
from pathlib import Path


def probe_duration(path: Path) -> float | None:
    """Duración en segundos, o None si no se puede leer (ffprobe ausente, archivo raro)."""
    if not shutil.which("ffprobe"):
        return None
    try:
        proc = subprocess.run(  # noqa: S603
            ["ffprobe", "-v", "error", "-show_entries", "format=duration"]  # noqa: S607
            + ["-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
        seconds = float(proc.stdout.strip())
    except (OSError, subprocess.TimeoutExpired, ValueError):
        return None
    return seconds if proc.returncode == 0 and seconds > 0 else None


def make_test_video(dest: Path, seconds: int = 12) -> bool:
    """MP4 sintético y reproducible en navegadores, para los escenarios de depuración."""
    if not shutil.which("ffmpeg"):
        return False
    try:
        proc = subprocess.run(  # noqa: S603
            ["ffmpeg", "-y", "-loglevel", "error"]  # noqa: S607
            + ["-f", "lavfi", "-i", f"testsrc=duration={seconds}:size=320x180:rate=15"]
            + ["-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}"]
            + ["-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p"]
            + ["-c:a", "aac", "-b:a", "48k", "-movflags", "+faststart", "-shortest", str(dest)],
            capture_output=True,
            timeout=60,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    return proc.returncode == 0 and dest.exists()
