import { useId } from "react";
import { CATEGORIES, PROVIDERS, type Provider } from "../../lib/providers";

function Group({ title, items }: { title: string; items: Provider[] }) {
  const id = useId();
  if (items.length === 0) return null;
  return (
    <section className="providers-group">
      <h3 id={id}>{title}</h3>
      <ul aria-labelledby={id}>
        {items.map((p) => (
          <li key={p.name}>{p.name}</li>
        ))}
      </ul>
    </section>
  );
}

/** Lista orientativa de sitios (plegada para no robar espacio al formulario). */
export function ProvidersList() {
  const supported = PROVIDERS.filter((p) => p.access === "public");
  return (
    <details className="card providers">
      <summary>Sitios compatibles ({supported.length})</summary>
      <p className="muted small">
        Además de cualquier enlace <code>.m3u8</code> directo. Las descargas usan yt-dlp, que reconoce más de 1.700
        sitios; esta es la lista revisada. Si un sitio deja de funcionar, suele bastar con actualizar yt-dlp.
      </p>
      {CATEGORIES.map((c) => (
        <Group key={c.id} title={c.label} items={supported.filter((p) => p.category === c.id)} />
      ))}
      <Group title="Requieren cuenta (aún no soportado)" items={PROVIDERS.filter((p) => p.access === "login")} />
      <Group title="No compatibles (DRM)" items={PROVIDERS.filter((p) => p.access === "drm")} />
    </details>
  );
}
