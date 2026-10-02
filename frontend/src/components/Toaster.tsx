import { dismissToast, useToasts } from "../lib/toast";

/** Avisos efímeros. aria-live=polite: se anuncian sin interrumpir. */
export function Toaster() {
  const toasts = useToasts();
  return (
    <div className="toaster" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="toast">
          <span>{t.message}</span>
          {t.action?.href && (
            <a className="btn ghost small" href={t.action.href} onClick={() => dismissToast(t.id)}>
              {t.action.label}
            </a>
          )}
          {t.action?.onClick && (
            <button
              type="button"
              className="btn ghost small"
              onClick={() => {
                t.action?.onClick?.();
                dismissToast(t.id);
              }}
            >
              {t.action.label}
            </button>
          )}
          <button type="button" className="toast-x" aria-label="Cerrar aviso" onClick={() => dismissToast(t.id)}>
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
