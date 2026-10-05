package com.carlospinan.guardavideo.download

import kotlin.math.ceil
import kotlin.math.max

/**
 * A VOD playlist that points into one local .ts file with EXT-X-BYTERANGE (HLS v4). Players that
 * cannot open a bare MPEG-TS file (AVPlayer) play this instead, and seek segment by segment.
 */
fun localPlaylist(segments: List<SegmentSpan>, mediaFileName: String): String = buildString {
    val target = ceil(segments.maxOfOrNull { it.durationSec } ?: 1.0).toInt()
    appendLine("#EXTM3U")
    appendLine("#EXT-X-VERSION:4")
    appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
    appendLine("#EXT-X-TARGETDURATION:${max(target, 1)}")
    appendLine("#EXT-X-MEDIA-SEQUENCE:0")
    for (s in segments) {
        appendLine("#EXTINF:${formatSeconds(s.durationSec)},")
        appendLine("#EXT-X-BYTERANGE:${s.length}@${s.offset}")
        appendLine(mediaFileName)
    }
    appendLine("#EXT-X-ENDLIST")
}

/** Three decimals without platform formatting (common code has no String.format). */
private fun formatSeconds(seconds: Double): String {
    val millis = kotlin.math.round(seconds * 1000).toLong()
    return "${millis / 1000}.${(millis % 1000).toString().padStart(3, '0')}"
}
