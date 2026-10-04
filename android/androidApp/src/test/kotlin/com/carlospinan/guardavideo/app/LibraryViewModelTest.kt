package com.carlospinan.guardavideo.app

import com.carlospinan.guardavideo.app.download.DownloadQueue
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
    private var ids = 0
    private val repo = JobRepository(FakeJobDao(), FakeFileSystem(), clock = { 1 }, newId = { "id${++ids}" })
    private lateinit var vm: LibraryViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = LibraryViewModel(repo, queue)
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
    fun pageLinksCannotBeSubmitted() = runTest {
        vm.onUrlChange("https://www.youtube.com/watch?v=1")
        assertTrue(!vm.source.canDownload)
        vm.submit()
        assertTrue(queue.enqueued.isEmpty())
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
}
