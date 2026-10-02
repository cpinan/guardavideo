"""Excepciones de dominio. La capa HTTP las traduce a códigos de estado (ver main.py)."""


class JobNotFoundError(Exception):
    pass


class InvalidJobStateError(Exception):
    pass


class UrlNotAllowedError(Exception):
    pass


class DownloadCancelledError(Exception):
    pass
