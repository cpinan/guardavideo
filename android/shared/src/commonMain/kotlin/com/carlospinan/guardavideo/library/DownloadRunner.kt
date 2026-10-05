package com.carlospinan.guardavideo.library

import com.carlospinan.guardavideo.download.DownloadException
import com.carlospinan.guardavideo.download.DownloadListener
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

/**
 * Optional step after a download, e.g. remuxing MPEG-TS into MP4 on the device. Returns the
 * file to keep (it may be a new one; the old one is the processor's to delete). A failure here
 * never fails the job: the original download is kept.
 */
fun interface MediaPostProcessor {
    suspend fun process(file: Path): Path
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
    private val postProcessor: MediaPostProcessor = MediaPostProcessor { it },
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
                postProcessor.process(result.path)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                result.path
            }
            repo.update(id) {
                it.copy(
                    status = JobStatus.Done, percent = 100f, retries = 0, error = null,
                    filePath = file.toString(), sizeBytes = fs.metadata(file).size ?: result.sizeBytes,
                    title = it.title ?: result.title,
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
