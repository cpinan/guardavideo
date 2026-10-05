package com.carlospinan.guardavideo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.carlospinan.guardavideo.app.di.ApplicationScope
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.media.SiteSupport
import com.carlospinan.guardavideo.app.download.Notifications
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.MediaProbe
import com.carlospinan.guardavideo.library.MediaPostProcessor
import com.carlospinan.guardavideo.library.backfillDurations
import com.carlospinan.guardavideo.library.repairFragmentedVideos
import okio.FileSystem
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GuardavideoApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var repository: JobRepository
    @Inject lateinit var queue: DownloadQueue
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var siteSupport: SiteSupport
    @Inject lateinit var probe: MediaProbe
    @Inject lateinit var postProcessor: MediaPostProcessor
    @Inject lateinit var fs: FileSystem

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // WorkManager persists queued work across restarts; this re-enqueues anything the
        // library still calls active (KEEP: no duplicate work if it is already scheduled).
        appScope.launch { repository.active().forEach { queue.enqueue(it.id) } }
        appScope.launch { siteSupport.maintenance() } // full flavor: yt-dlp's daily self-update
        appScope.launch { repository.seedHistoryIfEmpty() } // first run with history: record what exists
        appScope.launch {
            // Old fragmented MP4s first (they also get their real length), then any missing durations.
            repairFragmentedVideos(repository, fs, postProcessor, probe)
            backfillDurations(repository, probe)
        }
    }
}
