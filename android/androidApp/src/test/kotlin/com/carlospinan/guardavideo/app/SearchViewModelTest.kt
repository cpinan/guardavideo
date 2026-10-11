package com.carlospinan.guardavideo.app

import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.media.SearchHit
import com.carlospinan.guardavideo.app.media.SearchSite
import com.carlospinan.guardavideo.app.media.SiteSupport
import com.carlospinan.guardavideo.app.media.WebSearch
import com.carlospinan.guardavideo.app.ui.library.UiMessage
import com.carlospinan.guardavideo.app.ui.search.SearchViewModel
import com.carlospinan.guardavideo.app.ui.search.interleave
import com.carlospinan.guardavideo.download.VideoDownloader
import com.carlospinan.guardavideo.library.JobRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dm = SearchSite("dm", "Dailymotion", recommended = true)
    private val yt = SearchSite("yt", "YouTube")
    private val broken = mutableSetOf<String>()

    private val engine = object : WebSearch {
        override val sites = listOf(dm, yt)
        override suspend fun search(site: SearchSite, query: String): List<SearchHit> {
            if (site.id in broken) error("offline")
            return List(2) { SearchHit(site, "$query ${site.id}$it", "https://${site.id}.example/$it") }
        }
    }
    private val enqueued = mutableListOf<String>()
    private val queue = object : DownloadQueue {
        override fun enqueue(jobId: String) { enqueued += jobId }
        override fun cancel(jobId: String) {}
    }
    private var ids = 0
    private val repo = JobRepository(FakeJobDao(), FakeFileSystem(), clock = { 1 }, newId = { "id${++ids}" }, sitePages = true)
    private lateinit var vm: SearchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = SearchViewModel(
            object : SiteSupport {
                override val downloader: VideoDownloader? = null
                override val search = engine
            },
            repo,
            queue,
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theRecommendedSiteIsOnByDefault() {
        assertEquals(setOf(dm), vm.selected)
    }

    @Test
    fun theLastSiteCannotBeTurnedOff() {
        vm.toggle(dm)
        assertEquals(setOf(dm), vm.selected)
        vm.toggle(yt)
        vm.toggle(dm)
        assertEquals(setOf(yt), vm.selected)
    }

    @Test
    fun searchesOnlyTheChosenSites() = runTest {
        vm.onQuery("gatos")
        vm.search()
        assertEquals(listOf(dm), vm.state.hits.map { it.site }.distinct())
        vm.toggle(yt)
        vm.search()
        assertEquals(setOf(dm, yt), vm.state.hits.map { it.site }.toSet())
        assertEquals(dm, vm.state.hits.first().site) // alternates, recommended first
        assertEquals(yt, vm.state.hits[1].site)
    }

    @Test
    fun aBrokenSiteDoesNotHideTheOthers() = runTest {
        vm.toggle(yt)
        broken += "yt"
        vm.onQuery("gatos")
        vm.search()
        assertEquals(listOf(yt), vm.state.failedSites)
        assertEquals(2, vm.state.hits.size)
        assertFalse(vm.state.loading)
    }

    @Test
    fun aBlankQueryDoesNothing() {
        vm.onQuery("   ")
        vm.search()
        assertFalse(vm.state.searched)
    }

    @Test
    fun downloadingAHitQueuesItOnce() = runTest {
        vm.onQuery("gatos")
        vm.search()
        val hit = vm.state.hits.first()
        vm.download(hit)
        assertIs<UiMessage.Res>(vm.messages.first())
        assertEquals(1, enqueued.size)
        assertTrue(hit.url in vm.state.queued)
        vm.download(hit)
        assertEquals(R.string.already_downloading, (vm.messages.first() as UiMessage.Res).id)
        assertEquals(1, enqueued.size)
    }

    @Test
    fun interleaveAlternatesAndKeepsTheRest() {
        assertEquals(listOf(1, 10, 2, 20, 3), interleave(listOf(listOf(1, 2, 3), listOf(10, 20))))
    }
}
