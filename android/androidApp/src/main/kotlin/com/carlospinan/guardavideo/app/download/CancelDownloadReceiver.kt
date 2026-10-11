package com.carlospinan.guardavideo.app.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.carlospinan.guardavideo.library.JobRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The notification's "Cancelar" button: same order as the card's (repository first, then the work). */
@AndroidEntryPoint
class CancelDownloadReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: JobRepository
    @Inject lateinit var queue: DownloadQueue

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repository.cancel(id)
                queue.cancel(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_JOB_ID = "job_id"

        fun pendingIntent(context: Context, jobId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            jobId.hashCode(),
            Intent(context, CancelDownloadReceiver::class.java).putExtra(EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
