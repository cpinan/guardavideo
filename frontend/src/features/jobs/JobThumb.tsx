import { useState } from "react";
import { thumbnailUrl } from "../../lib/api";
import { isActive, type Job } from "../../lib/schemas";

const R = 18;
const CIRCUMFERENCE = 2 * Math.PI * R;

/**
 * Miniatura del video. Mientras descarga se ve atenuada con un anillo de progreso encima;
 * al terminar recupera el color y aparece la marca de "listo".
 */
export function JobThumb({ job, name }: { job: Job; name: string }) {
  const src = job.thumbnail_version > 0 ? thumbnailUrl(job) : null;
  const [loadedSrc, setLoadedSrc] = useState<string | null>(null);
  const active = isActive(job);
  const pct = Math.round(job.percent);

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
