package com.carlospinan.guardavideo.download

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleTest {
    @Test
    fun fileNamesBecomeTitles() {
        assertEquals("Mi video", titleFrom("https://x.org/media/Mi%20video.mp4?t=1"))
    }

    @Test
    fun genericPlaylistNamesFallBackToTheFolderThenTheHost() {
        assertEquals("concierto-2024", titleFrom("https://cdn.x.org/concierto-2024/master.m3u8"))
        assertEquals("cdn.x.org", titleFrom("https://cdn.x.org/hls/index.m3u8"))
        assertEquals("cdn.x.org", titleFrom("https://cdn.x.org/playlist.m3u8"))
    }
}
