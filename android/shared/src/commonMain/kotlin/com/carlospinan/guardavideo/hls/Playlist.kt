package com.carlospinan.guardavideo.hls

import kotlin.math.abs

/** A rendition listed in a master playlist. */
data class Variant(val uri: String, val bandwidth: Long, val height: Int?)

/** `#EXT-X-KEY`. `iv` is null when the playlist does not give one (derive it per segment). */
data class Key(val method: String, val uri: String?, val iv: ByteArray?) {
    override fun equals(other: Any?): Boolean =
        other is Key && method == other.method && uri == other.uri && iv.contentEquals(other.iv)

    override fun hashCode(): Int = 31 * (31 * method.hashCode() + (uri?.hashCode() ?: 0)) + iv.contentHashCode()
}

data class Segment(val uri: String, val duration: Double, val key: Key?, val initUri: String?)

sealed interface Playlist {
    data class Master(val variants: List<Variant>) : Playlist

    data class Media(val segments: List<Segment>, val mediaSequence: Long, val ended: Boolean) : Playlist
}

class PlaylistException(message: String) : Exception(message)

/** Parses an M3U8 playlist; every URI comes back absolute, resolved against [baseUrl]. */
fun parsePlaylist(text: String, baseUrl: String): Playlist {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.firstOrNull() != "#EXTM3U") throw PlaylistException("No es una lista HLS (#EXTM3U)")

    if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
        val variants = mutableListOf<Variant>()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@forEachIndexed
            val attrs = attributes(line.substringAfter(':'))
            val uri = lines.getOrNull(i + 1)?.takeUnless { it.startsWith("#") } ?: return@forEachIndexed
            val height = attrs["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull()
            variants += Variant(resolveUrl(baseUrl, uri), attrs["BANDWIDTH"]?.toLongOrNull() ?: 0, height)
        }
        if (variants.isEmpty()) throw PlaylistException("La lista maestra no tiene variantes")
        return Playlist.Master(variants)
    }

    var mediaSequence = 0L
    var ended = false
    var key: Key? = null
    var initUri: String? = null
    var duration = 0.0
    val segments = mutableListOf<Segment>()
    for (line in lines) {
        when {
            line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> mediaSequence = line.substringAfter(':').toLongOrNull() ?: 0
            line == "#EXT-X-ENDLIST" -> ended = true
            line.startsWith("#EXT-X-KEY:") -> key = parseKey(attributes(line.substringAfter(':')), baseUrl)
            line.startsWith("#EXT-X-MAP:") -> initUri = attributes(line.substringAfter(':'))["URI"]?.let { resolveUrl(baseUrl, it) }
            line.startsWith("#EXTINF:") -> duration = line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0
            !line.startsWith("#") -> {
                segments += Segment(resolveUrl(baseUrl, line), duration, key, initUri)
                duration = 0.0
            }
        }
    }
    return Playlist.Media(segments, mediaSequence, ended)
}

private fun parseKey(attrs: Map<String, String>, baseUrl: String): Key? {
    val method = attrs["METHOD"] ?: return null
    if (method == "NONE") return null
    val iv = attrs["IV"]?.let { hexToBytes(it.removePrefix("0x").removePrefix("0X")) }
    return Key(method, attrs["URI"]?.let { resolveUrl(baseUrl, it) }, iv)
}

/** `NAME=value,NAME="quoted, with commas"` → map, quotes removed. */
internal fun attributes(list: String): Map<String, String> =
    ATTRIBUTE.findAll(list).associate { m -> m.groupValues[1] to m.groupValues[2].removeSurrounding("\"") }

private val ATTRIBUTE = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

/** The rendition closest to [quality] (height in pixels), or the highest bitrate. */
fun pickVariant(variants: List<Variant>, quality: Int?): Variant =
    if (quality != null && quality > 0) {
        variants.minBy { abs((it.height ?: 0) - quality) }
    } else {
        variants.maxBy { it.bandwidth }
    }

/** IV for segment [index]: the playlist's, or the media sequence number as 16 big-endian bytes. */
fun segmentIv(key: Key, mediaSequence: Long, index: Int): ByteArray =
    key.iv ?: ByteArray(16).also { iv ->
        var n = mediaSequence + index
        for (i in 15 downTo 8) {
            iv[i] = (n and 0xFF).toByte()
            n = n shr 8
        }
    }

internal fun hexToBytes(hex: String): ByteArray {
    val clean = if (hex.length % 2 == 1) "0$hex" else hex
    val bytes = ByteArray(clean.length / 2) { i -> clean.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    // HLS IVs are 128 bits: left-pad shorter values.
    return if (bytes.size >= 16) bytes.copyOfRange(bytes.size - 16, bytes.size) else ByteArray(16 - bytes.size) + bytes
}

/** Resolves [ref] against [base] like a browser (absolute, protocol-relative, root and relative paths). */
fun resolveUrl(base: String, ref: String): String {
    if (ref.contains("://")) return ref
    val scheme = base.substringBefore("://")
    val afterScheme = base.substringAfter("://")
    val authority = afterScheme.substringBefore('/').substringBefore('?')
    if (ref.startsWith("//")) return "$scheme:$ref"
    if (ref.startsWith("/")) return "$scheme://$authority$ref"
    val basePath = afterScheme.removePrefix(authority).substringBefore('?').substringBefore('#')
    val dir = basePath.substringBeforeLast('/', missingDelimiterValue = "")
    val query = ref.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) "" else ref.substring(it) }
    val parts = ("$dir/${ref.removeSuffix(query)}").split('/')
    val out = ArrayDeque<String>()
    for (part in parts) {
        when (part) {
            "", "." -> Unit
            ".." -> out.removeLastOrNull()
            else -> out.addLast(part)
        }
    }
    return "$scheme://$authority/${out.joinToString("/")}$query"
}
