import pytest

from app.config import Settings


def test_defaults_are_safe() -> None:
    s = Settings(_env_file=None)
    assert s.allow_private_urls is False
    assert s.max_concurrent_jobs == 2


def test_env_prefix_overrides(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("HLSWEB_MAX_CONCURRENT_JOBS", "4")
    monkeypatch.setenv("HLSWEB_ALLOW_PRIVATE_URLS", "true")
    s = Settings(_env_file=None)
    assert s.max_concurrent_jobs == 4
    assert s.allow_private_urls is True


def test_out_of_range_concurrency_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("HLSWEB_MAX_CONCURRENT_JOBS", "0")
    with pytest.raises(ValueError):
        Settings(_env_file=None)
