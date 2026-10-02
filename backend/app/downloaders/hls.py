"""Descargador HLS propio: playlist -> segmentos (en paralelo, a disco) -> ffmpeg."""

import shutil
import subprocess  # noqa: S404
import threading
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.parse import urlparse

import httpx
import m3u8
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from app.downloaders.base import DownloadContext, DownloadResult
from app.thumbnails import extract_frame, thumb_path

HEADERS = {"User-Agent": "Mozilla/5.0 (hls-web learning project)"}


def decrypt_aes128(data: bytes, key: bytes, iv: bytes) -> bytes:
    """AES-128-CBC con padding PKCS7, como define HLS (esto NO es DRM)."""
    dec = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    out = dec.update(data) + dec.finalize()
    return out[: -out[-1]]


def pick_variant(master: m3u8.M3U8, quality: int | None) -> m3u8.Playlist:
    """Elige la variante: la más cercana a `quality` (altura) o la de mayor bitrate."""
    variants = master.playlists
    if quality:
        return min(variants, key=lambda v: abs((v.stream_info.resolution or (0, 0))[1] - quality))
    return max(variants, key=lambda v: v.stream_info.bandwidth or 0)


class _KeyCache:
    """Cada clave AES se descarga una sola vez aunque varios hilos la pidan."""

    def __init__(self, fetch: Callable[[str], bytes]) -> None:
        self._fetch = fetch
        self._keys: dict[str, bytes] = {}
        self._lock = threading.Lock()

    def get(self, uri: str) -> bytes:
        with self._lock:
            if uri not in self._keys:
                self._keys[uri] = self._fetch(uri)
            return self._keys[uri]


class HlsDownloader:
    name = "hls"

    def __init__(
        self,
        workers: int = 8,
        timeout: float = 30.0,
        retries: int = 5,
        backoff: float = 0.5,
        max_delay: float = 8.0,
    ) -> None:
        self._workers = workers
        self._timeout = timeout
        self._retries = retries
        self._backoff = backoff
        self._max_delay = max_delay

    def supports(self, url: str) -> bool:
        return urlparse(url).path.endswith(".m3u8")

    def _get(self, client: httpx.Client, url: str, ctx: DownloadContext) -> bytes:
        """GET con reintentos. Un 4xx (salvo 408/429) no se reintenta: no es un fallo de red."""
        for attempt in range(self._retries + 1):
            ctx.check_cancelled()
            try:
                r = client.get(url)
                r.raise_for_status()
                if attempt:
                    ctx.retrying(0)  # se recuperó
                return r.content
            except httpx.HTTPStatusError as e:
                code = e.response.status_code
                if attempt == self._retries or (400 <= code < 500 and code not in (408, 429)):
                    raise
            except httpx.HTTPError:
                if attempt == self._retries:
                    raise
            ctx.retrying(attempt + 1)
            ctx.sleep(min(self._backoff * 2**attempt, self._max_delay))
        raise RuntimeError("unreachable")

    def _media_playlist(
        self, client: httpx.Client, url: str, quality: int | None, ctx: DownloadContext
    ) -> m3u8.M3U8:
        playlist = m3u8.loads(self._get(client, url, ctx).decode(), uri=url)
        if not playlist.is_variant:
            return playlist
        chosen = pick_variant(playlist, quality)
        return m3u8.loads(
            self._get(client, chosen.absolute_uri, ctx).decode(), uri=chosen.absolute_uri
        )

    def download(
        self, url: str, dest_dir: Path, job_id: str, quality: int | None, ctx: DownloadContext
    ) -> DownloadResult:
        if not shutil.which("ffmpeg"):
            raise RuntimeError("ffmpeg no está instalado")
        out_path = dest_dir / f"{job_id}.mp4"

        with (
            httpx.Client(headers=HEADERS, follow_redirects=True, timeout=self._timeout) as client,
            TemporaryDirectory() as tmp,
        ):
            media = self._media_playlist(client, url, quality, ctx)
            segments = media.segments
            if not segments:
                raise ValueError("El playlist no tiene segmentos")
            keys = _KeyCache(lambda uri: self._get(client, uri, ctx))
            tmpdir = Path(tmp)

            def fetch(index: int, seg: m3u8.Segment) -> Path:
                ctx.check_cancelled()
                data = self._get(client, seg.absolute_uri, ctx)
                if seg.key and seg.key.method == "AES-128":
                    iv = (
                        int(seg.key.iv, 16).to_bytes(16, "big")
                        if seg.key.iv
                        else ((media.media_sequence or 0) + index).to_bytes(16, "big")
                    )
                    data = decrypt_aes128(data, keys.get(seg.key.absolute_uri), iv)
                elif seg.key and seg.key.method not in (None, "NONE"):
                    raise ValueError(f"Cifrado no soportado: {seg.key.method}")
                part = tmpdir / f"seg_{index:06d}"
                part.write_bytes(data)  # a disco: memoria acotada aunque el video sea enorme
                return part

            raw = tmpdir / "raw.bin"
            total = len(segments)
            with ThreadPoolExecutor(self._workers) as pool, raw.open("wb") as out:
                futures = [pool.submit(fetch, i, s) for i, s in enumerate(segments)]
                try:
                    if segments[0].init_section:  # fMP4: el segmento de inicialización va primero
                        out.write(self._get(client, segments[0].init_section.absolute_uri, ctx))
                    for n, fut in enumerate(futures, start=1):
                        part = fut.result()
                        # vista previa temprana: primer fotograma del primer segmento
                        if n == 1 and extract_frame(part, thumb_path(dest_dir, job_id), (1.0, 0.0)):
                            ctx.thumbnail(thumb_path(dest_dir, job_id))
                        with part.open("rb") as src:
                            shutil.copyfileobj(src, out)
                        part.unlink()
                        ctx.report(n / total * 95)  # el 5% restante es el remux
                except BaseException:
                    for pending in futures:
                        pending.cancel()
                    raise

            ctx.check_cancelled()
            proc = subprocess.run(  # noqa: S603
                ["ffmpeg", "-y", "-loglevel", "error", "-i", str(raw), "-c", "copy", str(out_path)],  # noqa: S607
                capture_output=True,
                text=True,
                check=False,
            )
            if proc.returncode != 0:
                raise RuntimeError(f"ffmpeg falló: {proc.stderr.strip()[:200]}")
            ctx.report(100)
            if extract_frame(out_path, thumb_path(dest_dir, job_id), (5.0, 1.0, 0.0)):
                ctx.thumbnail(thumb_path(dest_dir, job_id))  # versión definitiva
        return DownloadResult(out_path, Path(urlparse(url).path).stem or None)
