package com.carlospinan.guardavideo.download

import com.carlospinan.guardavideo.source.LinkSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaDownloaderTest {
    private val fs = FakeFileSystem().apply { createDirectories(DIR) }
    private val requests = mutableListOf<String>()

    @AfterTest
    fun noLeftovers() = fs.checkNoOpenFiles()

    private fun TestScope.downloader(
        decryptor: SegmentDecryptor = SegmentDecryptor { d, _, _ -> d },
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = MediaDownloader(
        http = HttpClient(MockEngine { req -> requests += req.url.toString(); handler(req) }),
        fs = fs,
        decryptor = decryptor,
        retry = RetryPolicy(attempts = 3, baseDelayMs = 1_000),
        workers = 2,
        io = StandardTestDispatcher(testScheduler), // backoff delays run on virtual time
    )

    private class Recorder : DownloadListener {
        val progress = mutableListOf<Float>()
        val retries = mutableListOf<Int>()
        val titles = mutableListOf<String>()
        override fun progress(percent: Float) { progress += percent }
        override fun retrying(attempt: Int) { retries += attempt }
        override fun title(title: String) { titles += title }
    }

    @Test
    fun directFileIsStreamedToDiskAndRenamedWhenComplete() = runTest {
        val body = ByteArray(200_000) { (it % 251).toByte() }
        val dl = downloader {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.size.toString()))
        }
        val rec = Recorder()
        val result = dl.download(LinkSource.DirectFile("https://x.org/Mi%20video.mp4", "mp4"), "j1", DIR, listener = rec)

        assertEquals(DIR / "j1.mp4", result.path)
        assertEquals("Mi video", result.title)
        assertEquals(body.size.toLong(), result.sizeBytes)
        assertContentEquals(body, fs.read(result.path) { readByteArray() })
        assertFalse(fs.exists(DIR / "j1.part"))
        assertEquals(100f, rec.progress.last())
    }

    @Test
    fun hlsFetchesTheChosenVariantAndWritesSegmentsInOrder() = runTest {
        val dl = downloader { req ->
            val url = req.url.toString()
            when {
                url.endsWith("master.m3u8") -> respond(
                    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=640x360\nlow.m3u8\n" +
                        "#EXT-X-STREAM-INF:BANDWIDTH=9,RESOLUTION=1280x720\nhigh.m3u8\n",
                )
                url.endsWith("high.m3u8") -> respond(
                    "#EXTM3U\n" + (0..4).joinToString("") { "#EXTINF:2,\ns$it.ts\n" } + "#EXT-X-ENDLIST\n",
                )
                else -> {
                    val n = url.substringAfterLast("/s").substringBefore(".ts").toInt()
                    delay((5 - n) * 10L) // later segments answer first: order must still hold
                    respond(byteArrayOf(n.toByte(), n.toByte()))
                }
            }
        }
        val rec = Recorder()
        val result = dl.download(LinkSource.Hls("https://h.example/v/master.m3u8"), "j2", DIR, listener = rec)

        assertEquals(DIR / "j2.ts", result.path)
        assertContentEquals(byteArrayOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4), fs.read(result.path) { readByteArray() })
        assertTrue(requests.none { it.endsWith("low.m3u8") })
        assertEquals(listOf(20f, 40f, 60f, 80f, 100f), rec.progress)
        assertEquals(listOf("v"), rec.titles) // reported before any segment: folder name, not "master"
        assertEquals((0..4).map { SegmentSpan(2.0, it * 2L, 2) }, result.segments)
    }

    @Test
    fun fmp4WritesTheInitSectionFirstAndProducesAnMp4() = runTest {
        val dl = downloader { req ->
            when (req.url.encodedPath.substringAfterLast('/')) {
                "media.m3u8" -> respond("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2,\na.m4s\n#EXTINF:2,\nb.m4s\n#EXT-X-ENDLIST")
                "init.mp4" -> respond(byteArrayOf(9))
                "a.m4s" -> respond(byteArrayOf(1))
                else -> respond(byteArrayOf(2))
            }
        }
        val result = dl.download(LinkSource.Hls("https://h.example/media.m3u8"), "j3", DIR)
        assertEquals(DIR / "j3.mp4", result.path)
        assertContentEquals(byteArrayOf(9, 1, 2), fs.read(result.path) { readByteArray() })
    }

    @Test
    fun aesSegmentsAreDecryptedWithTheirKeyAndIvAndTheKeyIsFetchedOnce() = runTest {
        val seen = mutableListOf<Pair<Byte, Byte>>()
        val decryptor = SegmentDecryptor { data, key, iv -> seen += key[0] to iv[15]; data.map { (it - 1).toByte() }.toByteArray() }
        val dl = downloader(decryptor) { req ->
            when (req.url.encodedPath.substringAfterLast('/')) {
                "media.m3u8" -> respond(
                    "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:3\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n" +
                        "#EXTINF:2,\na.ts\n#EXTINF:2,\nb.ts\n#EXTINF:2,\nc.ts\n#EXT-X-ENDLIST",
                )
                "k" -> respond(ByteArray(16) { 7 })
                else -> respond(byteArrayOf(11))
            }
        }
        val result = dl.download(LinkSource.Hls("https://h.example/media.m3u8"), "j4", DIR)
        assertContentEquals(byteArrayOf(10, 10, 10), fs.read(result.path) { readByteArray() })
        assertEquals(listOf<Pair<Byte, Byte>>(7.toByte() to 3, 7.toByte() to 4, 7.toByte() to 5), seen.sortedBy { it.second })
        assertEquals(1, requests.count { it.endsWith("/k") })
    }

    @Test
    fun serverErrorsAreRetriedWithBackoffAndRecoveryIsReported() = runTest {
        var calls = 0
        val dl = downloader {
            if (++calls < 3) respond("", HttpStatusCode.ServiceUnavailable) else respond(byteArrayOf(1, 2, 3))
        }
        val rec = Recorder()
        val start = testScheduler.currentTime
        dl.download(LinkSource.DirectFile("https://x.org/a.mp4", "mp4"), "j5", DIR, listener = rec)
        assertEquals(listOf(1, 2, 0), rec.retries)
        assertEquals(3_000, testScheduler.currentTime - start) // 1 s + 2 s
    }

    @Test
    fun notFoundFailsAtOnceWithAReadableMessage() = runTest {
        val dl = downloader { respond("", HttpStatusCode.NotFound) }
        val error = assertFailsWith<DownloadException> {
            dl.download(LinkSource.DirectFile("https://x.org/a.mp4", "mp4"), "j6", DIR)
        }
        assertEquals("El video ya no existe en el origen (404)", error.message)
        assertEquals(1, requests.size)
        assertFalse(fs.exists(DIR / "j6.part"))
    }

    @Test
    fun liveStreamsAndUnsupportedEncryptionAreRefused() = runTest {
        val live = downloader { respond("#EXTM3U\n#EXTINF:2,\na.ts\n") }
        val e1 = assertFailsWith<DownloadException> { live.download(LinkSource.Hls("https://h.example/l.m3u8"), "j7", DIR) }
        assertTrue(e1.message!!.contains("en vivo"))

        val sample = downloader { respond("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:2,\na.ts\n#EXT-X-ENDLIST") }
        val e2 = assertFailsWith<DownloadException> { sample.download(LinkSource.Hls("https://h.example/s.m3u8"), "j8", DIR) }
        assertTrue(e2.message!!.contains("SAMPLE-AES"))
    }

    @Test
    fun cancellingDeletesThePartialFile() = runTest {
        val never = CompletableDeferred<Unit>()
        val dl = downloader { req ->
            when (req.url.encodedPath.substringAfterLast('/')) {
                "media.m3u8" -> respond("#EXTM3U\n#EXTINF:2,\na.ts\n#EXTINF:2,\nb.ts\n#EXTINF:2,\nc.ts\n#EXT-X-ENDLIST")
                "c.ts" -> { never.await(); respond(byteArrayOf()) }
                else -> respond(byteArrayOf(1))
            }
        }
        val job = launch { dl.download(LinkSource.Hls("https://h.example/media.m3u8"), "j9", DIR) }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (requests.none { it.endsWith("c.ts") }) delay(5) }
        }
        job.cancelAndJoin()
        assertFalse(fs.exists(DIR / "j9.part"))
        assertTrue(fs.list(DIR).isEmpty())
    }

    @Test
    fun pageLinksAreNotDownloadedOnThePhone() = runTest {
        val dl = downloader { respond("") }
        assertFailsWith<DownloadException> { dl.download(LinkSource.SitePage("YouTube", "https://youtube.com/watch?v=1"), "j10", DIR) }
        assertTrue(requests.isEmpty())
    }

    companion object {
        val DIR = "/downloads".toPath()
    }
}
