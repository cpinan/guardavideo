from app.downloaders.base import Downloader
from app.downloaders.hls import HlsDownloader
from app.downloaders.registry import select_downloader
from app.downloaders.ytdlp import YtDlpDownloader

DOWNLOADERS: list[Downloader] = [HlsDownloader(), YtDlpDownloader()]


def test_m3u8_goes_to_hls() -> None:
    assert select_downloader("https://cdn.example/a/index.m3u8?token=1", DOWNLOADERS).name == "hls"


def test_other_urls_fall_back_to_ytdlp() -> None:
    assert select_downloader("https://www.youtube.com/watch?v=abc", DOWNLOADERS).name == "yt-dlp"
