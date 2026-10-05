package com.carlospinan.guardavideo.player

import com.carlospinan.guardavideo.library.CreateResult
import com.carlospinan.guardavideo.library.FakeJobDao
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.JobStatus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackControllerTest {
    /** Stand-in for ExoPlayer / AVPlayer: records calls, lets the test drive time and events. */
    private class FakePlayer : VideoPlayer {
        var loaded: Pair<PlayableMedia, Long>? = null
        var released = false
        var current: VideoPlayerListener? = null
        override var positionMs = 0L
        override var durationMs = 100_000L
        override var isPlaying = false
        private var rate = 1f
        override val speed: Float get() = rate
        val speedsSet = mutableListOf<Float>()

        override fun setSpeed(speed: Float) {
            rate = speed
            speedsSet += speed
        }

        override fun load(media: PlayableMedia, startAtMs: Long, playWhenReady: Boolean) {
            loaded = media to startAtMs
            positionMs = startAtMs
        }

        override fun play() = playing(true)

        override fun pause() = playing(false)

        fun playing(value: Boolean) {
            isPlaying = value
            current?.onPlayingChanged(value)
        }

        override fun seekTo(positionMs: Long) {
            this.positionMs = positionMs
        }

        override fun setListener(listener: VideoPlayerListener?) {
            current = listener
        }

        override fun dispose() {
            released = true
        }
    }

    private val player = FakePlayer()
    private val repo = JobRepository(FakeJobDao(), FakeFileSystem(), clock = { 1 }, newId = { "v1" })

    private suspend fun TestScope.video(file: String = "/v/v1.mp4", position: Long = 0): String {
        val job = assertIs<CreateResult.Created>(repo.create("https://x.org/a.mp4", null)).job
        repo.update(job.id) { it.copy(status = JobStatus.Done, filePath = file, positionMs = position) }
        return job.id
    }

    @Test
    fun resumesAtTheSavedPositionAndRecognisesLocalHls() = runTest {
        val id = video(file = "/v/v1.m3u8", position = 42_000)
        val controller = PlaybackController(player, repo, this, fileExists = { true })
        controller.open(id)
        assertEquals(PlayableMedia("/v/v1.m3u8", PlayableMedia.Kind.LocalHls, "a.mp4") to 42_000L, player.loaded)
        controller.close()
    }

    @Test
    fun countsOneViewPerOpeningAndSavesPeriodically() = runTest {
        val id = video()
        val controller = PlaybackController(player, repo, backgroundScope, fileExists = { true }, saveEveryMs = 3_000)
        controller.open(id)
        player.playing(true)
        player.playing(false)
        player.playing(true) // pausing and resuming is the same viewing
        runCurrent()
        assertEquals(1, repo.get(id)!!.viewCount)

        player.positionMs = 9_000
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(9_000, repo.get(id)!!.positionMs)
    }

    @Test
    fun closeSavesTheLastPositionAndReleasesThePlayer() = runTest {
        val id = video()
        val controller = PlaybackController(player, repo, backgroundScope, fileExists = { true })
        controller.open(id)
        player.positionMs = 55_000
        controller.close()
        runCurrent()
        assertEquals(55_000, repo.get(id)!!.positionMs)
        assertTrue(player.released)
    }

    @Test
    fun theSpeedIsRestoredOnOpenAndSavedWhenChanged() = runTest {
        val id = video()
        repo.saveSpeed(id, 1.5f)
        val controller = PlaybackController(player, repo, backgroundScope, fileExists = { true })
        controller.open(id)
        assertEquals(listOf(1.5f), player.speedsSet)
        assertEquals(1.5f, controller.state.value.speed)

        player.current!!.onSpeedChanged(2f) // picked in the native controls
        runCurrent()
        assertEquals(2f, repo.get(id)!!.playbackSpeed)
        repo.saveSpeed(id, 9f)
        assertEquals(3f, repo.get(id)!!.playbackSpeed) // clamped
    }

    @Test
    fun normalSpeedIsNotForcedAndRemotePlaybackIsReported() = runTest {
        val id = video()
        val controller = PlaybackController(player, repo, backgroundScope, fileExists = { true })
        controller.open(id)
        assertEquals(emptyList(), player.speedsSet)
        player.current!!.onRemoteChanged("Sala TV")
        assertEquals("Sala TV", controller.state.value.remoteDevice)
    }

    @Test
    fun aMissingFileIsReportedAndNothingIsLoaded() = runTest {
        val id = video()
        val controller = PlaybackController(player, repo, backgroundScope, fileExists = { false })
        controller.open(id)
        assertTrue(controller.state.value.missing)
        assertEquals(null, player.loaded)
    }
}
