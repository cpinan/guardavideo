package com.carlospinan.guardavideo.download

import com.carlospinan.guardavideo.source.LinkSource
import com.carlospinan.guardavideo.source.detectSource
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoutingDownloaderTest {
    private class Named(val name: String, val calls: MutableList<String>) : VideoDownloader {
        override suspend fun download(source: LinkSource, id: String, destDir: Path, quality: Int?, listener: DownloadListener): DownloadResult {
            calls += name
            return DownloadResult(destDir / "$id.mp4", null, 0)
        }
    }

    private val calls = mutableListOf<String>()
    private val dir = "/d".toPath()

    @Test
    fun directLinksUseTheBuiltInDownloaderAndPagesTheSiteOne() = runTest {
        val router = RoutingDownloader(Named("direct", calls), Named("site", calls))
        router.download(detectSource("https://x.org/a.m3u8"), "1", dir)
        router.download(detectSource("https://www.dailymotion.com/video/x4at2w3"), "2", dir)
        router.download(detectSource("https://example.org/page"), "3", dir)
        assertEquals(listOf("direct", "site", "site"), calls)
    }

    @Test
    fun withoutASiteDownloaderPagesFailAndDrmAlwaysFails() = runTest {
        val router = RoutingDownloader(Named("direct", calls), site = null)
        assertFailsWith<DownloadException> { router.download(detectSource("https://youtu.be/x"), "1", dir) }
        val withSite = RoutingDownloader(Named("direct", calls), Named("site", calls))
        assertFailsWith<DownloadException> { withSite.download(detectSource("https://www.netflix.com/watch/1"), "2", dir) }
        assertEquals(emptyList(), calls)
    }
}
