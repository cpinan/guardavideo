package com.carlospinan.guardavideo.app.ui

import android.content.Context
import android.content.Intent
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.library.Job

/**
 * Shares the video's original link (not the file) through the system share sheet: WhatsApp,
 * Telegram, email… The title goes first so the message reads well. UI layer only: needs a Context.
 */
fun shareOriginalLink(context: Context, job: Job) = shareLink(context, job.displayTitle, job.url, job.title)

fun shareLink(context: Context, subject: String, url: String, title: String?) {
    val text = if (title != null) "$title\n$url" else url
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(Intent.EXTRA_SUBJECT, subject)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_link)))
}

/** Opens the original page (to check whether the video still plays on the site). */
fun openInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}

/** Copies a link to the clipboard. */
fun copyLink(context: Context, label: String, url: String) {
    context.getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText(label, url))
}
