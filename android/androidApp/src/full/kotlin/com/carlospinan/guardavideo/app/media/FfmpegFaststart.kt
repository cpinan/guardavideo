package com.carlospinan.guardavideo.app.media

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the ffmpeg that youtubedl-android already bundles: `-c copy -movflags +faststart`, the
 * same fix the Mac server applies. It turns a fragmented MP4 into a regular one without
 * re-encoding. Media3 Transformer cannot: it fails with "Unexpected duration change" because the
 * duration grows fragment by fragment.
 *
 * Layout (youtubedl-android 0.18): the executable ships as nativeLibraryDir/libffmpeg.so and its
 * libraries are unpacked under noBackupFilesDir/youtubedl-android/packages/{ffmpeg,python}/usr/lib.
 */
@Singleton
class FfmpegFaststart @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ytDlp: YtDlpSiteDownloader,
) {
    /** True when [output] now holds a regular MP4 copy of [input]. */
    suspend fun copy(input: File, output: File): Boolean {
        ytDlp.ensureReady() // unpacks ffmpeg's libraries on first use
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val process = ProcessBuilder(
            binary.path, "-y", "-loglevel", "error", "-i", input.path,
            // -f mp4: the temp name (.remux.part) says nothing about the format
            "-c", "copy", "-movflags", "+faststart", "-f", "mp4", output.path,
        ).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = "$packages/python/usr/lib:$packages/ffmpeg/usr/lib"
        }.start()
        return try {
            runInterruptible(Dispatchers.IO) {
                val log = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                if (code != 0) Log.w("Remux", "ffmpeg exited $code: ${log.takeLast(500)}")
                code == 0 && output.length() > 0
            }
        } finally {
            if (process.isAlive) process.destroy() // cancelled: stop ffmpeg too
        }
    }
}
