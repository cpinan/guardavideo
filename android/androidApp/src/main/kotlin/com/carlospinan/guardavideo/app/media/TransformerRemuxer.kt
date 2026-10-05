package com.carlospinan.guardavideo.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.carlospinan.guardavideo.download.DownloadResult
import com.carlospinan.guardavideo.library.MediaPostProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Turns a downloaded MPEG-TS (.ts) into MP4 with Media3 Transformer. No edits are requested, so
 * the samples are copied (transmuxed), not re-encoded: seconds, no quality loss. MP4 seeks
 * precisely and galleries understand it; TS often does neither.
 */
class TransformerRemuxer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fs: FileSystem,
) : MediaPostProcessor {

    override suspend fun process(result: DownloadResult): Path {
        val file = result.path
        if (!file.name.endsWith(".ts")) return file
        val stem = file.name.removeSuffix(".ts")
        val tmp = file.parent!! / "$stem.remux.part" // never mistaken for a finished file
        val out = file.parent!! / "$stem.mp4"
        try {
            remux(File(file.toString()), File(tmp.toString()))
            fs.atomicMove(tmp, out)
            fs.delete(file)
            return out
        } finally {
            fs.delete(tmp, mustExist = false)
        }
    }

    // Transformer must be created and driven on a thread with a Looper: the main one.
    private suspend fun remux(input: File, output: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        cont.resumeWithException(exportException)
                    }
                })
                .build()
            transformer.start(MediaItem.fromUri(Uri.fromFile(input)), output.absolutePath)
            cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { transformer.cancel() } }
        }
    }
}
