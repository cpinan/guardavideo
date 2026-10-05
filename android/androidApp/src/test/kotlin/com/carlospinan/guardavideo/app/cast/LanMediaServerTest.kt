package com.carlospinan.guardavideo.app.cast

import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanMediaServerTest {
    private val server = LanMediaServer()
    private val video = File.createTempFile("clip", ".mp4").apply { writeBytes(ByteArray(1000) { (it % 251).toByte() }) }
    private val loopback = InetAddress.getByName("127.0.0.1")

    @AfterTest
    fun tearDown() {
        server.stop()
        video.delete()
    }

    private fun get(url: String, range: String? = null): Pair<HttpURLConnection, ByteArray> {
        val conn = URL(url).openConnection() as HttpURLConnection
        range?.let { conn.setRequestProperty("Range", it) }
        val body = runCatching { conn.inputStream.readBytes() }.getOrDefault(ByteArray(0))
        return conn to body
    }

    @Test
    fun servesTheWholeFileAndRangesForSeeking() {
        val url = server.serve(video, loopback)
        assertTrue(url.matches(Regex("http://127\\.0\\.0\\.1:\\d+/[0-9a-f]{32}/clip.*\\.mp4")))

        val (whole, all) = get(url)
        assertEquals(200, whole.responseCode)
        assertEquals("video/mp4", whole.contentType)
        assertContentEquals(video.readBytes(), all)

        val (part, bytes) = get(url, "bytes=100-199")
        assertEquals(206, part.responseCode)
        assertEquals("bytes 100-199/1000", part.getHeaderField("Content-Range"))
        assertContentEquals(video.readBytes().copyOfRange(100, 200), bytes)

        val (tail, last) = get(url, "bytes=-10")
        assertEquals(206, tail.responseCode)
        assertContentEquals(video.readBytes().copyOfRange(990, 1000), last)
    }

    @Test
    fun anythingButTheRegisteredFileIs404() {
        val url = server.serve(video, loopback)
        val base = url.substringBeforeLast('/')
        assertEquals(404, get("$base/other.mp4").first.responseCode)
        assertEquals(404, get(url.replace(Regex("/[0-9a-f]{32}/"), "/0000/")).first.responseCode)
        assertEquals(416, get(url, "bytes=5000-6000").first.responseCode)
    }
}
