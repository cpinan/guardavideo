/**
 * Traduce los errores técnicos (yt-dlp, ffmpeg, red) a mensajes que una persona entiende.
 * Si no reconoce el error, lo muestra tal cual: nunca inventa una causa.
 */
const RULES: { test: RegExp; message: string }[] = [
  { test: /private video|video is private/i, message: "Este video es privado." },
  {
    test: /sign in to confirm|confirm you.?re not a bot|login required|cookies/i,
    message: "El sitio pide iniciar sesión o verificar que no eres un robot, así que no se puede descargar desde aquí.",
  },
  {
    test: /available in your country|geo.?restrict|blocked it in your country|unavailable in your/i,
    message: "El video no está disponible en tu país.",
  },
  {
    test: /video unavailable|has been removed|no longer available|deleted|not found \(404\)|http error 404|404 client error/i,
    message: "El video ya no existe o fue eliminado.",
  },
  { test: /unsupported url|no video formats|unable to extract|no suitable/i, message: "No se encontró un video descargable en ese enlace." },
  { test: /http error 403|403 client error|forbidden/i, message: "El sitio rechazó la descarga (acceso denegado)." },
  { test: /http error 429|too many requests|429/i, message: "El sitio está limitando las peticiones. Espera unos minutos y reintenta." },
  { test: /drm|protected|widevine|fairplay/i, message: "El video tiene protección DRM y no se puede descargar." },
  {
    test: /timed out|timeout|connection (reset|refused|aborted)|name or service not known|network is unreachable|sin conexi[oó]n|failed to resolve|temporary failure/i,
    message: "Se perdió la conexión con el sitio. Reintenta cuando la red esté estable.",
  },
  { test: /ffmpeg/i, message: "Falló el procesamiento del video (ffmpeg)." },
  { test: /interrumpido: el servidor se reinici/i, message: "La descarga se interrumpió porque el servidor se reinició." },
  { test: /el archivo ya no existe/i, message: "El archivo ya no está en el disco. Puedes procesarlo de nuevo." },
];

export interface FriendlyError {
  message: string;
  /** Texto original, solo si lo reescribimos (para "Ver detalles"). */
  details: string | null;
}

export function friendlyError(raw: string): FriendlyError {
  const rule = RULES.find((r) => r.test.test(raw));
  return rule ? { message: rule.message, details: raw } : { message: raw, details: null };
}
