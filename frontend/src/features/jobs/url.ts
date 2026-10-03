import { findProvider } from "../../lib/providers";

export function isValidUrl(value: string): boolean {
  try {
    const { protocol } = new URL(value);
    return protocol === "http:" || protocol === "https:";
  } catch {
    return false;
  }
}

export type Source = { kind: "hls" | "site" | "debug" | "login" | "drm"; label: string };

/** Qué descargador se usará (igual que el backend): ayuda a entender qué va a pasar. */
export function detectSource(value: string): Source | null {
  if (!isValidUrl(value)) return null;
  const { hostname, pathname } = new URL(value);
  if (hostname === "debug.test") return { kind: "debug", label: "Escenario de depuración" };
  if (pathname.endsWith(".m3u8")) return { kind: "hls", label: "Stream HLS directo" };
  const provider = findProvider(hostname);
  if (provider?.access === "drm") return { kind: "drm", label: `${provider.name} · protegido con DRM, no se puede descargar` };
  if (provider?.access === "login")
    return { kind: "login", label: `${provider.name} · requiere iniciar sesión (aún no soportado)` };
  if (provider) return { kind: "site", label: `${provider.name} · compatible` };
  return { kind: "site", label: `Sitio web · ${hostname.replace(/^www\./, "")}` };
}

export function hostOf(value: string): string {
  try {
    return new URL(value).hostname.replace(/^www\./, "");
  } catch {
    return value;
  }
}
