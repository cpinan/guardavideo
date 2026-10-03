import { useSyncExternalStore } from "react";

/** Mismo corte que el CSS móvil (`@media (max-width: 640px)` en styles.css). */
export const PHONE_QUERY = "(max-width: 640px)";

const list = (query: string): MediaQueryList | null =>
  typeof window !== "undefined" && typeof window.matchMedia === "function" ? window.matchMedia(query) : null;

/** true mientras la media query coincide; false si el entorno no tiene matchMedia (jsdom, SSR). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      const mql = list(query);
      mql?.addEventListener("change", notify);
      return () => mql?.removeEventListener("change", notify);
    },
    () => list(query)?.matches ?? false,
    () => false,
  );
}
