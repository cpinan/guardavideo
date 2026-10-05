package com.carlospinan.guardavideo.library

import com.carlospinan.guardavideo.source.detectSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobRepositoryTest {
    private val fs = FakeFileSystem().apply { createDirectories("/v".toPath()) }
    private var now = 1_000L
    private var ids = 0
    private val repo = JobRepository(FakeJobDao(), fs, clock = { now++ }, newId = { "id${++ids}" })

    private suspend fun done(url: String, file: String): Job {
        val job = assertIs<CreateResult.Created>(repo.create(url, null)).job
        fs.write(file.toPath()) { writeUtf8("video") }
        return repo.update(job.id) { it.copy(status = JobStatus.Done, filePath = file) }!!
    }

    @Test
    fun withSiteSupportPageLinksAreQueuedButDrmNever() = runTest {
        val android = JobRepository(FakeJobDao(), fs, clock = { 1 }, newId = { "s1" }, sitePages = true)
        assertIs<CreateResult.Created>(android.create("https://www.dailymotion.com/video/x4at2w3", null))
        assertIs<CreateResult.Rejected>(android.create("https://www.netflix.com/watch/1", null))
        assertEquals("Dailymotion · se descargará con yt-dlp", sourceMessage(detectSource("https://dailymotion.com/video/x1"), sitePages = true))
        assertEquals(true, canDownload(detectSource("https://example.org/page"), sitePages = true))
        assertEquals(false, canDownload(detectSource("https://example.org/page"), sitePages = false))
    }

    @Test
    fun pageLinksAreRejectedWithAnExplanation() = runTest {
        val result = assertIs<CreateResult.Rejected>(repo.create("https://youtube.com/watch?v=1", null))
        assertTrue(result.reason.startsWith("YouTube:"))
        assertTrue(repo.observeJobs().first().isEmpty())
    }

    @Test
    fun sameLinkReusesTheRunningOrDownloadedJob() = runTest {
        val first = assertIs<CreateResult.Created>(repo.create(" https://h.example/a.m3u8 ", 720)).job
        assertEquals("https://h.example/a.m3u8", first.url)
        assertEquals(first, assertIs<CreateResult.Existing>(repo.create("https://h.example/a.m3u8", 720)).job)
        assertIs<CreateResult.Created>(repo.create("https://h.example/a.m3u8", 480)) // other quality

        val video = done("https://x.org/b.mp4", "/v/b.mp4")
        assertEquals(video.id, assertIs<CreateResult.Existing>(repo.create("https://x.org/b.mp4", null)).job.id)
        fs.delete("/v/b.mp4".toPath()) // file gone: download it again
        assertIs<CreateResult.Created>(repo.create("https://x.org/b.mp4", null))
    }

    @Test
    fun deleteKeepsAFileAnotherJobStillUses() = runTest {
        val a = done("https://x.org/a.mp4", "/v/shared.mp4")
        val b = repo.update(assertIs<CreateResult.Created>(repo.create("https://x.org/a.mp4?copy", null)).job.id) {
            it.copy(status = JobStatus.Done, filePath = "/v/shared.mp4")
        }!!
        assertTrue(repo.delete(a.id))
        assertTrue(fs.exists("/v/shared.mp4".toPath()))
        assertTrue(repo.delete(b.id))
        assertFalse(fs.exists("/v/shared.mp4".toPath()))
    }

    @Test
    fun deleteAlsoRemovesCompanionFilesOfTheSameName() = runTest {
        val video = done("https://x.org/a.m3u8", "/v/id1.m3u8")
        fs.write("/v/id1.ts".toPath()) { writeUtf8("ts") }
        fs.write("/v/id10.ts".toPath()) { writeUtf8("other job") }
        assertTrue(repo.delete(video.id))
        assertEquals(listOf("id10.ts"), fs.list("/v".toPath()).map { it.name })
    }

    @Test
    fun deleteAlsoRemovesTheThumbnail() = runTest {
        val video = done("https://x.org/a.mp4", "/v/a.mp4")
        fs.write("/v/a_thumb.jpg".toPath()) { writeUtf8("jpg") }
        repo.update(video.id) { it.copy(thumbnailPath = "/v/a_thumb.jpg") }
        repo.delete(video.id)
        assertFalse(fs.exists("/v/a_thumb.jpg".toPath()))
    }

    @Test
    fun activeJobsCannotBeDeletedAndOnlyFailuresRetry() = runTest {
        val job = assertIs<CreateResult.Created>(repo.create("https://x.org/a.mp4", null)).job
        assertFalse(repo.delete(job.id))
        assertNull(repo.retry(job.id)) // still queued: nothing to retry
        repo.cancel(job.id)
        val retried = repo.retry(job.id)!!
        assertEquals(JobStatus.Queued, retried.status)
        assertNull(retried.error)
    }

    @Test
    fun positionResetsNearTheEndAndMarksWatched() = runTest {
        val video = done("https://x.org/a.mp4", "/v/a.mp4")
        repo.savePosition(video.id, 30_000, 100_000)
        assertEquals(30_000, repo.get(video.id)!!.positionMs)
        repo.savePosition(video.id, 98_500, 100_000)
        val after = repo.get(video.id)!!
        assertEquals(0, after.positionMs)
        assertTrue(after.watched)
    }

    @Test
    fun viewsAreCountedOnlyForFinishedVideos() = runTest {
        val queued = assertIs<CreateResult.Created>(repo.create("https://x.org/q.mp4", null)).job
        assertEquals(0, repo.recordView(queued.id)!!.viewCount)
        val video = done("https://x.org/a.mp4", "/v/a.mp4")
        repo.recordView(video.id)
        val after = repo.recordView(video.id)!!
        assertEquals(2, after.viewCount)
        assertTrue(after.lastViewedAt != null)
    }

    @Test
    fun onlyFinishedVideosCanBeStarred() = runTest {
        val queued = assertIs<CreateResult.Created>(repo.create("https://x.org/q.mp4", null)).job
        assertFalse(repo.setStarred(queued.id, true)!!.starred)
        val video = done("https://x.org/a.mp4", "/v/a.mp4")
        assertTrue(repo.setStarred(video.id, true)!!.starred)
    }
}
