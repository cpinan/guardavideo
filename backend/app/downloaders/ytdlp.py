"""Descargas por sitio con yt-dlp como librería (YouTube, Vimeo, Dailymotion...).

Cada sitio tiene un 'extractor' que replica lo que hace su reproductor web para hallar los
streams. NO evade DRM: los formatos protegidos se omiten (allow_unplayable_formats=False).
"""

from pathlib import Path
from typing import Any

import yt_dlp

from app.downloaders.base import DownloadContext, DownloadResult
from app.thumbnails import extract_frame, fetch_remote, thumb_path


class YtDlpDownloader:
    name = "yt-dlp"

    def __init__(self, retries: int = 5, allow_private: bool = False) -> None:
        self._retries = retries
        self._allow_private = allow_private

    def supports(self, url: str) -> bool:
        return True  # descargador de respaldo: debe ir al final de la lista

    def download(
        self, url: str, dest_dir: Path, job_id: str, quality: int | None, ctx: DownloadContext
    ) -> DownloadResult:
        height = f"[height<={quality}]" if quality else ""
        state = {"finished": 0, "streams": 1}

        def hook(d: dict[str, Any]) -> None:
            ctx.check_cancelled()  # lanzar desde el hook aborta la descarga
            if d["status"] == "finished":
                state["finished"] += 1
            total = d.get("total_bytes") or d.get("total_bytes_estimate")
            if d["status"] == "downloading" and total:
                frac = d["downloaded_bytes"] / total
                # video y audio son streams separados: se reparte el progreso entre ellos
                ctx.report((state["finished"] + frac) / state["streams"] * 100)

        opts: dict[str, Any] = {
            "outtmpl": str(dest_dir / f"{job_id}.%(ext)s"),
            "format": f"bv*{height}+ba/b{height}",
            "merge_output_format": "mp4",
            "noplaylist": True,
            "allow_unplayable_formats": False,
            "quiet": True,
            "no_warnings": True,
            "retries": self._retries,
            "fragment_retries": self._retries,
            "extractor_retries": self._retries,
            "socket_timeout": 30,
            "progress_hooks": [hook],
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)  # 1) metadatos y formatos
            state["streams"] = len(info.get("requested_formats") or [info])
            if isinstance(info.get("title"), str) and info["title"]:
                ctx.title(info["title"])  # la UI deja de mostrar la URL cruda
            remote = info.get("thumbnail")  # vista previa inmediata, antes de bajar el video
            if isinstance(remote, str) and fetch_remote(
                remote, thumb_path(dest_dir, job_id), allow_private=self._allow_private
            ):
                ctx.thumbnail(thumb_path(dest_dir, job_id))
            result = ydl.process_ie_result(info, download=True)  # 2) descargar y unir

        downloads = result.get("requested_downloads") or []
        if downloads and downloads[0].get("filepath"):
            path = Path(downloads[0]["filepath"])
        else:
            path = next(p for p in dest_dir.glob(f"{job_id}.*") if p.suffix != ".part")
        ctx.report(100)
        if not thumb_path(dest_dir, job_id).exists() and extract_frame(
            path, thumb_path(dest_dir, job_id), (5.0, 1.0, 0.0)
        ):
            ctx.thumbnail(thumb_path(dest_dir, job_id))
        return DownloadResult(path, result.get("title"))
