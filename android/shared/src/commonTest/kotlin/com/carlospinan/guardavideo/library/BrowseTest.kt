package com.carlospinan.guardavideo.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowseTest {
    private val now = 100 * 86_400_000L
    private fun done(id: String, vararg mods: (Job) -> Job): Job =
        mods.fold(Job(id = id, url = "https://x.org/$id.mp4", title = id, status = JobStatus.Done, createdAt = now - 1000, durationMs = 100_000)) { j, m -> m(j) }

    @Test
    fun continueWatchingKeepsHalfWatchedVideosMostRecentFirst() {
        val a = done("a", { it.copy(positionMs = 30_000, positionUpdatedAt = 1) })
        val b = done("b", { it.copy(positionMs = 50_000, positionUpdatedAt = 2) })
        val finished = done("c", { it.copy(watched = true) })
        val barely = done("d", { it.copy(positionMs = 500) })
        assertEquals(listOf("b", "a"), buildShelves(listOf(a, b, finished, barely), now).continueWatching.map { it.id })
    }

    @Test
    fun mostViewedAndRecentNeedEnoughVideos() {
        val few = listOf(done("a", { it.copy(viewCount = 3) }))
        assertTrue(buildShelves(few, now).mostViewed.isEmpty())
        val many = listOf(
            done("a", { it.copy(viewCount = 1, lastViewedAt = 5) }),
            done("b", { it.copy(viewCount = 3) }),
            done("c", { it.copy(viewCount = 1, lastViewedAt = 9) }),
            done("old", { it.copy(createdAt = now - 30 * 86_400_000L) }),
        )
        val shelves = buildShelves(many, now)
        assertEquals(listOf("b", "c", "a"), shelves.mostViewed.map { it.id })
        assertEquals(listOf("a", "b", "c"), shelves.recent.map { it.id }.sorted())
    }

    @Test
    fun browseFiltersSearchesAndSorts() {
        val jobs = listOf(
            done("Zeta", { it.copy(sizeBytes = 5, starred = true) }),
            done("alfa", { it.copy(sizeBytes = 9) }),
            Job(id = "run", url = "https://x.org/run.m3u8", status = JobStatus.Running),
            Job(id = "err", url = "https://x.org/e.mp4", status = JobStatus.Error),
        )
        assertEquals(listOf("alfa", "Zeta"), browse(jobs, LibraryFilter.Done, "", LibrarySort.Name).map { it.id })
        assertEquals(listOf("alfa", "Zeta"), browse(jobs, LibraryFilter.Done, "", LibrarySort.Size).map { it.id })
        assertEquals(listOf("Zeta"), browse(jobs, LibraryFilter.Starred, "", LibrarySort.Recent).map { it.id })
        assertEquals(listOf("run"), browse(jobs, LibraryFilter.Active, "", LibrarySort.Recent).map { it.id })
        assertEquals(listOf("err"), browse(jobs, LibraryFilter.Failed, "", LibrarySort.Recent).map { it.id })
        assertEquals(listOf("run"), browse(jobs, LibraryFilter.All, "RUN.M3U8", LibrarySort.Recent).map { it.id })
    }

    @Test
    fun watchedFractionIgnoresFinishedAndUnknown() {
        assertEquals(0.3f, watchedFraction(done("a", { it.copy(positionMs = 30_000) })))
        assertEquals(0f, watchedFraction(done("b", { it.copy(positionMs = 30_000, watched = true) })))
        assertEquals(0f, watchedFraction(done("c", { it.copy(positionMs = 30_000, durationMs = null) })))
    }
}
