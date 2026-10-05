package com.carlospinan.guardavideo.app.media

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.carlospinan.guardavideo.library.Job
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    override suspend fun exportToGallery(job: Job): Boolean = withContext(Dispatchers.IO) {
        val file = job.filePath?.let(::File)?.takeIf { it.exists() } ?: return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safeName(job.displayTitle)}.${file.extension}")
            put(MediaStore.Video.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Guardavideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("no output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun safeName(title: String) = title.replace(Regex("""[\\/:*?"<>|]+"""), " ").trim().take(80).ifBlank { "video" }
}
