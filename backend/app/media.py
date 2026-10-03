"""Utilidades de medios con ffmpeg/ffprobe. Son extras: nunca deben romper una descarga."""

import os
import shutil
import subprocess  # noqa: S404
from pathlib import Path

MAX_TOP_LEVEL_BOXES = 512  # un MP4 normal tiene pocas; basta para ver si aparece un "moof"


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


def is_fragmented_mp4(path: Path) -> bool:
    """True si el MP4 viene en fragmentos (cajas "moof" en el nivel superior).

    Pasa con algunos HLS/DASH en fMP4 que yt-dlp une tal cual. El navegador tiene que recorrer
    los fragmentos uno a uno para saltar a un minuto: arranca lento al reanudar. Solo lee las
    cabeceras de las cajas (8-16 bytes cada una), no el archivo.
    """
    try:
        with path.open("rb") as f:
            end = path.stat().st_size
            pos = 0
            for _ in range(MAX_TOP_LEVEL_BOXES):
                if pos + 8 > end:
                    return False
                f.seek(pos)
                header = f.read(16)
                size, kind = int.from_bytes(header[:4], "big"), header[4:8]
                if kind == b"moof":
                    return True
                if size == 1:  # tamaño de 64 bits
                    size = int.from_bytes(header[8:16], "big")
                elif size == 0:  # la caja llega hasta el final
                    return False
                if size < 8:
                    return False  # no es un MP4 válido
                pos += size
    except OSError:
        return False
    return False


def faststart(path: Path) -> bool:
    """Reescribe el MP4 como archivo normal con el índice (moov) al inicio, sin recodificar.

    Usa un temporal y lo reemplaza de forma atómica: si ffmpeg falla, el original queda intacto.
    Devuelve True si el archivo cambió.
    """
    if not shutil.which("ffmpeg"):
        return False
    tmp = path.with_name(f"{path.stem}.faststart{path.suffix}")  # {job_id}.*: lo limpia _cleanup
    try:
        proc = subprocess.run(  # noqa: S603
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(path)]  # noqa: S607
            + ["-c", "copy", "-movflags", "+faststart", str(tmp)],
            capture_output=True,
            timeout=600,
            check=False,
        )
        if proc.returncode != 0 or not tmp.exists() or tmp.stat().st_size == 0:
            return False
        os.replace(tmp, path)
        return True
    except (OSError, subprocess.TimeoutExpired):
        return False
    finally:
        tmp.unlink(missing_ok=True)


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
