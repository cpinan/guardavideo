package com.carlospinan.guardavideo.app.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.carlospinan.guardavideo.app.MainActivity
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.classifyFailure

object Notifications {
    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_DONE = "downloads_done"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Low importance: a progress bar should not buzz on every update.
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Stable per job: the progress notification and its "done" replacement share an id. */
    fun idFor(job: Job): Int = job.id.hashCode()

    /** "Done / failed" notification of a download. */
    fun finishedIdFor(job: Job): Int = idFor(job) + 1

    /** Gallery export (progress, then saved / failed) of a finished video. */
    fun exportIdFor(job: Job): Int = idFor(job) + 2

    /** Without POST_NOTIFICATIONS (Android 13+) everything still works; it is just silent. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    fun progress(context: Context, job: Job): Notification {
        val pct = job.percent.toInt()
        // No number yet (queued, or yt-dlp still resolving the page): a moving bar, not a stuck 0 %.
        val indeterminate = job.percent <= 0f
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(thumbnail(job))
            .setContentTitle(job.displayTitle)
            .setContentText(
                when {
                    job.retries > 0 -> context.getString(R.string.notif_reconnecting, job.retries)
                    indeterminate -> context.getString(R.string.notif_starting)
                    else -> context.getString(R.string.notif_percent, pct)
                },
            )
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notif_cancel), CancelDownloadReceiver.pendingIntent(context, job.id))
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun finished(context: Context, job: Job, ok: Boolean): Notification {
        val failure = classifyFailure(job.error)
        return NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(thumbnail(job))
            .setContentTitle(
                if (ok) context.getString(R.string.notif_done_title) else context.getString(R.string.notif_failed_title, failure.title.lowercase()),
            )
            .setContentText(job.displayTitle)
            .setStyle(
                if (ok) null else NotificationCompat.BigTextStyle()
                    .bigText("${job.displayTitle}\n${failure.explanation.ifBlank { job.error.orEmpty() }}"),
            )
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
    }

    /** Copy to the gallery in progress: [percent] null while the size is unknown. */
    fun saving(context: Context, job: Job, percent: Int?): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.notif_saving_title))
            .setContentText(job.displayTitle)
            .setProgress(100, percent ?: 0, percent == null)
            .setOnlyAlertOnce(true)
            // Not ongoing + a timeout: if the process dies mid-copy it can't stay stuck forever.
            .setTimeoutAfter(10 * 60 * 1000L)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    /** Saved: tapping it opens the copy in the gallery / video app. */
    fun savedToGallery(context: Context, job: Job, uri: Uri): Notification {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(thumbnail(job))
            .setContentTitle(context.getString(R.string.notif_saved_title))
            .setContentText(context.getString(R.string.notif_saved_text, job.displayTitle))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_saved_text, job.displayTitle)))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, exportIdFor(job), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .build()
    }

    fun saveFailed(context: Context, job: Job): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.notif_save_failed_title))
            .setContentText(job.displayTitle)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    /** The site's thumbnail as the notification's picture; decoded once per file, small. */
    private val thumbnails = android.util.LruCache<String, android.graphics.Bitmap>(4)

    private fun thumbnail(job: Job): android.graphics.Bitmap? {
        val path = job.thumbnailPath ?: return null
        thumbnails.get(path)?.let { return it }
        val bitmap = runCatching {
            android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
        }.getOrNull() ?: return null
        thumbnails.put(path, bitmap)
        return bitmap
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
