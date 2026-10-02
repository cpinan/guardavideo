"""Capa HTTP delgada: valida, delega en el servicio y da formato a la respuesta."""

import re
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends, Request, Response
from fastapi.responses import FileResponse, StreamingResponse

from app.domain import Job
from app.schemas import JobCreate, JobRead
from app.services.jobs import JobService

router = APIRouter(prefix="/api/v1/jobs", tags=["jobs"])


def get_job_service(request: Request) -> JobService:
    service: JobService = request.app.state.job_service
    return service


Service = Annotated[JobService, Depends(get_job_service)]


@router.post("", response_model=JobRead, status_code=201)
def create_job(body: JobCreate, service: Service, response: Response) -> Job:
    job, reused = service.request_download(str(body.url), body.quality, force=body.force)
    response.headers["Location"] = f"/api/v1/jobs/{job.id}"
    if reused:
        response.status_code = 200  # ya existía: no se creó nada nuevo
    return job


@router.get("", response_model=list[JobRead])
def list_jobs(service: Service) -> list[Job]:
    return service.list()


@router.get("/{job_id}", response_model=JobRead)
def get_job(job_id: str, service: Service) -> Job:
    return service.get(job_id)


@router.delete("/{job_id}", response_model=JobRead, status_code=202)
def cancel_job(job_id: str, service: Service) -> Job:
    return service.cancel(job_id)


@router.delete("/{job_id}/record", status_code=204)
def delete_job(job_id: str, service: Service) -> Response:
    service.delete(job_id)
    return Response(status_code=204)


@router.post("/{job_id}/retry", response_model=JobRead, status_code=202)
def retry_job(job_id: str, service: Service) -> Job:
    return service.retry(job_id)


@router.get("/{job_id}/events")
async def job_events(job_id: str, service: Service) -> StreamingResponse:
    service.get(job_id)  # 404 ANTES de abrir el stream (después ya no se puede cambiar el status)

    async def stream() -> AsyncIterator[str]:
        async for job in service.watch(job_id):
            if job is None:
                # comentario SSE: el cliente lo ignora, pero mantiene vivo el proxy
                yield ": keep-alive\n\n"
            else:
                yield f"data: {JobRead.model_validate(job).model_dump_json()}\n\n"

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/{job_id}/thumbnail")
def get_thumbnail(job_id: str, service: Service) -> FileResponse:
    # no-cache: el navegador revalida con ETag (barato) y la URL lleva ?v= para forzar el cambio
    return FileResponse(
        service.thumbnail_for(job_id),
        media_type="image/jpeg",
        headers={"Cache-Control": "no-cache"},
    )


@router.get("/{job_id}/file")
def get_file(job_id: str, service: Service) -> FileResponse:
    job = service.file_for(job_id)
    assert job.file_path is not None
    safe = re.sub(r"[^\w\- ]+", "", job.title or job.id).strip() or job.id
    return FileResponse(job.file_path, filename=f"{safe}{job.file_path.suffix}")
