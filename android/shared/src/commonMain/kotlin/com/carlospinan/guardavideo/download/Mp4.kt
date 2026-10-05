package com.carlospinan.guardavideo.download

import okio.FileSystem
import okio.Path

private const val MAX_TOP_LEVEL_BOXES = 512

/**
 * True when an MP4 is stored as fragments (top-level "moof" boxes), as some sites deliver HLS in
 * fMP4 and yt-dlp concatenates it as is. Players and media readers then see only the first
 * fragment: wrong duration (7:30 for a 73-minute video), no thumbnail, slow seeking. Reads box
 * headers only, never the media.
 */
fun isFragmentedMp4(fs: FileSystem, path: Path): Boolean = runCatching {
    fs.read(path) {
        val size = fs.metadata(path).size ?: return@read false
        var pos = 0L
        repeat(MAX_TOP_LEVEL_BOXES) {
            if (pos + 8 > size) return@read false
            val boxSize = readInt().toLong() and 0xFFFFFFFFL
            val type = readUtf8(4)
            if (type == "moof") return@read true
            val length = when (boxSize) {
                1L -> readLong() // 64-bit size follows
                0L -> return@read false // box runs to the end of the file
                else -> boxSize
            }
            if (length < 8) return@read false
            val headerRead = if (boxSize == 1L) 16 else 8
            skip(length - headerRead)
            pos += length
        }
        false
    }
}.getOrDefault(false)
