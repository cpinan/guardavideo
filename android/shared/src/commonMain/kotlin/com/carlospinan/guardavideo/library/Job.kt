package com.carlospinan.guardavideo.library

import com.carlospinan.guardavideo.download.titleFrom

enum class JobStatus {
    Queued, Running, Done, Error, Cancelled;

    val isActive: Boolean get() = this == Queued || this == Running
    val isTerminal: Boolean get() = !isActive
}

/** A download and, once done, a video in the library. Times are epoch milliseconds. */
data class Job(
    val id: String,
    val url: String,
    val title: String? = null,
    val status: JobStatus = JobStatus.Queued,
    val percent: Float = 0f,
    val quality: Int? = null,
    val error: String? = null,
    val retries: Int = 0,
    val filePath: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = 0,
    val starred: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val watched: Boolean = false,
    val viewCount: Int = 0,
    val lastViewedAt: Long? = null,
    /** When the playback position last changed: orders "Seguir viendo". */
    val positionUpdatedAt: Long? = null,
    /** Last playback speed chosen for this video (1.0 = normal). */
    val playbackSpeed: Float = 1f,
    /** The site's own thumbnail (JPEG), known before the video finishes downloading. */
    val thumbnailPath: String? = null,
) {
    /** What the UI shows: the title, or the last part of the URL while it is unknown. */
    val displayTitle: String
        get() = title ?: titleFrom(url) ?: url
}
