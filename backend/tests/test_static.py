"""Modo producción: la API también sirve el frontend compilado (un solo puerto, sin Vite)."""

from collections.abc import Callable
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

ClientFactory = Callable[..., TestClient]


def build(tmp_path: Path) -> Path:
    dist = tmp_path / "dist"
    (dist / "assets").mkdir(parents=True)
    (dist / "index.html").write_text("<!doctype html><title>Guardavideo</title>")
    (dist / "assets" / "app.js").write_text("console.log(1)")
    return dist


def test_serves_index_and_assets_when_static_dir_is_set(
    make_client: ClientFactory, tmp_path: Path
) -> None:
    client = make_client(static_dir=build(tmp_path))
    home = client.get("/")
    assert home.status_code == 200 and "Guardavideo" in home.text
    assert client.get("/assets/app.js").text == "console.log(1)"


def test_api_routes_still_win_over_static_files(make_client: ClientFactory, tmp_path: Path) -> None:
    client = make_client(static_dir=build(tmp_path))
    assert client.get("/api/v1/jobs").json() == []
    assert client.get("/health").json()["status"] == "ok"


def test_without_static_dir_the_root_is_not_served(make_client: ClientFactory) -> None:
    assert make_client().get("/").status_code == 404


def test_missing_build_folder_fails_fast(make_client: ClientFactory, tmp_path: Path) -> None:
    with pytest.raises(RuntimeError, match="npm run build"):
        make_client(static_dir=tmp_path / "no-dist")
