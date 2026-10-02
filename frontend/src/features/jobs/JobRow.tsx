import { memo, useEffect, useRef, useState } from "react";
import { fileUrl } from "../../lib/api";
import { useWillAutoDownload } from "../../lib/autodownload";
import { friendlyError } from "../../lib/errors";
import { formatEta } from "../../lib/eta";
import { formatBytes, formatClock, formatWhen } from "../../lib/format";
import { savePlayback, usePlayback } from "../../lib/playback";
import { isActive, isRetryable, type Job, type Status } from "../../lib/schemas";
import { displayTitle } from "../../lib/title";
import { JobPlayer, type PlayerMode } from "./JobPlayer";
import { JobThumb } from "./JobThumb";
import { StarButton } from "./StarButton";
import { hostOf } from "./url";
import { useEta } from "./useEta";
import { useJobEvents } from "./useJobEvents";
import { useRecordView } from "./useJobs";

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

const TrashIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true" className="icon">
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 002 2h6a2 2 0 002-2l1-12M9 7V4h6v3" />
  </svg>
);
const DownIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true" className="icon">
    <path d="M12 4v11m0 0l-4-4m4 4l4-4M5 20h14" />
  </svg>
);
const PlayIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true" className="icon fill">
    <path d="M8 5v14l11-7z" />
  </svg>
);

/** memo: como solo cambia la referencia del job que recibió datos, el resto no se re-renderiza. */
export const JobRow = memo(function JobRow({ job, onCancel, onRetry, onDelete, highlight }: Props) {
  const connection = useJobEvents(job);
  const willDownload = useWillAutoDownload(job.id);
  const playback = usePlayback(job.id);
  const eta = useEta(job);
  const recordView = useRecordView();
  const [confirming, setConfirming] = useState(false);
  const [pipActive, setPipActive] = useState(false);
  const ref = useRef<HTMLLIElement>(null);

  const done = job.status === "done";
  const playerOpen = done && (playback?.open ?? false);
  const lightbox = playerOpen && (playback?.lightbox ?? false);
  // Cerrado pero con el video en Picture-in-Picture: el <video> sigue montado, oculto.
  const mode: PlayerMode | null = playerOpen ? (lightbox ? "lightbox" : "inline") : done && pipActive ? "hidden" : null;
  const togglePlayer = () => savePlayback(job.id, { open: !playerOpen, lightbox: false });
  const closePlayer = () => savePlayback(job.id, { open: false, lightbox: false });
  const toggleLightbox = () => savePlayback(job.id, { open: true, lightbox: !lightbox });

  const pct = Math.round(job.percent);
  const name = displayTitle(job);
  const retrying = job.retries > 0 && job.status === "running";
  const lostLink = isActive(job) && connection === "reconnecting";

  let note: string | null = null;
  if (retrying) note = `Reconectando con el origen… intento ${job.retries}`;
  else if (lostLink) note = "Reconectando para ver el progreso…";
  else if (job.status === "queued") note = "Esperando turno";
  else if (job.status === "running" && eta !== null) note = `${formatEta(eta)} restantes`;
  if (isActive(job) && willDownload) note = `${note ? `${note} · ` : ""}Se descargará al terminar`;
  const total = job.duration_seconds ?? playback?.d ?? 0;
  const resumeAt = playback && !playback.watched && playback.t >= 1 ? playback.t : 0;
  if (done && playback) {
    if (playback.watched) note = "Visto";
    else if (resumeAt) note = `Visto ${formatClock(resumeAt)}${total ? ` / ${formatClock(total)}` : ""}`;
  }

  useEffect(() => {
    if (highlight) ref.current?.scrollIntoView?.({ behavior: "smooth", block: "center" });
  }, [highlight]);

  const details = [
    hostOf(job.url),
    done && job.size_bytes !== null ? formatBytes(job.size_bytes) : null,
    done && job.view_count > 0 ? `${job.view_count} ${job.view_count === 1 ? "vista" : "vistas"}` : null,
    job.created_at ? formatWhen(job.created_at) : null,
  ].filter(Boolean);

  const barState = job.status === "queued" ? "indeterminate" : retrying ? "retrying" : job.status;
  const error = job.error ? friendlyError(job.error) : null;
  const playLabel = playerOpen ? "Cerrar vista previa" : resumeAt ? `Continuar ${formatClock(resumeAt)}` : "Reproducir";

  return (
    <li ref={ref} className={`job ${job.status}${highlight ? " flash" : ""}`}>
      <JobThumb job={job} name={name} playback={playback} onPlay={togglePlayer} />

      <div className="job-body">
        <div className="job-head">
          <h3 className="job-title" title={job.url}>
            {name}
          </h3>
          {done && <StarButton job={job} />}
          <span className={`badge ${job.status}${retrying ? " retrying" : ""}`}>
            {retrying ? "Reintentando" : LABELS[job.status]}
          </span>
        </div>
        <p className="job-host">{details.join(" · ")}</p>

        {!done && (
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
        )}

        <div className="job-meta">
          {!done && <span className="pct">{pct}%</span>}
          {note && <span className={`note${retrying || lostLink ? " warn" : ""}`}>{note}</span>}
          <span className="spacer" />
          {done && (
            <button type="button" className="btn primary" aria-expanded={playerOpen} onClick={togglePlayer}>
              <PlayIcon />
              {playLabel}
            </button>
          )}
          {done && (
            <a className="btn ghost" href={fileUrl(job)}>
              <DownIcon />
              Descargar
            </a>
          )}
          {isActive(job) && (
            <button type="button" className="btn ghost" onClick={() => onCancel(job.id)}>
              Cancelar
            </button>
          )}
          {isRetryable(job) && onRetry && (
            <button type="button" className="btn primary" onClick={() => onRetry(job.id)}>
              Reintentar
            </button>
          )}
          {confirming && onDelete ? (
            <>
              <button type="button" className="btn danger" onClick={() => onDelete(job.id)}>
                Sí, eliminar
              </button>
              <button type="button" className="btn ghost" onClick={() => setConfirming(false)}>
                Conservar
              </button>
            </>
          ) : (
            !isActive(job) &&
            onDelete && (
              <button
                type="button"
                className="btn ghost icon-only"
                aria-label="Eliminar"
                title="Eliminar"
                onClick={() => setConfirming(true)}
              >
                <TrashIcon />
              </button>
            )
          )}
        </div>

        {error && (
          <div role="alert" className="err">
            <p>{error.message}</p>
            {error.details && (
              <details>
                <summary>Ver detalles</summary>
                <code>{error.details}</code>
              </details>
            )}
          </div>
        )}
      </div>

      {mode && (
        <JobPlayer
          job={job}
          name={name}
          mode={mode}
          onToggleLightbox={toggleLightbox}
          onClose={closePlayer}
          onPipChange={setPipActive}
          onPlayStart={() => recordView.mutate(job.id)}
        />
      )}
    </li>
  );
});
