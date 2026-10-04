package com.carlospinan.guardavideo.hls

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaylistTest {
    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
        https://cdn2.example.com/720/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
        /abs/1080/index.m3u8
    """.trimIndent()

    @Test
    fun masterListsVariantsWithAbsoluteUris() {
        val playlist = assertIs<Playlist.Master>(parsePlaylist(master, "https://cdn.example.com/show/master.m3u8?t=9"))
        assertEquals(
            listOf(
                Variant("https://cdn.example.com/show/360/index.m3u8", 800_000, 360),
                Variant("https://cdn2.example.com/720/index.m3u8", 2_500_000, 720),
                Variant("https://cdn.example.com/abs/1080/index.m3u8", 5_000_000, 1080),
            ),
            playlist.variants,
        )
    }

    @Test
    fun pickVariantPrefersClosestHeightOrHighestBitrate() {
        val variants = (parsePlaylist(master, "https://c.example/m.m3u8") as Playlist.Master).variants
        assertEquals(720, pickVariant(variants, 700).height)
        assertEquals(1080, pickVariant(variants, null).height)
        assertEquals(360, pickVariant(variants, 240).height)
    }

    @Test
    fun mediaPlaylistCarriesKeysInitAndSequence() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            seg0.m4s
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/k1",IV=0x0000000000000000000000000000000A
            #EXTINF:4.5,
            seg1.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:2,
            seg2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val media = assertIs<Playlist.Media>(parsePlaylist(text, "https://h.example/v/1/media.m3u8"))
        assertEquals(7, media.mediaSequence)
        assertTrue(media.ended)
        assertEquals(listOf(4.0, 4.5, 2.0), media.segments.map { it.duration })
        assertEquals("https://h.example/v/1/seg1.m4s", media.segments[1].uri)
        assertEquals("https://h.example/v/1/init.mp4", media.segments[0].initUri)
        val key = media.segments[1].key!!
        assertEquals("https://h.example/v/keys/k1", key.uri)
        assertContentEquals(ByteArray(15) + byteArrayOf(10), key.iv)
        assertEquals(null, media.segments[2].key)
    }

    @Test
    fun liveListsAreFlaggedAndGarbageIsRejected() {
        val live = parsePlaylist("#EXTM3U\n#EXTINF:2,\na.ts", "https://h.example/l.m3u8")
        assertFalse((live as Playlist.Media).ended)
        assertFailsWith<PlaylistException> { parsePlaylist("<html>", "https://h.example/x.m3u8") }
    }

    @Test
    fun ivDefaultsToTheSequenceNumber() {
        val key = Key("AES-128", "https://k", null)
        val iv = segmentIv(key, mediaSequence = 255, index = 2)
        assertContentEquals(ByteArray(14) + byteArrayOf(1, 1), iv) // 257 = 0x0101
    }

    @Test
    fun quotedAttributesMayContainCommas() {
        assertEquals("a,b", attributes("""CODECS="a,b",BANDWIDTH=1""")["CODECS"])
    }
}
