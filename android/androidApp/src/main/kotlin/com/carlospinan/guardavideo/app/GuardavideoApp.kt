package com.carlospinan.guardavideo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.carlospinan.guardavideo.app.di.ApplicationScope
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.download.Notifications
import com.carlospinan.guardavideo.library.JobRepository
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // WorkManager persists queued work across restarts; this re-enqueues anything the
        // library still calls active (KEEP: no duplicate work if it is already scheduled).
        appScope.launch { repository.active().forEach { queue.enqueue(it.id) } }
    }
}
