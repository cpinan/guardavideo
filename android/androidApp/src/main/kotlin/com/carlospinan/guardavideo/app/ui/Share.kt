package com.carlospinan.guardavideo.app.ui

import android.content.Context
import android.content.Intent
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.library.Job

/**
 * Shares the video's original link (not the file) through the system share sheet: WhatsApp,
 * Telegram, email… The title goes first so the message reads well. UI layer only: needs a Context.
 */
fun shareOriginalLink(context: Context, job: Job) {
    val text = if (job.title != null) "${job.title}\n${job.url}" else job.url
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(Intent.EXTRA_SUBJECT, job.displayTitle)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_link)))
}
