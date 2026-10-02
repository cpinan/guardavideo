"""Protección SSRF: el servidor no debe poder pedir recursos de redes internas."""

import ipaddress
import socket
from urllib.parse import urlparse

from app.exceptions import UrlNotAllowedError


def assert_public_url(url: str, *, allow_private: bool = False) -> None:
    """Resuelve el host y rechaza cualquier IP no pública (loopback, privada, link-local...).

    Limitaciones conocidas (mencionar en entrevista): no cubre DNS rebinding ni redirecciones
    posteriores. En producción: allowlist de dominios + egress filtrado en red.
    """
    if allow_private:
        return
    host = urlparse(url).hostname
    if not host:
        raise UrlNotAllowedError("URL sin host")
    try:
        infos = socket.getaddrinfo(host, None)
    except socket.gaierror as e:
        raise UrlNotAllowedError("No se pudo resolver el host") from e
    for info in infos:
        ip = ipaddress.ip_address(str(info[4][0]).split("%")[0])
        if not ip.is_global:
            raise UrlNotAllowedError("La URL apunta a una red no permitida")
