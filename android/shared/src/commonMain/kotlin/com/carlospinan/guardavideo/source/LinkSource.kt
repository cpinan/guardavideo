package com.carlospinan.guardavideo.source

import io.ktor.http.Url
import io.ktor.http.URLProtocol

/**
 * What a pasted link is, from the phone's point of view.
 *
 * The phone has no yt-dlp: it can only download links that point straight at media
 * (an HLS playlist or a video file). A link to a site's page (YouTube, TikTok…) needs the
 * Guardavideo server, and DRM services cannot be downloaded at all.
 */
sealed interface LinkSource {
    /** Not an http(s) URL. */
    data object Invalid : LinkSource

    /** HLS playlist (.m3u8): downloaded segment by segment. */
    data class Hls(val url: String) : LinkSource

    /** Direct video file: downloaded as is. */
    data class DirectFile(val url: String, val extension: String) : LinkSource

    /** A known site's page: needs a site downloader (yt-dlp), available on Android only. */
    data class SitePage(val provider: String, val url: String = "") : LinkSource

    /** DRM-protected service: never downloadable (the project does not bypass DRM). */
    data class Drm(val provider: String) : LinkSource

    /** Some other web page: no direct media; yt-dlp's generic extractor may still find some. */
    data class UnknownPage(val host: String, val url: String = "") : LinkSource

    val canDownload: Boolean get() = this is Hls || this is DirectFile
}

/** Extensions downloaded as a single file. */
val DIRECT_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "ts")

fun detectSource(raw: String): LinkSource {
    val url = parseHttpUrl(raw.trim()) ?: return LinkSource.Invalid
    val path = url.encodedPath.lowercase()
    if (path.endsWith(".m3u8")) return LinkSource.Hls(url.toString())
    val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
    if (extension in DIRECT_EXTENSIONS) return LinkSource.DirectFile(url.toString(), extension)

    val provider = findProvider(url.host)
    return when {
        provider == null -> LinkSource.UnknownPage(url.host.removePrefix("www."), url.toString())
        provider.access == Access.Drm -> LinkSource.Drm(provider.name)
        else -> LinkSource.SitePage(provider.name, url.toString())
    }
}

internal fun parseHttpUrl(raw: String): Url? {
    if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) return null
    val url = runCatching { Url(raw) }.getOrNull() ?: return null
    val web = url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS
    return url.takeIf { web && it.host.isNotBlank() }
}
