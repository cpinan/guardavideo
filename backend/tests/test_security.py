import socket

import pytest

from app.exceptions import UrlNotAllowedError
from app.security import assert_public_url


def _resolve_to(monkeypatch: pytest.MonkeyPatch, ip: str) -> None:
    monkeypatch.setattr(
        socket, "getaddrinfo", lambda *a, **k: [(socket.AF_INET, 0, 0, "", (ip, 0))]
    )


@pytest.mark.parametrize(
    "ip", ["127.0.0.1", "10.0.0.5", "192.168.1.10", "169.254.169.254", "172.16.0.1"]
)
def test_rejects_non_public_ips(monkeypatch: pytest.MonkeyPatch, ip: str) -> None:
    _resolve_to(monkeypatch, ip)
    with pytest.raises(UrlNotAllowedError):
        assert_public_url("https://example.com/video")


def test_allows_public_ip(monkeypatch: pytest.MonkeyPatch) -> None:
    _resolve_to(monkeypatch, "93.184.216.34")
    assert_public_url("https://example.com/video")


def test_allow_private_flag_skips_check() -> None:
    assert_public_url("http://127.0.0.1/x", allow_private=True)


def test_unresolvable_host(monkeypatch: pytest.MonkeyPatch) -> None:
    def boom(*a: object, **k: object) -> None:
        raise socket.gaierror

    monkeypatch.setattr(socket, "getaddrinfo", boom)
    with pytest.raises(UrlNotAllowedError):
        assert_public_url("https://no-existe.invalid/")
