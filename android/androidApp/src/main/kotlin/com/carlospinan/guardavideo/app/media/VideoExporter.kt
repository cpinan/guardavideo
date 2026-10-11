package com.carlospinan.guardavideo.app.media

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.carlospinan.guardavideo.app.download.Notifications
import com.carlospinan.guardavideo.library.Job
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Copies a finished video out of the app's private folder so other apps (Photos…) see it. */
interface VideoExporter {
    /** True when the copy is in the gallery. */
    suspend fun exportToGallery(job: Job): Boolean
}

/**
 * Writes into Movies/Guardavideo through MediaStore (Android 10+: no storage permission). The
 * entry stays IS_PENDING while copying, so a half-written video never shows up in the gallery.
 */
class MediaStoreExporter @Inject constructor(@ApplicationContext private val context: Context) : VideoExporter {

    // NonCancellable: leaving the screen (its ViewModel dies) must not abandon a half-copied video
    // with a "Guardando…" notification that never resolves; the copy runs to its end.
    override suspend fun exportToGallery(job: Job): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        val file = job.filePath?.let(::File)?.takeIf { it.exists() } ?: return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safeName(job.displayTitle)}.${file.extension}")
            put(MediaStore.Video.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Guardavideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@withContext fail(job)
        // A big video takes a while to copy: show a bar, then replace it with "saved" (or "failed").
        Notifications.show(context, Notifications.exportIdFor(job), Notifications.saving(context, job, 0))
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { copyWithProgress(it, out, file.length(), job) } }
                ?: error("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            Notifications.show(context, Notifications.exportIdFor(job), Notifications.savedToGallery(context, job, uri))
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            fail(job)
        }
    }

    private fun fail(job: Job): Boolean {
        Notifications.show(context, Notifications.exportIdFor(job), Notifications.saveFailed(context, job))
        return false
    }

    private fun copyWithProgress(input: java.io.InputStream, out: java.io.OutputStream, total: Long, job: Job) {
        val buffer = ByteArray(256 * 1024)
        var copied = 0L
        var shown = 0
        var lastAt = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            copied += n
            val pct = if (total > 0) (copied * 100 / total).toInt() else 0
            // Android sheds notification updates sent too fast (the final "saved" one too):
            // at most one every 500 ms, and only when the number changed.
            val now = android.os.SystemClock.elapsedRealtime()
            if (pct != shown && now - lastAt >= 500) {
                shown = pct
                lastAt = now
                Notifications.show(context, Notifications.exportIdFor(job), Notifications.saving(context, job, pct))
            }
        }
    }

    private fun safeName(title: String) = title.replace(Regex("""[\\/:*?"<>|]+"""), " ").trim().take(80).ifBlank { "video" }
}
