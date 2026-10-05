package com.carlospinan.guardavideo.library

import com.carlospinan.guardavideo.download.DownloadException
import com.carlospinan.guardavideo.download.DownloadListener
import com.carlospinan.guardavideo.download.DownloadResult
import com.carlospinan.guardavideo.download.isFragmentedMp4
import com.carlospinan.guardavideo.download.VideoDownloader
import com.carlospinan.guardavideo.source.detectSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Optional step after a download, e.g. remuxing MPEG-TS into MP4 on the device. Returns the
 * file to keep (it may be a new one; the old one is the processor's to delete). A failure here
 * never fails the job: the original download is kept.
 */
fun interface MediaPostProcessor {
    suspend fun process(result: DownloadResult): Path
}

/** Reads a finished file's duration (platform media APIs); null when unknown. */
fun interface MediaProbe {
    suspend fun durationMs(file: Path): Long?
}

/**
 * Videos downloaded before durations were stored (schema v1) get one now, once: the thumbnail
 * badge and the "watched" bar need it. Cheap: only finished jobs without a duration are probed.
 */
suspend fun backfillDurations(repo: JobRepository, probe: MediaProbe) {
    repo.observeJobsOnce().filter { it.status == JobStatus.Done && it.durationMs == null && it.filePath != null }
        .forEach { job ->
            val ms = runCatching { probe.durationMs(job.filePath!!.toPath()) }.getOrNull()
            if (ms != null && ms > 0) repo.update(job.id) { it.copy(durationMs = it.durationMs ?: ms) }
        }
}

/**
 * Videos saved before fragmented MP4s were converted (their length reads as the first fragment
 * only): convert them now with [postProcessor] and store the real length. Runs once per file;
 * afterwards the files are regular MP4s and are skipped.
 */
suspend fun repairFragmentedVideos(
    repo: JobRepository,
    fs: FileSystem,
    postProcessor: MediaPostProcessor,
    probe: MediaProbe,
) {
    repo.observeJobsOnce()
        .filter { it.status == JobStatus.Done && it.filePath?.endsWith(".mp4") == true }
        .filter { isFragmentedMp4(fs, it.filePath!!.toPath()) }
        .forEach { job ->
            val file = runCatching { postProcessor.process(DownloadResult(job.filePath!!.toPath(), job.title, job.sizeBytes ?: 0)) }
                .getOrNull() ?: return@forEach
            if (isFragmentedMp4(fs, file)) return@forEach // conversion did not happen: keep what we had
            val duration = runCatching { probe.durationMs(file) }.getOrNull()
            repo.update(job.id) {
                it.copy(
                    filePath = file.toString(),
                    sizeBytes = fs.metadata(file).size ?: it.sizeBytes,
                    durationMs = duration ?: it.durationMs,
                )
            }
        }
}

/**
 * Runs one job end to end: Queued → Running → Done / Error, writing progress to the library.
 * The platform decides *where* it runs (WorkManager on Android); this decides *what* happens.
 *
 * Progress is written only when the whole percent changes, so a fast download does not turn
 * into thousands of database writes.
 */
class DownloadRunner(
    private val repo: JobRepository,
    private val downloader: VideoDownloader,
    private val fs: FileSystem,
    private val downloadsDir: Path,
    private val postProcessor: MediaPostProcessor = MediaPostProcessor { it.path },
    private val probe: MediaProbe = MediaProbe { null },
) {
    /** Returns the final status, or null if the job was deleted or already finished. */
    suspend fun run(id: String, onUpdate: (Job) -> Unit = {}): JobStatus? {
        val job = repo.get(id) ?: return null
        if (job.status.isTerminal) return null // cancelled (or done) before the work started
        repo.update(id) { it.copy(status = JobStatus.Running, error = null) }?.let(onUpdate)

        val percentFlow = MutableStateFlow(0)
        val retries = MutableStateFlow(0)
        val listener = object : DownloadListener {
            override fun progress(percent: Float) {
                if (percent >= 0) percentFlow.value = percent.toInt()
            }

            override fun retrying(attempt: Int) {
                retries.value = attempt
            }
        }
        return try {
            fs.createDirectories(downloadsDir)
            val result = coroutineScope {
                val writer = launch {
                    combine(percentFlow, retries) { p, r -> p to r }.distinctUntilChanged().drop(1).collect { (p, r) ->
                        repo.update(id) { it.copy(percent = p.toFloat(), retries = r) }?.let(onUpdate)
                    }
                }
                downloader.download(detectSource(job.url), id, downloadsDir, job.quality, listener)
                    .also { writer.cancel() }
            }
            val file = try {
                postProcessor.process(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                result.path
            }
            // HLS already says how long each segment is; otherwise ask the platform.
            val duration = result.segments.takeIf { it.isNotEmpty() }
                ?.let { spans -> (spans.sumOf { it.durationSec } * 1000).toLong() }
                ?: runCatching { probe.durationMs(file) }.getOrNull()
            repo.update(id) {
                it.copy(
                    status = JobStatus.Done, percent = 100f, retries = 0, error = null,
                    filePath = file.toString(), sizeBytes = fs.metadata(file).size ?: result.sizeBytes,
                    title = it.title ?: result.title,
                    durationMs = it.durationMs ?: duration,
                )
            }?.let(onUpdate)
            JobStatus.Done
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                // The user's cancel already wrote Cancelled. Anything else (the system stopped the
                // work) goes back to the queue so it resumes when the work is rescheduled.
                repo.update(id) { if (it.status == JobStatus.Running) it.copy(status = JobStatus.Queued, percent = 0f) else it }
            }
            throw e
        } catch (e: DownloadException) {
            fail(id, e.message ?: "Error al descargar", onUpdate)
        } catch (e: Exception) {
            fail(id, "Error inesperado: ${e.message ?: e::class.simpleName}", onUpdate)
        }
    }

    private suspend fun fail(id: String, message: String, onUpdate: (Job) -> Unit): JobStatus {
        repo.update(id) { it.copy(status = JobStatus.Error, error = message.take(300), retries = 0) }?.let(onUpdate)
        return JobStatus.Error
    }
}
