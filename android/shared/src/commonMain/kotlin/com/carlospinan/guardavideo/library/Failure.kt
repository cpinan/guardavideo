package com.carlospinan.guardavideo.library

/**
 * Why a download failed, in words a person understands (ported from the web's lib/errors.ts).
 * Unknown errors are shown as they are: never invent a cause.
 */
enum class FailureKind(val title: String, val explanation: String, val hint: String, val retryable: Boolean) {
    Private(
        "Video privado",
        "Su dueño lo marcó como privado: solo él puede verlo.",
        "Si tienes acceso, pide un enlace público.",
        retryable = false,
    ),
    LoginRequired(
        "El sitio pide iniciar sesión",
        "Este video solo se ve con una cuenta o tras demostrar que no eres un robot.",
        "Por ahora Guardavideo no usa tu cuenta del sitio.",
        retryable = false,
    ),
    GeoBlocked(
        "No disponible en tu país",
        "El sitio bloquea este video según desde dónde te conectas.",
        "No se puede descargar desde aquí.",
        retryable = false,
    ),
    Removed(
        "El video ya no existe",
        "Fue eliminado o el enlace cambió.",
        "Ábrelo en el navegador para confirmarlo.",
        retryable = false,
    ),
    Forbidden(
        "El sitio bloqueó la descarga",
        "Respondió «acceso denegado». Suele pasar cuando el video fue retirado, es privado o tiene restricción de región.",
        "Ábrelo en el navegador: si tampoco se ve allí, ya no está disponible. Si se ve, reintenta más tarde.",
        retryable = true,
    ),
    RateLimited(
        "Demasiadas peticiones",
        "El sitio está limitando las descargas desde tu conexión.",
        "Espera unos minutos y reintenta.",
        retryable = true,
    ),
    NoVideo(
        "No hay video en ese enlace",
        "La página no tiene un video que se pueda descargar.",
        "Revisa que el enlace sea el del video, no el de un canal o una lista.",
        retryable = false,
    ),
    Drm(
        "Protegido con DRM",
        "El video está cifrado para que no se pueda guardar.",
        "Guardavideo no evade protecciones DRM.",
        retryable = false,
    ),
    Network(
        "Se perdió la conexión",
        "La red se cortó o el sitio no respondió a tiempo.",
        "Reintenta cuando tengas una conexión estable (mejor por Wi-Fi).",
        retryable = true,
    ),
    NoSpace(
        "Sin espacio en el teléfono",
        "No queda espacio para guardar el video.",
        "Borra videos que ya no necesites y reintenta.",
        retryable = true,
    ),
    Processing(
        "Falló el procesamiento",
        "El video se descargó pero no se pudo preparar para reproducirlo.",
        "Reintenta; si vuelve a fallar, el archivo del sitio puede estar dañado.",
        retryable = true,
    ),
    Unknown("No se pudo descargar", "", "Reintenta; si sigue fallando, revisa los detalles.", retryable = true),
}

private val RULES: List<Pair<Regex, FailureKind>> = listOf(
    """private video|video is private|privado""" to FailureKind.Private,
    """sign in to confirm|confirm you.?re not a bot|login required|cookies|requiere iniciar sesi""" to FailureKind.LoginRequired,
    """available in your country|geo.?restrict|blocked it in your country|unavailable in your|no disponible en tu pa""" to FailureKind.GeoBlocked,
    """video unavailable|has been removed|no longer available|deleted|not found \(404\)|http error 404|404 client error|ya no existe en el origen""" to FailureKind.Removed,
    """drm|widevine|fairplay|sample-aes""" to FailureKind.Drm,
    """unsupported url|no video formats|unable to extract|no suitable|no es un enlace directo""" to FailureKind.NoVideo,
    """http error 403|403 client error|forbidden|access denied|no permite descargar""" to FailureKind.Forbidden,
    """http error 429|too many requests|\b429\b""" to FailureKind.RateLimited,
    """no space left|enospc|disk full""" to FailureKind.NoSpace,
    """timed out|timeout|connection (reset|refused|aborted)|name or service not known|network is unreachable|sin conexi[oó]n|failed to resolve|temporary failure|unable to resolve host""" to FailureKind.Network,
    """ffmpeg|postprocessing|remux|no se pudo preparar""" to FailureKind.Processing,
).map { (pattern, kind) -> Regex(pattern, RegexOption.IGNORE_CASE) to kind }

fun classifyFailure(raw: String?): FailureKind =
    raw?.let { text -> RULES.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second } ?: FailureKind.Unknown
