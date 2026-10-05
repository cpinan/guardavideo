package com.carlospinan.guardavideo.library

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoryTest {
    private val fs = FakeFileSystem().apply { createDirectories("/v".toPath()) }
    private var now = 100L
    private var ids = 0
    private val jobs = FakeJobDao()
    private val historyDao = FakeHistoryDao()
    private val repo = JobRepository(jobs, fs, historyDao, clock = { now++ }, newId = { "id${++ids}" }, sitePages = true)

    private suspend fun history() = repo.observeHistory().first()

    @Test
    fun everyLinkIsRecordedAndFollowsItsJob() = runTest {
        val job = assertIs<CreateResult.Created>(repo.create("https://www.dailymotion.com/video/x1", null)).job
        assertEquals(HistoryStatus.InProgress, history().single().status)

        repo.update(job.id) { it.copy(title = "Episodio 1") }
        repo.update(job.id) { it.copy(percent = 50f) } // progress: no history write needed
        repo.update(job.id) { it.copy(status = JobStatus.Done, filePath = "/v/a.mp4", sizeBytes = 9) }

        val entry = history().single()
        assertEquals(HistoryStatus.Downloaded, entry.status)
        assertEquals("Episodio 1", entry.displayTitle)
        assertEquals(9L, entry.sizeBytes)
        assertTrue(entry.finishedAt != null)
    }

    @Test
    fun deletingTheVideoKeepsTheHistoryEntry() = runTest {
        val job = assertIs<CreateResult.Created>(repo.create("https://x.org/a.mp4", null)).job
        repo.update(job.id) { it.copy(status = JobStatus.Done, title = "Clip") }
        repo.delete(job.id)
        val entry = history().single()
        assertEquals(HistoryStatus.Deleted, entry.status)
        assertEquals("Clip", entry.displayTitle)
        assertEquals("https://x.org/a.mp4", entry.url)
    }

    @Test
    fun failuresAreKeptWithTheirReason() = runTest {
        val job = assertIs<CreateResult.Created>(repo.create("https://x.org/b.mp4", null)).job
        repo.update(job.id) { it.copy(status = JobStatus.Error, error = "Access forbidden") }
        assertEquals(HistoryStatus.Failed to "Access forbidden", history().single().let { it.status to it.error })
    }

    @Test
    fun existingJobsSeedAnEmptyHistoryAndClearingEmptiesIt() = runTest {
        val plain = JobRepository(jobs, fs, clock = { 1 }, newId = { "old" }) // before history existed
        plain.create("https://x.org/old.mp4", null)
        repo.seedHistoryIfEmpty()
        assertEquals(listOf("old"), history().map { it.jobId })
        repo.clearHistory()
        assertTrue(history().isEmpty())
    }
}
