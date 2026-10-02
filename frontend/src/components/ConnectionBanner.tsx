import { useOnline } from "../lib/hooks";

/** Aviso de conexión. role=status: se anuncia sin interrumpir al lector de pantalla. */
export function ConnectionBanner() {
  const online = useOnline();
  if (online) return null;
  return (
    <div role="status" className="banner">
      <span className="dot" aria-hidden="true" />
      Sin conexión. Los datos mostrados son los últimos guardados; se actualizarán solos al volver.
    </div>
  );
}
