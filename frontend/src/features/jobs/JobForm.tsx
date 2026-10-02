import { useEffect, useId, useState } from "react";
import { storage } from "../../lib/storage";
import { detectSource, isValidUrl } from "./url";

const DRAFT_KEY = "hlsweb:draft:v1";

interface Props {
  /** Si devuelve una promesa, el formulario conserva lo escrito hasta que se resuelva. */
  onSubmit: (url: string, quality?: number) => Promise<unknown> | void;
  pending: boolean;
  error?: string;
}

function loadDraft(): { url: string; quality: string } {
  try {
    const d = JSON.parse(storage.get(DRAFT_KEY) ?? "{}");
    return { url: typeof d.url === "string" ? d.url : "", quality: typeof d.quality === "string" ? d.quality : "" };
  } catch {
    return { url: "", quality: "" };
  }
}

export function JobForm({ onSubmit, pending, error }: Props) {
  const urlId = useId();
  const qualityId = useId();
  const hintId = useId();
  const [draft] = useState(loadDraft);
  const [url, setUrl] = useState(draft.url);
  const [quality, setQuality] = useState(draft.quality);
  const [touched, setTouched] = useState(false);

  // Borrador: si se recarga la página (o falla el envío) no se pierde lo escrito.
  useEffect(() => {
    if (url || quality) storage.set(DRAFT_KEY, JSON.stringify({ url, quality }));
    else storage.remove(DRAFT_KEY);
  }, [url, quality]);

  const valid = isValidUrl(url);
  const showInvalid = touched && url !== "" && !valid;
  const source = detectSource(url);

  return (
    <form
      className="card form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (!valid || pending) return;
        try {
          await onSubmit(url, quality ? Number(quality) : undefined);
        } catch {
          return; // el error se muestra vía `error`; se conserva el borrador para reintentar
        }
        setUrl("");
        setTouched(false);
      }}
    >
      <div className="field grow">
        <label htmlFor={urlId}>URL del video</label>
        <input
          id={urlId}
          type="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          onBlur={() => setTouched(true)}
          placeholder="YouTube, Vimeo, Dailymotion o .m3u8"
          aria-invalid={showInvalid}
          aria-describedby={showInvalid ? hintId : undefined}
          autoComplete="off"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          inputMode="url"
          enterKeyHint="go"
        />
        {showInvalid && (
          <p id={hintId} role="alert" className="err">
            Ingresa una URL http(s) válida
          </p>
        )}
        {source && (
          <p className={`chip ${source.kind}`} aria-live="polite" key={source.label}>
            {source.label}
          </p>
        )}
      </div>

      <div className="field">
        <label htmlFor={qualityId}>Calidad</label>
        <select id={qualityId} value={quality} onChange={(e) => setQuality(e.target.value)}>
          <option value="">Mejor</option>
          <option value="1080">1080p</option>
          <option value="720">720p</option>
          <option value="480">480p</option>
        </select>
      </div>

      <button type="submit" className="primary" disabled={!valid || pending}>
        {pending && <span className="spinner" aria-hidden="true" />}
        {pending ? "Enviando…" : "Descargar"}
      </button>
      {error && (
        <p role="alert" className="err full">
          {error}
        </p>
      )}
    </form>
  );
}
