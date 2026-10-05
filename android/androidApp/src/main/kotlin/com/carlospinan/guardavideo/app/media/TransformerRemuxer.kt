package com.carlospinan.guardavideo.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.carlospinan.guardavideo.download.DownloadResult
import com.carlospinan.guardavideo.download.isFragmentedMp4
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
 * Rewrites downloads into a regular MP4 with Media3 Transformer. No edits are requested, so the
 * samples are copied (transmuxed), not re-encoded: seconds, no quality loss.
 * - MPEG-TS (.ts): MP4 seeks precisely and galleries understand it; TS often does neither.
 * - Fragmented MP4 (moof boxes, e.g. Dailymotion via yt-dlp): readers see only the first
 *   fragment (a 73-minute video reads as 7:30, no thumbnail). Transformer cannot rewrite those
 *   ("Unexpected duration change"), so they go through [FragmentedMp4Fixer] (the bundled ffmpeg in the full flavor).
 */
class TransformerRemuxer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fs: FileSystem,
    private val fragmentedFixer: FragmentedMp4Fixer,
) : MediaPostProcessor {

    override suspend fun process(result: DownloadResult): Path {
        val file = result.path
        val ts = file.name.endsWith(".ts")
        if (!ts && !(file.name.endsWith(".mp4") && isFragmentedMp4(fs, file))) return file
        val stem = file.name.substringBeforeLast('.')
        val tmp = file.parent!! / "$stem.remux.part" // never mistaken for a finished file
        val out = file.parent!! / "$stem.mp4"
        try {
            if (ts) {
                remux(File(file.toString()), File(tmp.toString()))
            } else if (!fragmentedFixer.fix(File(file.toString()), File(tmp.toString()))) {
                return file // keep the download as it is; the repair retries on the next start
            }
            fs.atomicMove(tmp, out) // for a fragmented .mp4 this replaces it in place
            if (ts) fs.delete(file)
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
