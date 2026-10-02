import asyncio
import threading
import time
from pathlib import Path

import pytest

from app.config import Settings
from app.domain import Job, Status
from app.exceptions import InvalidJobStateError, JobNotFoundError, UrlNotAllowedError
from app.repositories import InMemoryJobRepository
from app.services.jobs import JobService
from tests.conftest import FakeDownloader

URL = "https://example.com/v.mp4"


def make_service(
    tmp_path: Path, downloader: FakeDownloader | None = None, **cfg: object
) -> JobService:
    cfg.setdefault("allow_private_urls", True)
    settings = Settings(download_dir=tmp_path, **cfg)  # type: ignore[arg-type]
    return JobService(InMemoryJobRepository(), [downloader or FakeDownloader()], settings)


def wait_status(svc: JobService, job_id: str, wanted: set[Status], timeout: float = 5.0) -> Job:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        job = svc.get(job_id)
        if job.status in wanted:
            return job
        time.sleep(0.01)
    raise AssertionError(f"job no llegó a {wanted}")


def test_job_runs_to_done(tmp_path: Path) -> None:
    svc = make_service(tmp_path)
    try:
        job = wait_status(svc, svc.submit(URL, None).id, {Status.done})
        assert job.percent == 100 and job.file_path is not None
        assert svc.file_for(job.id).id == job.id
    finally:
        svc.shutdown()


class PartialThenFail(FakeDownloader):
    def download(self, url, dest_dir, job_id, quality, ctx):  # type: ignore[no-untyped-def]
        (dest_dir / f"{job_id}.part").write_bytes(b"x")
        return super().download(url, dest_dir, job_id, quality, ctx)


def test_failure_is_reported_and_partials_cleaned(tmp_path: Path) -> None:
    svc = make_service(tmp_path, PartialThenFail(fail="boom"))
    try:
        job_id = svc.submit(URL, None).id
        job = wait_status(svc, job_id, {Status.error})
        assert job.error == "boom"
        assert not list(tmp_path.glob(f"{job_id}.*"))
    finally:
        svc.shutdown()


def test_cancel_running_job(tmp_path: Path) -> None:
    hold = threading.Event()
    dl = FakeDownloader(hold=hold)
    svc = make_service(tmp_path, dl)
    try:
        job_id = svc.submit(URL, None).id
        assert dl.started.wait(5)
        svc.cancel(job_id)
        assert wait_status(svc, job_id, {Status.cancelled}).status == Status.cancelled
    finally:
        hold.set()
        svc.shutdown()


def test_cancel_queued_job_never_runs(tmp_path: Path) -> None:
    hold = threading.Event()
    first = FakeDownloader(hold=hold)
    svc = make_service(tmp_path, first, max_concurrent_jobs=1)
    try:
        svc.submit(URL, None)
        assert first.started.wait(5)
        queued = svc.submit(URL, None).id
        assert svc.cancel(queued).status == Status.cancelled
        hold.set()
        time.sleep(0.05)  # el worker libre debe saltarse el job cancelado
        assert svc.get(queued).status == Status.cancelled
    finally:
        hold.set()
        svc.shutdown()


def test_cancel_finished_job_is_invalid_state(tmp_path: Path) -> None:
    svc = make_service(tmp_path)
    try:
        job_id = svc.submit(URL, None).id
        wait_status(svc, job_id, {Status.done})
        with pytest.raises(InvalidJobStateError):
            svc.cancel(job_id)
    finally:
        svc.shutdown()


def test_file_for_unfinished_job_is_invalid_state(tmp_path: Path) -> None:
    hold = threading.Event()
    dl = FakeDownloader(hold=hold)
    svc = make_service(tmp_path, dl)
    try:
        job_id = svc.submit(URL, None).id
        assert dl.started.wait(5)
        with pytest.raises(InvalidJobStateError):
            svc.file_for(job_id)
    finally:
        hold.set()
        svc.shutdown()


def test_unknown_job_raises_not_found(tmp_path: Path) -> None:
    svc = make_service(tmp_path)
    try:
        with pytest.raises(JobNotFoundError):
            svc.get("nope")
    finally:
        svc.shutdown()


def test_private_url_rejected_when_not_allowed(tmp_path: Path) -> None:
    svc = make_service(tmp_path, allow_private_urls=False)
    try:
        with pytest.raises(UrlNotAllowedError):
            svc.submit("http://127.0.0.1/v.m3u8", None)
    finally:
        svc.shutdown()


def test_watch_emits_until_terminal_and_heartbeats(tmp_path: Path) -> None:
    hold = threading.Event()
    dl = FakeDownloader(hold=hold)
    svc = make_service(tmp_path, dl)

    async def collect(job_id: str) -> list[Job | None]:
        seen: list[Job | None] = []
        async for item in svc.watch(job_id, poll=0.01, heartbeat=0.03):
            seen.append(item)
            if sum(i is None for i in seen) == 1:
                hold.set()  # tras el primer latido, deja terminar la descarga
        return seen

    try:
        job_id = svc.submit(URL, None).id
        assert dl.started.wait(5)
        seen = asyncio.run(collect(job_id))
        assert None in seen
        last = seen[-1]
        assert last is not None and last.status == Status.done
    finally:
        hold.set()
        svc.shutdown()
