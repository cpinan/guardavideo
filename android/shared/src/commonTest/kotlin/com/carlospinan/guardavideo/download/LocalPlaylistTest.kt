package com.carlospinan.guardavideo.download

import com.carlospinan.guardavideo.hls.Playlist
import com.carlospinan.guardavideo.hls.parsePlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalPlaylistTest {
    @Test
    fun indexesEachSegmentByByteRangeInOneFile() {
        val text = localPlaylist(
            listOf(SegmentSpan(4.0, 0, 1000), SegmentSpan(4.5, 1000, 1200), SegmentSpan(2.25, 2200, 300)),
            "abc.ts",
        )
        assertTrue("#EXT-X-TARGETDURATION:5" in text)
        assertTrue("#EXTINF:4.500,\n#EXT-X-BYTERANGE:1200@1000\nabc.ts" in text)
        assertTrue(text.trimEnd().endsWith("#EXT-X-ENDLIST"))
        // and our own parser reads it back as a finished media playlist with three segments
        val media = parsePlaylist(text, "file:///videos/abc.m3u8") as Playlist.Media
        assertEquals(listOf(4.0, 4.5, 2.25), media.segments.map { it.duration })
        assertTrue(media.ended)
    }
}
