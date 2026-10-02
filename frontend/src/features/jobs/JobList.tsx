import { useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { groupByDay } from "../../lib/group";
import { JOBS_KEY } from "../../lib/keys";
import { isActive, type Job } from "../../lib/schemas";
import { displayTitle } from "../../lib/title";
import { JobRow } from "./JobRow";
import { useCancelJob, useDeleteJob, useDeleteMany, useJobsQuery, useRetryJob } from "./useJobs";

type Filter = "all" | "active" | "done" | "failed";
const FILTERS: { id: Filter; label: string; test: (j: Job) => boolean }[] = [
  { id: "all", label: "Todas", test: () => true },
  { id: "active", label: "En curso", test: isActive },
  { id: "done", label: "Listas", test: (j) => j.status === "done" },
  { id: "failed", label: "Con error", test: (j) => j.status === "error" || j.status === "cancelled" },
];

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

function Empty({ onExample }: { onExample?: () => void }) {
  return (
    <div className="empty">
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <rect x="6" y="14" width="52" height="36" rx="6" />
        <path d="M27 24v16l14-8z" />
        <path d="M32 54v6M24 60h16" />
      </svg>
      <p className="muted">Aún no hay descargas.</p>
      <p className="muted small">Pega un enlace arriba para empezar.</p>
      {onExample && (
        <button type="button" className="btn ghost" onClick={onExample}>
          Probar con un enlace de ejemplo
        </button>
      )}
    </div>
  );
}

interface Props {
  highlightId?: string | null;
  onExample?: () => void;
}

export function JobList({ highlightId, onExample }: Props) {
  const qc = useQueryClient();
  const { data, isLoading, isError, isFetching, dataUpdatedAt, refetch } = useJobsQuery();
  const cancel = useCancelJob();
  const retry = useRetryJob();
  const remove = useDeleteJob();
  const removeMany = useDeleteMany();
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");
  const [confirmClear, setConfirmClear] = useState(false);

  const jobs = useMemo(() => (data ?? []).slice().reverse(), [data]); // más nuevo primero
  const failed = useMemo(() => jobs.filter(FILTERS[3]!.test), [jobs]);
  const visible = useMemo(() => {
    const test = FILTERS.find((f) => f.id === filter)!.test;
    const q = query.trim().toLowerCase();
    return jobs.filter((j) => test(j) && (!q || `${displayTitle(j)} ${j.url}`.toLowerCase().includes(q)));
  }, [jobs, filter, query]);
  const groups = useMemo(() => groupByDay(visible), [visible]);

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
  if (!data?.length) return <Empty onExample={onExample} />;

  const active = jobs.filter(isActive).length;
  const done = jobs.filter((j) => j.status === "done").length;
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

      <div className="toolbar">
        <div className="filters" role="group" aria-label="Filtrar descargas">
          {FILTERS.map((f) => {
            const count = jobs.filter(f.test).length;
            if (f.id !== "all" && count === 0 && filter !== f.id) return null;
            return (
              <button
                key={f.id}
                type="button"
                className={`filter${filter === f.id ? " on" : ""}`}
                aria-pressed={filter === f.id}
                onClick={() => setFilter(f.id)}
              >
                {f.label} <span className="count">{count}</span>
              </button>
            );
          })}
        </div>
        <label className="search">
          <span className="sr-only">Buscar descargas</span>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar…"
            autoComplete="off"
          />
        </label>
      </div>

      {failed.length > 0 && (
        <div className="clear-row">
          {confirmClear ? (
            <>
              <span className="small">¿Eliminar {failed.length} {failed.length === 1 ? "registro" : "registros"} con error?</span>
              <button
                type="button"
                className="btn danger small"
                onClick={() => {
                  removeMany.mutate(failed.map((j) => j.id));
                  setConfirmClear(false);
                }}
              >
                Sí, eliminar
              </button>
              <button type="button" className="btn ghost small" onClick={() => setConfirmClear(false)}>
                Conservar
              </button>
            </>
          ) : (
            <button type="button" className="btn ghost small" onClick={() => setConfirmClear(true)}>
              Limpiar con error ({failed.length})
            </button>
          )}
        </div>
      )}

      {visible.length === 0 && <p className="muted empty-filter">Nada coincide con el filtro o la búsqueda.</p>}

      {groups.map((group) => (
        <div key={group.label} className="day">
          <h3 className="day-title">{group.label}</h3>
          <ul className="jobs" aria-label={`Descargas · ${group.label}`}>
            {group.jobs.map((job) => (
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
        </div>
      ))}
    </section>
  );
}
