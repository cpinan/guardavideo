import { useEffect, useId, useState } from "react";
import { storage } from "../../lib/storage";
import { showToast } from "../../lib/toast";
import { detectSource, isValidUrl } from "./url";

const DRAFT_KEY = "hlsweb:draft:v1";

/** download = procesar y bajar el archivo al terminar; process = solo procesar (queda en la lista). */
export type SubmitMode = "download" | "process";

interface Props {
  /** Si devuelve una promesa, el formulario conserva lo escrito hasta que se resuelva. */
  onSubmit: (url: string, quality: number | undefined, mode: SubmitMode) => Promise<unknown> | void;
  pending: boolean;
  error?: string;
  /** Rellena el campo desde fuera (p. ej. "usar enlace de ejemplo"). `nonce` distingue repeticiones. */
  injectUrl?: { url: string; nonce: number };
}

function loadDraft(): { url: string; quality: string } {
  try {
    const d = JSON.parse(storage.get(DRAFT_KEY) ?? "{}");
    return { url: typeof d.url === "string" ? d.url : "", quality: typeof d.quality === "string" ? d.quality : "" };
  } catch {
    return { url: "", quality: "" };
  }
}

export function JobForm({ onSubmit, pending, error, injectUrl }: Props) {
  const urlId = useId();
  const qualityId = useId();
  const hintId = useId();
  const [draft] = useState(loadDraft);
  const [url, setUrl] = useState(draft.url);
  const [quality, setQuality] = useState(draft.quality);
  const [touched, setTouched] = useState(false);
  const [sending, setSending] = useState<SubmitMode | null>(null);

  useEffect(() => {
    if (injectUrl) setUrl(injectUrl.url);
  }, [injectUrl]);

  async function paste() {
    try {
      const text = (await navigator.clipboard.readText()).trim();
      if (isValidUrl(text)) {
        setUrl(text);
        setTouched(true);
      } else {
        showToast("El portapapeles no tiene un enlace http(s) válido.");
      }
    } catch {
      showToast("No se pudo leer el portapapeles. Pega con Ctrl/Cmd+V.");
    }
  }

  // Borrador: si se recarga la página (o falla el envío) no se pierde lo escrito.
  useEffect(() => {
    if (url || quality) storage.set(DRAFT_KEY, JSON.stringify({ url, quality }));
    else storage.remove(DRAFT_KEY);
  }, [url, quality]);

  const valid = isValidUrl(url);
  const showInvalid = touched && url !== "" && !valid;
  const source = detectSource(url);

  async function submit(mode: SubmitMode) {
    if (!valid || pending) return;
    setSending(mode);
    try {
      await onSubmit(url, quality ? Number(quality) : undefined, mode);
    } catch {
      return; // el error se muestra vía `error`; se conserva el borrador para reintentar
    } finally {
      setSending(null);
    }
    setUrl("");
    setTouched(false);
  }

  return (
    <form
      className="card form"
      onSubmit={(e) => {
        e.preventDefault(); // Enter = acción principal: procesar y descargar
        void submit("download");
      }}
    >
      <div className="field grow">
        <label htmlFor={urlId}>URL del video</label>
        <div className="input-row">
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
        {typeof navigator !== "undefined" && typeof navigator.clipboard?.readText === "function" && (
          <button type="button" className="btn ghost" onClick={() => void paste()}>
            Pegar
          </button>
        )}
        </div>
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

      <div className="actions">
        <button type="submit" className="primary" disabled={!valid || pending}>
          {sending === "download" && <span className="spinner" aria-hidden="true" />}
          {sending === "download" ? "Enviando…" : "Procesar y descargar"}
        </button>
        <button type="button" className="ghost" disabled={!valid || pending} onClick={() => void submit("process")}>
          {sending === "process" && <span className="spinner" aria-hidden="true" />}
          {sending === "process" ? "Enviando…" : "Solo procesar"}
        </button>
      </div>
      {error && (
        <p role="alert" className="err full">
          {error}
        </p>
      )}
    </form>
  );
}
