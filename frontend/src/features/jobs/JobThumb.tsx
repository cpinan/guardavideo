import { useState } from "react";
import { thumbnailUrl } from "../../lib/api";
import { formatClock } from "../../lib/format";
import type { Playback } from "../../lib/playback";
import { isActive, type Job } from "../../lib/schemas";

const R = 18;
const CIRCUMFERENCE = 2 * Math.PI * R;

/**
 * Miniatura del video. Mientras descarga se ve atenuada con un anillo de progreso encima;
 * al terminar recupera el color y aparece la marca de "listo".
 */
interface Props {
  job: Job;
  name: string;
  playback?: Playback;
  /** Atajo con el mouse; el botón accesible "Reproducir" está en las acciones de la tarjeta. */
  onPlay?: () => void;
}

export function JobThumb({ job, name, playback, onPlay }: Props) {
  const src = job.thumbnail_version > 0 ? thumbnailUrl(job) : null;
  const [loadedSrc, setLoadedSrc] = useState<string | null>(null);
  const active = isActive(job);
  const pct = Math.round(job.percent);
  const total = job.duration_seconds ?? playback?.d ?? 0;
  const watchedPct = playback && !playback.watched && total > 0 ? Math.min(100, (playback.t / total) * 100) : 0;

  return (
    <div className={`thumb ${job.status}`}>
      {src ? (
        <img
          key={src} // una miniatura nueva remonta la imagen y vuelve a hacer el fundido
          src={src}
          alt={`Vista previa de ${name}`}
          className={loadedSrc === src ? "loaded" : ""}
          onLoad={() => setLoadedSrc(src)}
          loading="lazy"
          decoding="async"
        />
      ) : (
        <svg className="thumb-empty" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M8 5v14l11-7z" />
        </svg>
      )}

      {active && (
        <div className="ring" aria-hidden="true">
          <svg viewBox="0 0 44 44">
            <circle className="ring-bg" cx="22" cy="22" r={R} />
            <circle
              className="ring-fg"
              cx="22"
              cy="22"
              r={R}
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={CIRCUMFERENCE * (1 - job.percent / 100)}
            />
          </svg>
          <span>{pct}</span>
        </div>
      )}
      {job.status === "done" && onPlay && (
        <button type="button" className="thumb-play" onClick={onPlay} tabIndex={-1} aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M8 5v14l11-7z" />
          </svg>
        </button>
      )}
      {job.status === "done" && job.duration_seconds !== null && (
        <span className="duration">{formatClock(job.duration_seconds)}</span>
      )}
      {job.status === "done" && watchedPct > 0 && (
        <div className="watch" aria-hidden="true">
          <span style={{ width: `${watchedPct}%` }} />
        </div>
      )}
      {job.status === "done" && (
        <svg className="mark ok" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="11" />
          <path d="M7 12.5l3.2 3.2L17 9" />
        </svg>
      )}
      {job.status === "error" && (
        <svg className="mark bad" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="11" />
          <path d="M12 7v6M12 16.5v.5" />
        </svg>
      )}
    </div>
  );
}
