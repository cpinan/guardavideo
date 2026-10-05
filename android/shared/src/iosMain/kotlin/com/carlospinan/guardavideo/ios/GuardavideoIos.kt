package com.carlospinan.guardavideo.ios

import com.carlospinan.guardavideo.download.DownloadResult
import com.carlospinan.guardavideo.download.MediaDownloader
import com.carlospinan.guardavideo.download.localPlaylist
import com.carlospinan.guardavideo.library.CreateResult
import com.carlospinan.guardavideo.library.DownloadRunner
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.MediaPostProcessor
import com.carlospinan.guardavideo.library.createDatabase
import com.carlospinan.guardavideo.library.sourceMessage
import com.carlospinan.guardavideo.player.PlaybackController
import com.carlospinan.guardavideo.player.PlayerUiState
import com.carlospinan.guardavideo.player.VideoPlayer
import com.carlospinan.guardavideo.source.detectSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import kotlinx.coroutines.Job as CoroutineJob

/** What the URL field shows. */
class LinkInfo(val message: String, val canDownload: Boolean)

/** Result of tapping "Descargar": [message] for the user, [started] if a download began. */
class SubmitOutcome(val message: String, val started: Boolean)

/** Handle returned by observers; call [cancel] when the view goes away. */
class Subscription internal constructor(private val job: CoroutineJob) {
    fun cancel() = job.cancel()
}

/**
 * Entry point for the Swift app: the same library, runner and downloader as Android, wired for
 * iOS (Darwin HTTP engine, Documents/Videos). Downloads run while the app is in the foreground
 * (plus the short background time the app requests); there is no WorkManager equivalent here.
 *
 * Call everything from the main thread (Kotlin/Native's rule for suspend calls from Swift).
 */
class GuardavideoIos {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fs = FileSystem.SYSTEM
    private val videosDir: Path = "${documentsPath()}/Videos".toPath()
    private val database = createDatabase()
    private val repository = JobRepository(
        database.jobs(),
        fs,
        database.history(),
        clock = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
        newId = { NSUUID().UUIDString().replace("-", "").lowercase().take(12) },
    )
    private val runner = DownloadRunner(
        repository,
        MediaDownloader(HttpClient(Darwin) { install(HttpTimeout) { socketTimeoutMillis = 60_000 } }, fs, io = Dispatchers.IO),
        fs,
        videosDir,
        PlaylistIndexer(fs),
    )
    private val running = mutableMapOf<String, CoroutineJob>()

    /** Calls [onChange] on the main thread with the whole library, newest first, on every change. */
    fun observeJobs(onChange: (List<Job>) -> Unit): Subscription =
        Subscription(scope.launch { repository.observeJobs().collect { onChange(it) } })

    fun describe(url: String): LinkInfo = detectSource(url.trim()).let { LinkInfo(sourceMessage(it), it.canDownload) }

    /** [quality] is the wanted height in pixels; 0 means the best available. */
    suspend fun submit(url: String, quality: Int): SubmitOutcome =
        when (val result = repository.create(url, quality.takeIf { it > 0 })) {
            is CreateResult.Created -> {
                start(result.job.id)
                SubmitOutcome("Descarga en cola", true)
            }
            is CreateResult.Existing -> SubmitOutcome(
                if (result.job.status.isActive) "Ese video ya se está descargando" else "Ya tenías este video",
                false,
            )
            is CreateResult.Rejected -> SubmitOutcome(result.reason, false)
        }

    /** Restarts downloads left queued or interrupted by the app being closed. */
    fun resumePending() {
        scope.launch { repository.active().forEach { start(it.id) } }
    }

    fun cancel(id: String) {
        scope.launch {
            repository.cancel(id) // first: the runner then knows the user stopped it
            running.remove(id)?.cancel()
        }
    }

    fun retry(id: String) {
        scope.launch { repository.retry(id)?.let { start(it.id) } }
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }

    fun setStarred(id: String, starred: Boolean) {
        scope.launch { repository.setStarred(id, starred) }
    }

    fun savePosition(id: String, positionMs: Long, durationMs: Long) {
        scope.launch { repository.savePosition(id, positionMs, durationMs.takeIf { it > 0 }) }
    }

    fun hasActiveDownloads(): Boolean = running.isNotEmpty()

    /**
     * Shared playback rules (resume, periodic save, view count) over a Swift AVPlayer adapter
     * that implements [VideoPlayer]. One per player screen; call [IosPlayback.close] on exit.
     */
    fun playback(player: VideoPlayer): IosPlayback =
        IosPlayback(PlaybackController(player, repository, scope, fileExists = { NSFileManager.defaultManager.fileExistsAtPath(it) }), scope)

    private fun start(id: String) {
        if (running[id]?.isActive == true) return
        running[id] = scope.launch {
            try {
                runner.run(id)
            } finally {
                running.remove(id)
            }
        }
    }
}

/**
 * AVPlayer cannot open a bare .ts file, but it plays HLS. For TS downloads this writes a local
 * playlist that indexes the single file by byte range and returns it as the file to play.
 */
private class PlaylistIndexer(private val fs: FileSystem) : MediaPostProcessor {
    override suspend fun process(result: DownloadResult): Path {
        if (result.segments.isEmpty() || !result.path.name.endsWith(".ts")) return result.path
        val playlist = result.path.parent!! / result.path.name.replace(".ts", ".m3u8")
        fs.write(playlist) { writeUtf8(localPlaylist(result.segments, result.path.name)) }
        return playlist
    }
}

/** Swift-friendly face of [PlaybackController]: callbacks instead of Flow and suspend. */
class IosPlayback internal constructor(private val controller: PlaybackController, private val scope: CoroutineScope) {
    fun open(id: String) {
        scope.launch { controller.open(id) }
    }

    fun observe(onChange: (PlayerUiState) -> Unit): Subscription =
        Subscription(scope.launch { controller.state.collect { onChange(it) } })

    fun pause() = controller.pause()

    fun close() = controller.close()
}

@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String =
    requireNotNull(NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)?.path)
