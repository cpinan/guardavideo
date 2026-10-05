package com.carlospinan.guardavideo.app.media

import com.carlospinan.guardavideo.download.VideoDownloader
import java.io.File

/**
 * What differs between the two distributions (Gradle flavors):
 * - full (sideload, you and friends): yt-dlp downloads site pages and updates itself daily.
 * - play (Google Play): direct links only; no yt-dlp, Python or ffmpeg in the package.
 * Each flavor binds its own implementation in its FlavorModule.
 */
interface SiteSupport {
    /** Downloader for site pages (YouTube, Dailymotion…), or null where sites are not supported. */
    val downloader: VideoDownloader?

    /** Periodic upkeep at app start (yt-dlp's daily self-update in the full flavor). */
    suspend fun maintenance() {}
}

/**
 * Rewrites a fragmented MP4 into a regular one (index first, no re-encode). False when it could
 * not (or when this flavor has no tool for it: the file is then kept as it is).
 */
fun interface FragmentedMp4Fixer {
    suspend fun fix(input: File, output: File): Boolean
}
