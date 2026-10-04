package com.carlospinan.guardavideo.app.download

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.carlospinan.guardavideo.library.DownloadRunner
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.JobStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one download as a foreground service (type dataSync) so it survives leaving the app.
 * The work itself lives in the shared [DownloadRunner]; this only adds the notification.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: DownloadRunner,
    private val repository: JobRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = repository.get(id) ?: return Result.success() // deleted meanwhile
        setForeground(foregroundInfo(job))

        var lastShown = -1
        val status = runner.run(id) { updated ->
            val pct = updated.percent.toInt()
            if (updated.status == JobStatus.Running && pct != lastShown) {
                lastShown = pct
                notify(Notifications.idFor(updated), Notifications.progress(applicationContext, updated))
            }
        }
        val finished = repository.get(id)
        if (finished != null && status != null) {
            notify(Notifications.idFor(finished) + 1, Notifications.finished(applicationContext, finished, status == JobStatus.Done))
        }
        return Result.success() // failures are recorded on the job; WorkManager must not retry them
    }

    /** Called by WorkManager before doWork on API < 31 for expedited work; harmless otherwise. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(KEY_JOB_ID).orEmpty()
        return foregroundInfo(repository.get(id) ?: Job(id = id, url = id))
    }

    private fun foregroundInfo(job: Job): ForegroundInfo {
        val notification = Notifications.progress(applicationContext, job)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(Notifications.idFor(job), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifications.idFor(job), notification)
        }
    }

    // Without POST_NOTIFICATIONS (Android 13+) the download still runs; it is just silent.
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
    }
}
