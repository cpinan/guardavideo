package com.carlospinan.guardavideo.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where downloads get scheduled. An interface so ViewModels can be tested without WorkManager. */
interface DownloadQueue {
    fun enqueue(jobId: String)

    fun cancel(jobId: String)
}

/** One unique WorkManager job per download, named after the job id. */
@Singleton
class DownloadScheduler @Inject constructor(@ApplicationContext private val context: Context) : DownloadQueue {
    private val work get() = WorkManager.getInstance(context)

    override fun enqueue(jobId: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_JOB_ID to jobId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .build()
        // KEEP: tapping "download" twice or re-enqueueing on startup never runs the job twice.
        work.enqueueUniqueWork(name(jobId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(jobId: String) {
        work.cancelUniqueWork(name(jobId))
    }

    private fun name(jobId: String) = "download-$jobId"

    companion object {
        const val TAG = "download"
    }
}
