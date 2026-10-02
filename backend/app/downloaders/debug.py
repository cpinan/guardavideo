"""Escenarios simulados para validar la UI sin red (solo con HLSWEB_DEBUG=true).

Se piden como http://debug.test/<escenario>. El dominio `.test` está reservado (RFC 2606) y
nunca se resuelve ni se contacta: no hay tráfico de red ni se debilita la protección SSRF.
"""

from pathlib import Path
from urllib.parse import urlparse

from app.downloaders.base import DownloadContext, DownloadResult
from app.thumbnails import make_test_card, thumb_path

DEBUG_HOST = "debug.test"

# nombre -> descripción (la UI de depuración la muestra)
SCENARIOS: dict[str, str] = {
    "ok": "Termina bien en ~4 s",
    "slow": "Termina bien en ~30 s (para probar cancelar y refrescar)",
    "fail": "Falla de inmediato",
    "late-fail": "Falla al 60 %",
    "flaky": "Pierde la red 3 veces y se recupera solo",
    "network": "Pierde la red y agota los reintentos",
    "stall": "Se queda colgado al 40 % hasta cancelar",
}


def is_debug_url(url: str) -> bool:
    return urlparse(url).hostname == DEBUG_HOST


class DebugDownloader:
    name = "debug"

    def __init__(self, tick: float = 0.2) -> None:
        self._tick = tick

    def supports(self, url: str) -> bool:
        return is_debug_url(url)

    def _advance(self, ctx: DownloadContext, start: float, end: float, steps: int) -> None:
        for i in range(1, steps + 1):
            ctx.sleep(self._tick)
            ctx.report(start + (end - start) * i / steps)

    def _preview(self, ctx: DownloadContext, dest_dir: Path, job_id: str, rev: int) -> None:
        if make_test_card(thumb_path(dest_dir, job_id), rev):
            ctx.thumbnail(thumb_path(dest_dir, job_id))

    def download(
        self, url: str, dest_dir: Path, job_id: str, quality: int | None, ctx: DownloadContext
    ) -> DownloadResult:
        scenario = urlparse(url).path.strip("/") or "ok"
        if scenario not in SCENARIOS:
            raise ValueError(f"Escenario desconocido: {scenario}")

        if scenario != "fail":
            self._preview(ctx, dest_dir, job_id, 0)  # el sitio "publica" su miniatura al inicio
        if scenario == "fail":
            raise RuntimeError("Fallo simulado: el sitio no es compatible")
        if scenario == "stall":
            self._advance(ctx, 0, 40, 8)
            while True:  # colgado: solo la cancelación lo despierta
                ctx.sleep(self._tick)
        if scenario == "network":
            self._advance(ctx, 0, 20, 4)
            for attempt in range(1, 6):
                ctx.retrying(attempt)
                ctx.sleep(self._tick * 4)
            raise RuntimeError("Sin conexión: se agotaron los reintentos")

        steps = 150 if scenario == "slow" else 20
        if scenario == "flaky":
            self._advance(ctx, 0, 30, steps // 4)
            for attempt in range(1, 4):
                ctx.retrying(attempt)
                ctx.sleep(self._tick * 5)
            ctx.retrying(0)
            self._advance(ctx, 30, 100, steps - steps // 4)
        elif scenario == "late-fail":
            self._advance(ctx, 0, 60, steps)
            raise RuntimeError("Fallo simulado al 60 %: segmento corrupto")
        else:
            self._advance(ctx, 0, 100, steps)

        self._preview(ctx, dest_dir, job_id, 1)  # versión definitiva
        path = dest_dir / f"{job_id}.mp4"
        path.write_bytes(b"debug-video")
        return DownloadResult(path, f"Debug · {scenario}")
