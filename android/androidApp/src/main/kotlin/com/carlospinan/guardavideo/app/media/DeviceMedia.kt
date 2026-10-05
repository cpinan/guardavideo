package com.carlospinan.guardavideo.app.media

import android.media.MediaMetadataRetriever
import android.os.StatFs
import com.carlospinan.guardavideo.app.di.DownloadsDir
import com.carlospinan.guardavideo.library.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import javax.inject.Inject

/** Duration of a finished file, for the badge on thumbnails (the web gets it from ffprobe). */
class AndroidMediaProbe @Inject constructor() : MediaProbe {
    override suspend fun durationMs(file: Path): Long? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.toString())
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

/** Free space where videos are saved: the "· 12 GB libres" in the library summary. */
interface StorageInfo {
    fun freeBytes(): Long
}

class AndroidStorageInfo @Inject constructor(@DownloadsDir private val dir: Path) : StorageInfo {
    override fun freeBytes(): Long = runCatching {
        java.io.File(dir.toString()).mkdirs()
        StatFs(dir.toString()).availableBytes
    }.getOrDefault(0)
}
