package com.carlospinan.guardavideo.app

import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.media.StorageInfo
import com.carlospinan.guardavideo.app.media.VideoExporter
import com.carlospinan.guardavideo.app.ui.library.DayLabel
import com.carlospinan.guardavideo.app.ui.library.groupByDay
import java.time.ZoneOffset
import com.carlospinan.guardavideo.download.LinkPolicy
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.app.ui.library.LibraryViewModel
import com.carlospinan.guardavideo.app.ui.library.UiMessage
import com.carlospinan.guardavideo.app.ui.library.extractUrl
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private class FakeQueue : DownloadQueue {
        val enqueued = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        override fun enqueue(jobId: String) { enqueued += jobId }
        override fun cancel(jobId: String) { cancelled += jobId }
    }

    private val queue = FakeQueue()
    private val exporter = object : VideoExporter {
        val exported = mutableListOf<String>()
        var ok = true
        override suspend fun exportToGallery(job: Job): Boolean { exported += job.id; return ok }
    }
    private var ids = 0
    private val repo = JobRepository(FakeJobDao(), FakeFileSystem(), clock = { 1 }, newId = { "id${++ids}" }, sitePages = true)
    private lateinit var vm: LibraryViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = LibraryViewModel(repo, queue, exporter, LinkPolicy(sitePages = true), storage = object : StorageInfo { override fun freeBytes() = 5L }, now = { NOW })
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aDirectLinkIsQueuedAndTheFieldCleared() = runTest {
        vm.onUrlChange("https://cdn.example.com/show/index.m3u8")
        vm.submit()
        assertEquals(listOf("id1"), queue.enqueued)
        assertEquals("", vm.url)
        assertIs<UiMessage.Res>(vm.messages.first())
        assertEquals(JobStatus.Queued, repo.get("id1")!!.status)
    }

    @Test
    fun theSameLinkTwiceIsNotQueuedAgain() = runTest {
        vm.onUrlChange("https://x.org/a.mp4"); vm.submit()
        vm.messages.first()
        vm.onUrlChange("https://x.org/a.mp4"); vm.submit()
        assertEquals(R.string.already_downloading, (vm.messages.first() as UiMessage.Res).id)
        assertEquals(1, queue.enqueued.size)
    }

    @Test
    fun siteLinksAreQueuedForYtDlpButDrmIsNot() = runTest {
        vm.onUrlChange("https://www.dailymotion.com/video/x4at2w3")
        assertTrue(vm.canSubmit)
        assertEquals("Dailymotion · se descargará con yt-dlp", vm.linkMessage)
        vm.submit()
        assertEquals(listOf("id1"), queue.enqueued)

        vm.onUrlChange("https://www.netflix.com/watch/1")
        assertTrue(!vm.canSubmit)
        vm.submit()
        assertEquals(1, queue.enqueued.size)
    }

    @Test
    fun cancelMarksTheJobFirstThenStopsTheWork() = runTest {
        vm.onUrlChange("https://x.org/a.mp4"); vm.submit()
        vm.cancel("id1")
        assertEquals(JobStatus.Cancelled, repo.get("id1")!!.status)
        assertEquals(listOf("id1"), queue.cancelled)
        vm.retry("id1")
        assertEquals(listOf("id1", "id1"), queue.enqueued)
    }

    @Test
    fun sharedTextKeepsOnlyTheLink() {
        assertEquals("https://x.org/a.m3u8?t=1", extractUrl("Mira esto: https://x.org/a.m3u8?t=1."))
        assertEquals("http://h/v.mp4", extractUrl("(http://h/v.mp4)"))
        assertNull(extractUrl("sin enlace"))
    }

    @Test
    fun saveToGalleryReportsTheResult() = runTest {
        vm.onUrlChange("https://x.org/a.mp4"); vm.submit()
        vm.messages.first()
        val job = repo.get("id1")!!
        vm.saveToGallery(job)
        assertEquals(R.string.saved_to_gallery, (vm.messages.first() as UiMessage.Res).id)
        exporter.ok = false
        vm.saveToGallery(job)
        assertEquals(R.string.save_failed, (vm.messages.first() as UiMessage.Res).id)
        assertEquals(listOf("id1", "id1"), exporter.exported)
    }

    @Test
    fun jobsAreGroupedByDayWithFinishedVideosApart() {
        val day = 86_400_000L
        val jobs = listOf(
            Job(id = "a", url = "u", status = com.carlospinan.guardavideo.library.JobStatus.Done, createdAt = NOW - 1_000),
            Job(id = "b", url = "u", status = com.carlospinan.guardavideo.library.JobStatus.Running, createdAt = NOW - 2_000),
            Job(id = "c", url = "u", status = com.carlospinan.guardavideo.library.JobStatus.Done, createdAt = NOW - day),
            Job(id = "d", url = "u", status = com.carlospinan.guardavideo.library.JobStatus.Error, createdAt = NOW - 3 * day),
        )
        val groups = groupByDay(jobs, NOW, ZoneOffset.UTC)
        assertEquals(listOf<DayLabel>(DayLabel.Today, DayLabel.Yesterday), groups.take(2).map { it.day })
        assertEquals(listOf("b") to listOf("a"), groups[0].pending.map { it.id } to groups[0].finished.map { it.id })
        assertTrue(groups[2].day is DayLabel.Date)
        assertEquals(listOf("d"), groups[2].pending.map { it.id })
    }

    @Test
    fun searchShowsAPlainListOfMatchesAndClosingClearsIt() = runTest {
        repo.create("https://x.org/receta-de-pan.mp4", null)
        repo.create("https://x.org/paisaje.mp4", null)
        vm.openSearch()
        vm.onQuery("PAN")
        // The state is built off the main thread: wait for it rather than reading it at once.
        val state = vm.state.first { it.searching && it.total == 2 }
        assertEquals(listOf("receta-de-pan"), state.flat.map { it.displayTitle })
        assertTrue(state.groups.isEmpty())
        vm.closeSearch()
        assertTrue(!vm.searchOpen)
        assertTrue(!vm.state.first { !it.searching }.searching)
    }

    @Test
    fun openingTheLibraryReEnqueuesJobsThatClaimToBeRunning() = runTest {
        val stuck = (repo.create("https://x.org/stuck.mp4", null) as com.carlospinan.guardavideo.library.CreateResult.Created).job
        repo.update(stuck.id) { it.copy(status = com.carlospinan.guardavideo.library.JobStatus.Running) }
        queue.enqueued.clear()
        LibraryViewModel(repo, queue, exporter, LinkPolicy(sitePages = true), storage = object : StorageInfo { override fun freeBytes() = 5L }, now = { NOW })
        assertEquals(listOf(stuck.id), queue.enqueued)
    }

    private companion object {
        const val NOW = 1_790_000_000_000L + 12 * 3_600_000L // midday UTC
    }
}
