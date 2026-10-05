package com.carlospinan.guardavideo.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkSourceTest {
    @Test
    fun directMediaLinksAreDownloadable() {
        val hls = detectSource("https://cdn.example.com/a/index.m3u8?token=1")
        assertEquals(LinkSource.Hls("https://cdn.example.com/a/index.m3u8?token=1"), hls)
        assertEquals(LinkSource.DirectFile("https://x.org/clip.MP4", "mp4"), detectSource("https://x.org/clip.MP4"))
        assertTrue(hls.canDownload)
    }

    @Test
    fun sitePagesNeedTheServerAndDrmIsNeverDownloadable() {
        assertEquals(LinkSource.SitePage("YouTube", "https://m.youtube.com/watch?v=1"), detectSource("https://m.youtube.com/watch?v=1"))
        assertEquals("Instagram", (detectSource("https://www.instagram.com/p/1") as LinkSource.SitePage).provider)
        assertEquals(LinkSource.Drm("Netflix"), detectSource("https://www.netflix.com/watch/1"))
        assertFalse(detectSource("https://youtu.be/abc").canDownload)
    }

    @Test
    fun unknownPagesAndInvalidInput() {
        assertEquals(LinkSource.UnknownPage("notyoutube.com", "https://www.notyoutube.com/v"), detectSource("https://www.notyoutube.com/v"))
        assertEquals(LinkSource.Invalid, detectSource("youtube.com"))
        assertEquals(LinkSource.Invalid, detectSource("ftp://x.com/a.mp4"))
        assertEquals(LinkSource.Invalid, detectSource("javascript:alert(1)"))
        assertEquals(LinkSource.Invalid, detectSource(""))
    }

    @Test
    fun providerMatchingIsByWholeDomain() {
        assertEquals("YouTube", findProvider("youtube.com")?.name)
        assertNull(findProvider("youtube.com.evil.example"))
        val domains = PROVIDERS.flatMap { it.domains }
        assertEquals(domains.size, domains.toSet().size, "a domain belongs to one provider")
    }
}
