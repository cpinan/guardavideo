package com.carlospinan.guardavideo.download

import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Mp4Test {
    private val fs = FakeFileSystem()

    private fun box(type: String, payload: Int = 4) = Buffer().writeInt(8 + payload).writeUtf8(type).write(ByteArray(payload))

    private fun file(name: String, vararg boxes: Buffer) = name.toPath().also { p ->
        fs.write(p) { boxes.forEach { writeAll(it) } }
    }

    @Test
    fun regularMp4IsNotFragmented() {
        assertFalse(isFragmentedMp4(fs, file("/a.mp4", box("ftyp"), box("moov", 20), box("free"), box("mdat", 100))))
    }

    @Test
    fun fragmentsAfterTheMoovAreDetected() {
        // What Dailymotion's fMP4 looks like after yt-dlp: ftyp moov styp sidx sidx moof mdat …
        assertTrue(isFragmentedMp4(fs, file("/b.mp4", box("ftyp"), box("moov", 20), box("styp"), box("sidx"), box("sidx"), box("moof"), box("mdat", 50))))
    }

    @Test
    fun largeBoxesAndGarbageAreHandled() {
        val big = Buffer().writeInt(1).writeUtf8("mdat").writeLong(16 + 8).write(ByteArray(8))
        assertTrue(isFragmentedMp4(fs, file("/c.mp4", box("ftyp"), big, box("moof"))))
        assertFalse(isFragmentedMp4(fs, file("/d.mp4", Buffer().writeUtf8("not a video"))))
        assertFalse(isFragmentedMp4(fs, "/missing.mp4".toPath()))
    }
}
