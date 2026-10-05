package com.carlospinan.guardavideo.app

import com.carlospinan.guardavideo.app.ui.library.thumbnailFrameMs
import kotlin.test.Test
import kotlin.test.assertEquals

class ThumbnailFrameTest {
    @Test
    fun picksTenPercentClampedBetweenOneAndThirtySeconds() {
        assertEquals(30_000, thumbnailFrameMs(634_000)) // 10:34 video: 63 s, capped at 30 s
        assertEquals(12_000, thumbnailFrameMs(120_000)) // 2 min clip: 12 s in
        assertEquals(1_000, thumbnailFrameMs(5_000)) // short clip: at least 1 s
        assertEquals(2_000, thumbnailFrameMs(null)) // unknown duration
    }
}
