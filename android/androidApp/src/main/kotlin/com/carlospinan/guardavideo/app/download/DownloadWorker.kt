package com.carlospinan.guardavideo.app.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import android.util.Log
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
        try {
            setForeground(foregroundInfo(job))
        } catch (e: Exception) {
            // Android 12+ can refuse a foreground service when the work starts in the background.
            // Better a silent download than none: carry on without the notification.
            Log.w("DownloadWorker", "No foreground service for $id: ${e.javaClass.simpleName}")
        }

        // Redraw when the number, the title (yt-dlp learns it late), the picture or the retry note changes.
        var lastShown: List<Any?>? = null
        var lastAt = 0L
        val status = runner.run(id) { updated ->
            val shown = listOf(updated.percent.toInt(), updated.displayTitle, updated.thumbnailPath != null, updated.retries)
            // At most one redraw every 500 ms: Android sheds notification updates sent too fast,
            // and that could swallow the final "done" one.
            val now = android.os.SystemClock.elapsedRealtime()
            if (updated.status == JobStatus.Running && shown != lastShown && now - lastAt >= 500) {
                lastShown = shown
                lastAt = now
                Notifications.show(applicationContext, Notifications.idFor(updated), Notifications.progress(applicationContext, updated))
            }
        }
        val finished = repository.get(id)
        // A cancel is the user's own doing: no "failed" notification for it.
        if (finished != null && (status == JobStatus.Done || status == JobStatus.Error)) {
            Notifications.show(applicationContext, Notifications.finishedIdFor(finished), Notifications.finished(applicationContext, finished, status == JobStatus.Done))
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

    companion object {
        const val KEY_JOB_ID = "job_id"
    }
}
