import { useQueryClient } from "@tanstack/react-query";
import { JOBS_KEY } from "../../lib/keys";
import { isActive } from "../../lib/schemas";
import { JobRow } from "./JobRow";
import { useCancelJob, useDeleteJob, useJobsQuery, useRetryJob } from "./useJobs";

function Skeleton() {
  return (
    <ul className="jobs" aria-hidden="true">
      {[0, 1].map((i) => (
        <li key={i} className="job skeleton">
          <div className="thumb" />
          <div className="job-body">
            <div className="sk-line w60" />
            <div className="sk-line w30" />
            <div className="sk-line bar" />
          </div>
        </li>
      ))}
    </ul>
  );
}

function Empty() {
  return (
    <div className="empty">
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <rect x="6" y="14" width="52" height="36" rx="6" />
        <path d="M27 24v16l14-8z" />
        <path d="M32 54v6M24 60h16" />
      </svg>
      <p className="muted">Aún no hay descargas.</p>
      <p className="muted small">Pega un enlace arriba para empezar.</p>
    </div>
  );
}

export function JobList({ highlightId }: { highlightId?: string | null }) {
  const qc = useQueryClient();
  const { data, isLoading, isError, isFetching, dataUpdatedAt, refetch } = useJobsQuery();
  const cancel = useCancelJob();
  const retry = useRetryJob();
  const remove = useDeleteJob();

  if (isLoading) {
    return (
      <>
        <p role="status" className="sr-only">
          Cargando…
        </p>
        <Skeleton />
      </>
    );
  }
  if (isError && !data) {
    return (
      <div role="alert" className="notice bad">
        <span>No se pudo conectar con la API</span>
        <button type="button" className="btn ghost" onClick={() => void refetch()}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!data?.length) return <Empty />;

  const active = data.filter(isActive).length;
  const done = data.filter((j) => j.status === "done").length;
  // updatedAt 0 = lo que se ve viene de la caché local y aún no se confirmó con el servidor
  const fromCache = dataUpdatedAt === 0 && isFetching;

  return (
    <section aria-labelledby="jobs-title">
      <div className="list-head">
        <h2 id="jobs-title">Descargas</h2>
        <span className="muted small">
          {active > 0 && `${active} en curso · `}
          {done} completadas
        </span>
        {fromCache && (
          <span className="chip sync" role="status">
            <span className="spinner" aria-hidden="true" /> Sincronizando…
          </span>
        )}
      </div>
      {isError && (
        <div role="alert" className="notice warn">
          <span>No se pudo actualizar. Mostrando datos guardados; reintentando solo…</span>
          <button
            type="button"
            className="btn ghost"
            onClick={() => void qc.refetchQueries({ queryKey: JOBS_KEY })}
          >
            Reintentar ahora
          </button>
        </div>
      )}
      <ul className="jobs" aria-label="Descargas">
        {data
          .slice()
          .reverse()
          .map((job) => (
            <JobRow
              key={job.id}
              job={job}
              onCancel={cancel.mutate}
              onRetry={retry.mutate}
              onDelete={remove.mutate}
              highlight={job.id === highlightId}
            />
          ))}
      </ul>
    </section>
  );
}
