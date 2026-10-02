import threading
from collections.abc import Iterator
from pathlib import Path

import pytest

from app.domain import Job, Status
from app.exceptions import JobNotFoundError
from app.repositories import InMemoryJobRepository, JobRepository, SqliteJobRepository


@pytest.fixture(params=["memory", "sqlite"])
def repo(request: pytest.FixtureRequest, tmp_path: Path) -> Iterator[JobRepository]:
    """Mismo contrato para ambos adaptadores: lo que cambia es dónde se guarda."""
    if request.param == "memory":
        yield InMemoryJobRepository()
    else:
        sqlite = SqliteJobRepository(tmp_path / "t.db")
        yield sqlite
        sqlite.close()


def make_job(job_id: str = "a") -> Job:
    return Job(id=job_id, url="https://example.com/v.mp4")


def test_get_returns_copy_not_shared_object(repo: JobRepository) -> None:
    repo.add(make_job())
    copy = repo.get("a")
    copy.percent = 99
    assert repo.get("a").percent == 0


def test_update_applies_changes_and_returns_copy(repo: JobRepository) -> None:
    repo.add(make_job())
    updated = repo.update("a", status=Status.running, percent=10)
    assert updated.status == Status.running
    assert repo.get("a").percent == 10
    updated.percent = 50
    assert repo.get("a").percent == 10


def test_list_returns_all_jobs(repo: JobRepository) -> None:
    repo.add(make_job("a"))
    repo.add(make_job("b"))
    assert {j.id for j in repo.list()} == {"a", "b"}


def test_missing_job_raises_not_found(repo: JobRepository) -> None:
    with pytest.raises(JobNotFoundError):
        repo.get("nope")
    with pytest.raises(JobNotFoundError):
        repo.update("nope", percent=1)


def test_concurrent_updates_do_not_lose_jobs(repo: JobRepository) -> None:
    for i in range(20):
        repo.add(make_job(str(i)))

    def bump(job_id: str) -> None:
        for p in range(50):
            repo.update(job_id, percent=p)

    threads = [threading.Thread(target=bump, args=(str(i),)) for i in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert all(j.percent == 49 for j in repo.list())


def test_list_keeps_insertion_order_after_updates(repo: JobRepository) -> None:
    for i in "abc":
        repo.add(make_job(i))
    repo.update("a", percent=50)  # actualizar no debe mover el job al final
    assert [j.id for j in repo.list()] == ["a", "b", "c"]


def test_delete_removes_and_missing_raises(repo: JobRepository) -> None:
    repo.add(make_job())
    repo.delete("a")
    with pytest.raises(JobNotFoundError):
        repo.get("a")
    with pytest.raises(JobNotFoundError):
        repo.delete("a")


def test_unknown_field_is_rejected(repo: JobRepository) -> None:
    repo.add(make_job())
    with pytest.raises(ValueError, match="desconocidos"):
        repo.update("a", nope=1)


def test_all_fields_roundtrip(repo: JobRepository, tmp_path: Path) -> None:
    job = Job(
        id="x",
        url="https://a.test/v.mp4",
        quality=720,
        status=Status.done,
        percent=100,
        title="Título ñ",
        error=None,
        retries=2,
        file_path=tmp_path / "x.mp4",
        size_bytes=123,
        thumbnail_path=tmp_path / "x_thumb.jpg",
        thumbnail_rev=3,
        url_hash="u" * 64,
        content_hash="c" * 64,
    )
    repo.add(job)
    assert repo.get("x") == job
