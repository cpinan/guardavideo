import logging
import shutil
from collections.abc import AsyncIterator, Sequence
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app.api import jobs
from app.config import Settings
from app.downloaders.base import Downloader
from app.downloaders.registry import default_downloaders
from app.exceptions import InvalidJobStateError, JobNotFoundError, UrlNotAllowedError
from app.repositories import SqliteJobRepository
from app.services.jobs import JobService
from app.services.recovery import recover_jobs

logger = logging.getLogger(__name__)


def create_app(
    settings: Settings | None = None, downloaders: Sequence[Downloader] | None = None
) -> FastAPI:
    """App factory: permite crear instancias con otra config o descargadores falsos (tests)."""
    cfg = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        logging.basicConfig(level=cfg.log_level)
        cfg.download_dir = cfg.download_dir.resolve()  # absoluta: la BD no depende del cwd
        cfg.download_dir.mkdir(parents=True, exist_ok=True)
        if not shutil.which("ffmpeg"):
            logger.warning("ffmpeg no encontrado: las descargas fallarán")
        repo = SqliteJobRepository(cfg.resolved_db_path)
        recover_jobs(repo, cfg.download_dir)
        service = JobService(repo, downloaders or default_downloaders(cfg), cfg)
        app.state.job_service = service
        yield
        service.shutdown()
        repo.close()

    app = FastAPI(title="Guardavideo", version="0.2.0", lifespan=lifespan)

    if cfg.cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=cfg.cors_origins,
            allow_methods=["GET", "POST", "PATCH", "DELETE"],
            allow_headers=["Content-Type"],
        )

    # Excepciones de dominio -> códigos HTTP (las rutas no repiten try/except)
    def _handler(status: int):  # type: ignore[no-untyped-def]
        async def handle(_: Request, exc: Exception) -> JSONResponse:
            return JSONResponse({"detail": str(exc) or exc.__class__.__name__}, status_code=status)

        return handle

    app.add_exception_handler(JobNotFoundError, _handler(404))
    app.add_exception_handler(InvalidJobStateError, _handler(409))
    app.add_exception_handler(UrlNotAllowedError, _handler(400))

    app.include_router(jobs.router)

    @app.get("/health", tags=["ops"])
    def health() -> dict[str, object]:
        return {"status": "ok", "ffmpeg": shutil.which("ffmpeg") is not None, "debug": cfg.debug}

    if cfg.static_dir is not None:
        # Se monta al final: /api y /health se registraron antes y tienen prioridad.
        if not (cfg.static_dir / "index.html").is_file():
            raise RuntimeError(f"{cfg.static_dir} no tiene index.html: ejecuta `npm run build`")
        app.mount("/", StaticFiles(directory=cfg.static_dir, html=True), name="frontend")

    return app


app = create_app()
