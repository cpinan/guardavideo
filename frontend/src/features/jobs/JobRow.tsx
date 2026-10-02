import { memo, useEffect, useRef, useState } from "react";
import { fileUrl } from "../../lib/api";
import { formatEta } from "../../lib/eta";
import { formatBytes, formatWhen } from "../../lib/format";
import { isActive, isRetryable, type Job, type Status } from "../../lib/schemas";
import { JobThumb } from "./JobThumb";
import { hostOf } from "./url";
import { useEta } from "./useEta";
import { useJobEvents } from "./useJobEvents";

const LABELS: Record<Status, string> = {
  queued: "En cola",
  running: "Descargando",
  done: "Listo",
  error: "Error",
  cancelled: "Cancelado",
};

interface Props {
  job: Job;
  onCancel: (id: string) => void;
  onRetry?: (id: string) => void;
  onDelete?: (id: string) => void;
  /** Se resalta y se lleva a la vista (p. ej. "ya lo tenías descargado"). */
  highlight?: boolean;
}

/** memo: como solo cambia la referencia del job que recibió datos, el resto no se re-renderiza. */
export const JobRow = memo(function JobRow({ job, onCancel, onRetry, onDelete, highlight }: Props) {
  const connection = useJobEvents(job);
  const eta = useEta(job);
  const pct = Math.round(job.percent);
  const name = job.title ?? job.url;
  const retrying = job.retries > 0 && job.status === "running";
  const lostLink = isActive(job) && connection === "reconnecting";

  let note: string | null = null;
  if (retrying) note = `Reconectando con el origen… intento ${job.retries}`;
  else if (lostLink) note = "Reconectando para ver el progreso…";
  else if (job.status === "queued") note = "Esperando turno";
  else if (job.status === "running" && eta !== null) note = `${formatEta(eta)} restantes`;

  const [confirming, setConfirming] = useState(false);
  const ref = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (highlight) ref.current?.scrollIntoView?.({ behavior: "smooth", block: "center" });
  }, [highlight]);

  const details = [
    hostOf(job.url),
    job.status === "done" && job.size_bytes !== null ? formatBytes(job.size_bytes) : null,
    job.created_at ? formatWhen(job.created_at) : null,
  ].filter(Boolean);

  const barState = job.status === "queued" ? "indeterminate" : retrying ? "retrying" : job.status;

  return (
    <li ref={ref} className={`job ${job.status}${highlight ? " flash" : ""}`}>
      <JobThumb job={job} name={name} />

      <div className="job-body">
        <div className="job-head">
          <h3 className="job-title" title={job.url}>
            {name}
          </h3>
          <span className={`badge ${job.status}${retrying ? " retrying" : ""}`}>
            {retrying ? "Reintentando" : LABELS[job.status]}
          </span>
        </div>
        <p className="job-host">{details.join(" · ")}</p>

        <div
          className={`bar ${barState}`}
          role="progressbar"
          aria-label={`Progreso de ${name}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={pct}
          aria-valuetext={`${pct}%${note ? `, ${note}` : ""}`}
        >
          <div className="bar-fill" style={{ transform: `scaleX(${job.percent / 100})` }} />
        </div>

        <div className="job-meta">
          <span className="pct">{pct}%</span>
          {note && (
            <span className={`note${retrying || lostLink ? " warn" : ""}`}>
              {note}
            </span>
          )}
          <span className="spacer" />
          {job.status === "done" && (
            <a className="btn primary" href={fileUrl(job)}>
              Descargar
            </a>
          )}
          {isActive(job) && (
            <button type="button" className="btn ghost" onClick={() => onCancel(job.id)}>
              Cancelar
            </button>
          )}
          {confirming && onDelete && (
            <>
              <button type="button" className="btn danger" onClick={() => onDelete(job.id)}>
                Sí, eliminar
              </button>
              <button type="button" className="btn ghost" onClick={() => setConfirming(false)}>
                Conservar
              </button>
            </>
          )}
          {isRetryable(job) && onRetry && (
            <button type="button" className="btn ghost" onClick={() => onRetry(job.id)}>
              Reintentar
            </button>
          )}
          {!isActive(job) && onDelete && !confirming && (
            <button type="button" className="btn ghost" onClick={() => setConfirming(true)}>
              Eliminar
            </button>
          )}
        </div>

        {job.error && (
          <p role="alert" className="err">
            {job.error}
          </p>
        )}
      </div>
    </li>
  );
});
