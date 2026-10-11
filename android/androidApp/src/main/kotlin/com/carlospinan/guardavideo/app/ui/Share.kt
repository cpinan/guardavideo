package com.carlospinan.guardavideo.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.library.Job
import java.io.File

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

/**
 * Shares the video file itself (WhatsApp, Drive, Telegram…) through the share sheet, via our
 * FileProvider so the receiving app can read it without any storage permission.
 */
fun shareVideoFile(context: Context, job: Job): Boolean {
    val file = job.filePath?.let(::File)?.takeIf { it.exists() } ?: return false
    val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull() ?: return false
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "video/*"
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, job.displayTitle)
        .putExtra(Intent.EXTRA_TITLE, job.displayTitle)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(job.displayTitle, uri) }
    return runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.share_video_chooser))) }.isSuccess
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
