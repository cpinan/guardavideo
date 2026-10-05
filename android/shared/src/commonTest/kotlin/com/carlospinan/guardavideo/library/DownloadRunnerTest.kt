package com.carlospinan.guardavideo.library

import com.carlospinan.guardavideo.download.DownloadException
import com.carlospinan.guardavideo.download.DownloadListener
import com.carlospinan.guardavideo.download.DownloadResult
import com.carlospinan.guardavideo.download.VideoDownloader
import com.carlospinan.guardavideo.source.LinkSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRunnerTest {
    private val fs = FakeFileSystem()
    private val dir = "/downloads".toPath()
    private val repo = JobRepository(FakeJobDao(), fs, clock = { 1 }, newId = { "j1" })

    private fun runner(
        postProcessor: MediaPostProcessor = MediaPostProcessor { it.path },
        block: suspend (LinkSource, String, Path, DownloadListener) -> DownloadResult,
    ) =
        DownloadRunner(
            repo,
            object : VideoDownloader {
                override suspend fun download(source: LinkSource, id: String, destDir: Path, quality: Int?, listener: DownloadListener) =
                    block(source, id, destDir, listener)
            },
            fs,
            dir,
            postProcessor,
        )

    private suspend fun queued() = assertIs<CreateResult.Created>(repo.create("https://x.org/My%20clip.mp4", null)).job

    @Test
    fun successStoresTheFileAndWholePercentProgress() = runTest {
        val job = queued()
        val seen = mutableListOf<Float>()
        val status = runner { _, id, destDir, listener ->
            listOf(0.4f, 10.2f, 10.9f, 55f).forEach { listener.progress(it); delay(100) }
            val file = destDir / "$id.mp4"
            fs.write(file) { writeUtf8("abc") }
            DownloadResult(file, "My clip", 3)
        }.run(job.id) { seen += it.percent }

        assertEquals(JobStatus.Done, status)
        val stored = repo.get(job.id)!!
        assertEquals("/downloads/j1.mp4", stored.filePath)
        assertEquals("My clip", stored.title)
        assertEquals(3L, stored.sizeBytes)
        assertEquals(listOf(0f, 10f, 55f, 100f), seen) // Running(0), whole percents, Done
    }

    @Test
    fun titleAndPreviewAppearWhileStillDownloading() = runTest {
        val job = queued()
        val seen = mutableListOf<Pair<String?, String?>>()
        runner { _, id, destDir, listener ->
            listener.title("Atrás la heredera")
            listener.thumbnail(destDir / "${id}_thumb.jpg")
            delay(100)
            val file = destDir / "$id.mp4"
            fs.write(file) { writeUtf8("v") }
            DownloadResult(file, "título final", 1)
        }.run(job.id) { if (it.status == JobStatus.Running) seen += it.title to it.thumbnailPath }

        assertTrue(seen.any { it == "Atrás la heredera" to "/downloads/j1_thumb.jpg" })
        val done = repo.get(job.id)!!
        assertEquals("Atrás la heredera", done.title) // the early title wins over the file name
        assertEquals("/downloads/j1_thumb.jpg", done.thumbnailPath)
    }

    @Test
    fun downloadErrorsBecomeAReadableJobError() = runTest {
        val job = queued()
        val status = runner { _, _, _, _ -> throw DownloadException("El video ya no existe en el origen (404)") }.run(job.id)
        assertEquals(JobStatus.Error, status)
        assertEquals("El video ya no existe en el origen (404)", repo.get(job.id)!!.error)
    }

    @Test
    fun aJobCancelledBeforeTheWorkStartsIsNotDownloaded() = runTest {
        val job = queued()
        repo.cancel(job.id)
        var called = false
        assertNull(runner { _, _, _, _ -> called = true; error("unreachable") }.run(job.id))
        assertEquals(false, called)
        assertEquals(JobStatus.Cancelled, repo.get(job.id)!!.status)
    }

    @Test
    fun systemStopRequeuesButUserCancelStaysCancelled() = runTest {
        val job = queued()
        val started = CompletableDeferred<Unit>()
        val hang = runner { _, _, _, _ -> started.complete(Unit); CompletableDeferred<DownloadResult>().await() }

        val stopped = async { runCatching { hang.run(job.id) } }
        started.await()
        stopped.cancel() // e.g. the OS stopped the worker
        stopped.join()
        assertEquals(JobStatus.Queued, repo.get(job.id)!!.status)

        val started2 = CompletableDeferred<Unit>()
        val hang2 = runner { _, _, _, _ -> started2.complete(Unit); CompletableDeferred<DownloadResult>().await() }
        val cancelled = async { runCatching { hang2.run(job.id) } }
        started2.await()
        repo.cancel(job.id) // the user tapped Cancel, then the work is cancelled
        cancelled.cancel()
        cancelled.join()
        assertEquals(JobStatus.Cancelled, repo.get(job.id)!!.status)
    }

    @Test
    fun postProcessingReplacesTheFileAndItsSize() = runTest {
        val job = queued()
        val remux = MediaPostProcessor { result ->
            val path = result.path
            val mp4 = path.parent!! / "${path.name.substringBeforeLast('.')}.mp4"
            fs.write(mp4) { writeUtf8("remuxed!") }
            fs.delete(path)
            mp4
        }
        runner(remux) { _, id, destDir, _ ->
            val ts = destDir / "$id.ts"
            fs.write(ts) { writeUtf8("ts") }
            DownloadResult(ts, null, 2)
        }.run(job.id)
        val stored = repo.get(job.id)!!
        assertEquals("/downloads/j1.mp4", stored.filePath)
        assertEquals(8L, stored.sizeBytes)
    }

    @Test
    fun durationComesFromHlsSegmentsOrTheProbe() = runTest {
        val job = queued()
        DownloadRunner(
            repo,
            object : VideoDownloader {
                override suspend fun download(source: LinkSource, id: String, destDir: Path, quality: Int?, listener: DownloadListener): DownloadResult {
                    val ts = destDir / "$id.ts"
                    fs.write(ts) { writeUtf8("ts") }
                    return DownloadResult(ts, null, 2, listOf(com.carlospinan.guardavideo.download.SegmentSpan(4.5, 0, 1), com.carlospinan.guardavideo.download.SegmentSpan(2.0, 1, 1)))
                }
            },
            fs, dir, probe = { 999 },
        ).run(job.id)
        assertEquals(6_500L, repo.get(job.id)!!.durationMs)
    }

    @Test
    fun backfillAddsDurationsOnlyWhereMissing() = runTest {
        val job = queued()
        repo.update(job.id) { it.copy(status = JobStatus.Done, filePath = "/downloads/a.mp4") }
        backfillDurations(repo) { 42_000 }
        assertEquals(42_000L, repo.get(job.id)!!.durationMs)
        backfillDurations(repo) { 1 } // already known: untouched
        assertEquals(42_000L, repo.get(job.id)!!.durationMs)
    }

    @Test
    fun fragmentedVideosAreConvertedOnceAndGetTheirRealLength() = runTest {
        val job = queued()
        val file = dir / "j1.mp4"
        fs.createDirectories(dir)
        // ftyp + moof: fragmented
        fs.write(file) { writeInt(12).writeUtf8("ftyp").writeInt(0).writeInt(12).writeUtf8("moof").writeInt(0) }
        repo.update(job.id) { it.copy(status = JobStatus.Done, filePath = file.toString(), durationMs = 450_000) }
        var converted = 0
        val remux = MediaPostProcessor { result ->
            converted++
            fs.write(result.path) { writeInt(12).writeUtf8("ftyp").writeInt(0).writeInt(12).writeUtf8("moov").writeInt(0) }
            result.path
        }
        repairFragmentedVideos(repo, fs, remux, probe = { 4_380_000 })
        assertEquals(4_380_000L, repo.get(job.id)!!.durationMs) // 1:13:00, not 7:30
        repairFragmentedVideos(repo, fs, remux, probe = { 1 }) // now regular: skipped
        assertEquals(1, converted)
    }

    @Test
    fun aFailedPostProcessKeepsTheOriginalDownload() = runTest {
        val job = queued()
        val status = runner(MediaPostProcessor { error("remux failed") }) { _, id, destDir, _ ->
            val ts = destDir / "$id.ts"
            fs.write(ts) { writeUtf8("ts") }
            DownloadResult(ts, null, 2)
        }.run(job.id)
        assertEquals(JobStatus.Done, status)
        assertEquals("/downloads/j1.ts", repo.get(job.id)!!.filePath)
    }
}
