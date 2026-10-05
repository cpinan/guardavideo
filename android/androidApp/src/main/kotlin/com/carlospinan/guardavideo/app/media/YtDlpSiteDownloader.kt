package com.carlospinan.guardavideo.app.media

import android.content.Context
import com.carlospinan.guardavideo.download.DownloadException
import com.carlospinan.guardavideo.download.DownloadListener
import com.carlospinan.guardavideo.download.DownloadResult
import com.carlospinan.guardavideo.download.VideoDownloader
import com.carlospinan.guardavideo.source.LinkSource
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Site pages (Dailymotion, YouTube, …) through yt-dlp itself, bundled with Python and ffmpeg by
 * youtubedl-android. Same options as the Mac server: best video+audio up to the chosen height,
 * merged to MP4, one video (no playlists). yt-dlp never downloads DRM formats.
 */
@Singleton
class YtDlpSiteDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fs: FileSystem,
) : VideoDownloader {
    private val initLock = Mutex()
    private var ready = false

    /** Unpacks Python/yt-dlp/ffmpeg on first use (seconds, once per install or update). */
    suspend fun ensureReady() = initLock.withLock {
        if (!ready) {
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
            }
            ready = true
        }
    }

    override suspend fun download(
        source: LinkSource,
        id: String,
        destDir: Path,
        quality: Int?,
        listener: DownloadListener,
    ): DownloadResult {
        val url = when (source) {
            is LinkSource.SitePage -> source.url
            is LinkSource.UnknownPage -> source.url
            else -> throw DownloadException("yt-dlp solo descarga páginas de sitios")
        }
        try {
            ensureReady()
        } catch (e: YoutubeDLException) {
            throw DownloadException("No se pudo preparar yt-dlp: ${e.message}", e)
        }
        val height = quality?.let { "[height<=$it]" }.orEmpty()
        val request = YoutubeDLRequest(url).apply {
            addOption("-o", "$destDir/$id.%(ext)s")
            addOption("-f", "bv*$height+ba/b$height/b")
            addOption("--merge-output-format", "mp4")
            addOption("--no-playlist")
            // HLS sites (Dailymotion…) are fragment by fragment: fetch 8 at a time, like the
            // built-in downloader, instead of yt-dlp's default of one.
            addOption("--concurrent-fragments", 8)
            addOption("--no-mtime")
            addOption("--newline")
            // The title is printed once the file is final; --progress keeps the progress lines
            // that --print would otherwise silence.
            addOption("--print", "after_move:%(title)s")
            addOption("--progress")
        }

        val response = withContext(Dispatchers.IO) {
            coroutineScope {
                val finished = AtomicBoolean(false)
                // execute() blocks its thread; cancelling the coroutine kills the yt-dlp process.
                val killer = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        if (!finished.get()) YoutubeDL.getInstance().destroyProcessById(id)
                    }
                }
                try {
                    YoutubeDL.getInstance().execute(request, id) { progress, _, _ ->
                        if (progress >= 0) listener.progress(progress)
                    }
                } catch (e: YoutubeDL.CanceledException) {
                    throw CancellationException("cancelled").apply { initCause(e) }
                } catch (e: YoutubeDLException) {
                    throw DownloadException(friendlyError(e.message), e)
                } finally {
                    finished.set(true)
                    killer.cancel()
                }
            }
        }

        val file = fs.list(destDir).firstOrNull { path ->
            val name = path.name
            name.startsWith("$id.") && !name.endsWith(".part") && !name.endsWith(".ytdl") && ".temp." !in name
        } ?: throw DownloadException("yt-dlp terminó pero no dejó ningún archivo")
        val title = response.out.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() && !it.startsWith("[") }
        listener.progress(100f)
        return DownloadResult(file, title, fs.metadata(file).size ?: 0)
    }

    /** Last "ERROR:" line from yt-dlp, without its prefix: what actually went wrong. */
    private fun friendlyError(raw: String?): String {
        val line = raw.orEmpty().lines().lastOrNull { it.contains("ERROR:") }
            ?.substringAfter("ERROR:")?.trim()
        return when {
            line == null -> "yt-dlp no pudo descargar este enlace"
            "DRM" in line -> "Este video está protegido con DRM: no se puede descargar"
            else -> line.take(300)
        }
    }
}
