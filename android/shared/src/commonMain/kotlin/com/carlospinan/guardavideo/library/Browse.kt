package com.carlospinan.guardavideo.library

/**
 * Library views shared by every UI (ported from the web app's lib/shelves.ts): shelves, filters
 * and sorting. Pure functions over the job list, so they are tested once here.
 */
data class Shelves(
    /** Started but not finished, most recently watched first. */
    val continueWatching: List<Job>,
    val favorites: List<Job>,
    /** Played at least once, most played first. */
    val mostViewed: List<Job>,
    /** Finished in the last [RECENT_DAYS] days, newest first. */
    val recent: List<Job>,
) {
    val isEmpty: Boolean get() = continueWatching.isEmpty() && favorites.isEmpty() && mostViewed.isEmpty() && recent.isEmpty()
}

const val RECENT_DAYS = 7
private const val DAY_MS = 86_400_000L

/** With few videos the "most viewed" and "recent" shelves only repeat the list below them. */
const val MIN_FOR_AUTO_SHELVES = 4

/** How much was watched, 0..1 (0 without data or once finished). */
fun watchedFraction(job: Job): Float {
    val total = job.durationMs ?: return 0f
    if (job.watched || job.positionMs < 1_000 || total <= 0) return 0f
    return (job.positionMs.toFloat() / total).coerceIn(0f, 1f)
}

fun buildShelves(jobs: List<Job>, now: Long, limit: Int = 8): Shelves {
    val done = jobs.filter { it.status == JobStatus.Done }
    val enough = done.size >= MIN_FOR_AUTO_SHELVES
    return Shelves(
        continueWatching = done.filter { watchedFraction(it) in 0.0001f..0.98f }
            .sortedByDescending { it.positionUpdatedAt ?: 0 }.take(limit),
        favorites = done.filter { it.starred }.sortedByDescending { it.createdAt }.take(limit),
        mostViewed = if (!enough) emptyList() else done.filter { it.viewCount > 0 }
            .sortedWith(compareByDescending<Job> { it.viewCount }.thenByDescending { it.lastViewedAt ?: 0 }).take(limit),
        recent = if (!enough) emptyList() else done.filter { now - it.createdAt <= RECENT_DAYS * DAY_MS }
            .sortedByDescending { it.createdAt }.take(limit),
    )
}

enum class LibraryFilter(val test: (Job) -> Boolean) {
    All({ true }),
    Starred({ it.starred }),
    Active({ it.status.isActive }),
    Done({ it.status == JobStatus.Done }),
    Failed({ it.status == JobStatus.Error || it.status == JobStatus.Cancelled }),
}

enum class LibrarySort { Recent, Views, Name, Size }

fun sortJobs(jobs: List<Job>, sort: LibrarySort): List<Job> = when (sort) {
    LibrarySort.Recent -> jobs.sortedByDescending { it.createdAt }
    LibrarySort.Views -> jobs.sortedWith(compareByDescending<Job> { it.viewCount }.thenByDescending { it.lastViewedAt ?: 0 })
    LibrarySort.Name -> jobs.sortedBy { it.displayTitle.lowercase() }
    LibrarySort.Size -> jobs.sortedByDescending { it.sizeBytes ?: -1 }
}

/** Filter + search (title or URL, case-insensitive) + sort, as the list below the shelves shows it. */
fun browse(jobs: List<Job>, filter: LibraryFilter, query: String, sort: LibrarySort): List<Job> {
    val q = query.trim().lowercase()
    return sortJobs(jobs.filter { filter.test(it) && (q.isEmpty() || q in "${it.displayTitle} ${it.url}".lowercase()) }, sort)
}
