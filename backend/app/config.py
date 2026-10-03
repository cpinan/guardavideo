"""Configuración 12-factor: todo viene de variables de entorno (prefijo HLSWEB_)."""

from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="HLSWEB_", env_file=".env", extra="ignore")

    download_dir: Path = Path("downloads")
    # Base de datos SQLite con el historial de descargas. Por defecto vive junto a los videos.
    db_path: Path | None = None
    max_concurrent_jobs: int = Field(2, ge=1, le=16)
    segment_workers: int = Field(8, ge=1, le=32)
    # Reintentos de red por segmento/petición, con backoff exponencial (tope 8 s)
    network_retries: int = Field(5, ge=0, le=20)
    retry_backoff: float = Field(0.5, ge=0, le=10)
    # Solo desarrollo: habilita escenarios simulados en http://debug.test/<escenario>
    debug: bool = False
    # Solo pruebas locales: permite localhost/redes privadas (desactiva la protección SSRF)
    allow_private_urls: bool = False
    # Con el proxy de Vite en desarrollo no hace falta CORS.
    # Ej: HLSWEB_CORS_ORIGINS='["https://app.example"]'
    cors_origins: list[str] = []
    # Producción: carpeta del frontend compilado (frontend/dist). La API la sirve en "/" y ya no
    # hace falta Vite: un solo puerto, mismo origen. Ej: HLSWEB_STATIC_DIR=../frontend/dist
    static_dir: Path | None = None
    log_level: str = "INFO"

    @property
    def resolved_db_path(self) -> Path:
        return self.db_path or self.download_dir / "hlsweb.db"
