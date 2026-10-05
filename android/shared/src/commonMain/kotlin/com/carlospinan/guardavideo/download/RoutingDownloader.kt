package com.carlospinan.guardavideo.download

import com.carlospinan.guardavideo.library.canDownload
import com.carlospinan.guardavideo.library.sourceMessage
import com.carlospinan.guardavideo.source.LinkSource
import okio.Path

/**
 * Direct media (HLS, files) goes to the built-in [direct] downloader; site pages go to the
 * platform's [site] downloader (yt-dlp on Android), when there is one. DRM never reaches either.
 */
class RoutingDownloader(
    private val direct: VideoDownloader,
    private val site: VideoDownloader?,
) : VideoDownloader {
    override suspend fun download(
        source: LinkSource,
        id: String,
        destDir: Path,
        quality: Int?,
        listener: DownloadListener,
    ): DownloadResult = when (source) {
        is LinkSource.Hls, is LinkSource.DirectFile -> direct.download(source, id, destDir, quality, listener)
        is LinkSource.SitePage, is LinkSource.UnknownPage ->
            site?.download(source, id, destDir, quality, listener)
                ?: throw DownloadException("Este enlace no se puede descargar en este dispositivo")
        is LinkSource.Drm -> throw DownloadException("${source.provider} está protegido con DRM: no se puede descargar")
        LinkSource.Invalid -> throw DownloadException("Ingresa una URL http(s) válida")
    }
}

/** What this device can do with a link, for the UI: one place instead of flags in every screen. */
class LinkPolicy(val sitePages: Boolean) {
    fun canDownload(source: LinkSource): Boolean = canDownload(source, sitePages)

    fun message(source: LinkSource): String = sourceMessage(source, sitePages)
}
