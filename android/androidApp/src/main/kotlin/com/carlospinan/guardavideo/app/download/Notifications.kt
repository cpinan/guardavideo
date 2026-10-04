package com.carlospinan.guardavideo.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.carlospinan.guardavideo.app.MainActivity
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.library.Job

object Notifications {
    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_DONE = "downloads_done"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Low importance: a progress bar should not buzz on every update.
                NotificationChannel(CHANNEL_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Stable per job: the progress notification and its "done" replacement share an id. */
    fun idFor(job: Job): Int = job.id.hashCode()

    fun progress(context: Context, job: Job) = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setContentTitle(job.displayTitle)
        .setContentText(if (job.retries > 0) "Reconectando… intento ${job.retries}" else "${job.percent.toInt()} %")
        .setProgress(100, job.percent.toInt(), job.percent <= 0f)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp(context))
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()

    fun finished(context: Context, job: Job, ok: Boolean) = NotificationCompat.Builder(context, CHANNEL_DONE)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setContentTitle(if (ok) "Video listo" else "No se pudo descargar")
        .setContentText(if (ok) job.displayTitle else job.error ?: job.displayTitle)
        .setAutoCancel(true)
        .setContentIntent(openApp(context))
        .build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
