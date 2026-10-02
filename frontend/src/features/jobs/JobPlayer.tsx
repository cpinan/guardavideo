import { useEffect, useRef, useState } from "react";
import { streamUrl, thumbnailUrl } from "../../lib/api";
import { formatClock } from "../../lib/format";
import { getPlayback, savePlayback } from "../../lib/playback";
import type { Job } from "../../lib/schemas";

const SAVE_EVERY_MS = 1000;

/**
 * Reproductor en línea. Guarda segundo y duración mientras se reproduce y, al volver a abrirlo
 * (o recargar la página), continúa donde lo dejaste. Terminar el video lo marca como visto.
 */
export function JobPlayer({ job, name }: { job: Job; name: string }) {
  const ref = useRef<HTMLVideoElement>(null);
  const lastSave = useRef(0);
  const [failed, setFailed] = useState(false);
  const [resumedAt] = useState(() => {
    const saved = getPlayback(job.id);
    return saved && !saved.watched && saved.t >= 1 ? saved.t : 0;
  });

  const snapshot = (v: HTMLVideoElement | null) => {
    if (!v || Number.isNaN(v.duration) || v.ended) return;
    lastSave.current = Date.now();
    savePlayback(job.id, { t: v.currentTime, d: v.duration, watched: false });
  };
  const save = () => snapshot(ref.current);

  // Al cerrar el reproductor conserva el último segundo. Se captura el elemento al montar:
  // en la limpieza React ya soltó `ref.current`.
  useEffect(() => {
    const video = ref.current;
    return () => snapshot(video);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (failed) {
    return (
      <div role="alert" className="player-error">
        Tu navegador no puede reproducir este formato. Usa <b>Descargar</b> para verlo en tu equipo.
      </div>
    );
  }

  return (
    <div className="player">
      <video
        ref={ref}
        src={streamUrl(job)}
        poster={job.thumbnail_version > 0 ? thumbnailUrl(job) : undefined}
        aria-label={`Reproductor de ${name}`}
        controls
        playsInline
        preload="metadata"
        onLoadedMetadata={(e) => {
          const v = e.currentTarget;
          if (resumedAt > 0 && resumedAt < v.duration - 1) v.currentTime = resumedAt;
          savePlayback(job.id, { d: v.duration });
        }}
        onTimeUpdate={() => Date.now() - lastSave.current >= SAVE_EVERY_MS && save()}
        onPause={save}
        onSeeked={save}
        onEnded={() => savePlayback(job.id, { t: 0, watched: true })}
        onError={() => setFailed(true)}
      />
      {resumedAt > 0 && (
        <p className="player-note" role="status">
          Continúas desde {formatClock(resumedAt)}
        </p>
      )}
    </div>
  );
}
