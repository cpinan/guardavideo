from collections.abc import Sequence

from app.config import Settings
from app.downloaders.base import Downloader
from app.downloaders.debug import DebugDownloader
from app.downloaders.hls import HlsDownloader
from app.downloaders.ytdlp import YtDlpDownloader


def default_downloaders(settings: Settings) -> list[Downloader]:
    # El orden importa: el primero que 'supports' gana; yt-dlp es el respaldo.
    real: list[Downloader] = [
        HlsDownloader(
            workers=settings.segment_workers,
            retries=settings.network_retries,
            backoff=settings.retry_backoff,
        ),
        YtDlpDownloader(
            retries=settings.network_retries, allow_private=settings.allow_private_urls
        ),
    ]
    return [DebugDownloader(), *real] if settings.debug else real


def select_downloader(url: str, downloaders: Sequence[Downloader]) -> Downloader:
    for d in downloaders:
        if d.supports(url):
            return d
    raise ValueError("Ningún descargador soporta esta URL")
