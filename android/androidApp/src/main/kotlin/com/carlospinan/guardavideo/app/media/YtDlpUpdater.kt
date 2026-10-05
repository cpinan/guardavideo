package com.carlospinan.guardavideo.app.media

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Sites change often and yt-dlp follows them: refresh it at most once a day (stable channel).
 * A failed update is harmless; the bundled version keeps working.
 */
class YtDlpUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: YtDlpSiteDownloader,
) {
    suspend fun updateIfStale() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_update", 0)
        if (System.currentTimeMillis() - last < DAY_MS) return@withContext
        runCatching {
            downloader.ensureReady()
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
