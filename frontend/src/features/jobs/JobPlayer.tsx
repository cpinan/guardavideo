import { useEffect, useRef, useState } from "react";
import { streamUrl, thumbnailUrl } from "../../lib/api";
import { formatClock } from "../../lib/format";
import { getPlayback, savePlayback } from "../../lib/playback";
import { loadPrefs, savePrefs } from "../../lib/prefs";
import type { Job } from "../../lib/schemas";

const SAVE_EVERY_MS = 1000;

/** hidden: el reproductor está cerrado pero el video sigue en Picture-in-Picture. */
export type PlayerMode = "inline" | "lightbox" | "hidden";

interface Props {
  job: Job;
  name: string;
  mode?: PlayerMode;
  onToggleLightbox?: () => void;
  onClose?: () => void;
  onPipChange?: (active: boolean) => void;
  /** Se llama una sola vez por sesión de reproductor, al empezar a reproducir (cuenta una "vista"). */
  onPlayStart?: () => void;
}

const pipSupported = () =>
  typeof document !== "undefined" && "pictureInPictureEnabled" in document && document.pictureInPictureEnabled;

/**
 * Reproductor. Una sola instancia de <video> sirve a los tres modos (en la tarjeta, ampliado,
 * oculto en PiP): cambiar de modo solo cambia clases CSS, así que no se corta la reproducción
 * ni se pierde el segundo actual. Guarda segundo y duración, y reanuda donde lo dejaste.
 */
export function JobPlayer({ job, name, mode = "inline", onToggleLightbox, onClose, onPipChange, onPlayStart }: Props) {
  const ref = useRef<HTMLVideoElement>(null);
  const frame = useRef<HTMLDivElement>(null);
  const closeBtn = useRef<HTMLButtonElement>(null);
  const lastSave = useRef(0);
  const counted = useRef(false);
  const [failed, setFailed] = useState(false);
  const [pip, setPip] = useState(false);
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

  // Picture-in-Picture: React no expone estos eventos, se escuchan directo en el elemento.
  const onPipChangeRef = useRef(onPipChange);
  onPipChangeRef.current = onPipChange;
  useEffect(() => {
    const v = ref.current;
    if (!v) return;
    const enter = () => {
      setPip(true);
      onPipChangeRef.current?.(true);
    };
    const leave = () => {
      setPip(false);
      onPipChangeRef.current?.(false);
    };
    v.addEventListener("enterpictureinpicture", enter);
    v.addEventListener("leavepictureinpicture", leave);
    return () => {
      v.removeEventListener("enterpictureinpicture", enter);
      v.removeEventListener("leavepictureinpicture", leave);
    };
  }, [failed]);

  async function togglePip() {
    const v = ref.current;
    if (!v) return;
    try {
      if (document.pictureInPictureElement === v) await document.exitPictureInPicture();
      else await v.requestPictureInPicture();
    } catch {
      /* el navegador lo rechazó (p. ej. video sin metadatos): no es grave */
    }
  }

  // Lightbox: Esc cierra, el fondo no hace scroll, el foco se queda dentro y vuelve al salir.
  const lightbox = mode === "lightbox";
  useEffect(() => {
    if (!lightbox) return;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeBtn.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose?.();
      if (e.key !== "Tab" || !frame.current) return;
      const focusable = frame.current.querySelectorAll<HTMLElement>("button, video[controls], [href]");
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) return;
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
      previous?.focus?.();
    };
  }, [lightbox, onClose]);

  if (failed) {
    return (
      <div role="alert" className="player-error">
        Tu navegador no puede reproducir este formato. Usa <b>Descargar</b> para verlo en tu equipo.
      </div>
    );
  }

  return (
    <div
      className={`player ${mode}`}
      hidden={mode === "hidden"}
      role={lightbox ? "dialog" : undefined}
      aria-modal={lightbox || undefined}
      aria-label={lightbox ? `Reproductor de ${name}` : undefined}
      onClick={lightbox ? (e) => e.target === e.currentTarget && onClose?.() : undefined}
    >
      <div className="player-frame" ref={frame}>
        <div className="player-bar">
          {lightbox && <span className="player-title">{name}</span>}
          <span className="spacer" />
          {pipSupported() && (
            <button type="button" className="btn ghost" aria-pressed={pip} onClick={() => void togglePip()}>
              Picture-in-Picture
            </button>
          )}
          {onToggleLightbox && (
            <button type="button" className="btn ghost" onClick={onToggleLightbox}>
              {lightbox ? "Reducir" : "Ampliar"}
            </button>
          )}
          {lightbox && onClose && (
            <button type="button" ref={closeBtn} className="btn ghost" onClick={onClose}>
              Cerrar reproductor
            </button>
          )}
        </div>
        <video
          ref={ref}
          src={streamUrl(job)}
          poster={job.thumbnail_version > 0 ? thumbnailUrl(job) : undefined}
          aria-label={lightbox ? undefined : `Reproductor de ${name}`}
          controls
          playsInline
          preload="metadata"
          onLoadedMetadata={(e) => {
            const v = e.currentTarget;
            const prefs = loadPrefs();
            v.volume = prefs.volume;
            v.muted = prefs.muted;
            v.playbackRate = prefs.rate;
            if (resumedAt > 0 && resumedAt < v.duration - 1) v.currentTime = resumedAt;
            savePlayback(job.id, { d: v.duration });
          }}
          onVolumeChange={(e) => savePrefs({ volume: e.currentTarget.volume, muted: e.currentTarget.muted })}
          onRateChange={(e) => savePrefs({ rate: e.currentTarget.playbackRate })}
          onTimeUpdate={() => Date.now() - lastSave.current >= SAVE_EVERY_MS && save()}
          onPlay={() => {
            if (counted.current) return;
            counted.current = true;
            onPlayStart?.();
          }}
          onPause={save}
          onSeeked={save}
          onEnded={() => savePlayback(job.id, { t: 0, watched: true })}
          onError={() => setFailed(true)}
        />
        {resumedAt > 0 && mode !== "hidden" && (
          <p className="player-note" role="status">
            Continúas desde {formatClock(resumedAt)}
          </p>
        )}
      </div>
    </div>
  );
}
