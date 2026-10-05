package com.carlospinan.guardavideo.app.cast

import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Player
import com.carlospinan.guardavideo.app.media.ExoVideoPlayer
import com.carlospinan.guardavideo.player.PlayableMedia
import com.carlospinan.guardavideo.player.VideoPlayer
import com.carlospinan.guardavideo.player.VideoPlayerListener

/**
 * The [VideoPlayer] the player screen gets: plays here (ExoPlayer) or on a Chromecast
 * (CastPlayer), and moves the video, position, speed and play/pause state across when a Cast
 * session starts or ends. The shared PlaybackController never knows which one is active.
 */
class SwitchingVideoPlayer(
    private val local: ExoVideoPlayer,
    private val cast: CastVideoPlayer?,
) : VideoPlayer {
    private var current: VideoPlayer = local
    private var listener: VideoPlayerListener? = null
    private var media: PlayableMedia? = null

    /** The Media3 player the screen's PlayerView should show (it changes when casting starts). */
    val surface: Player get() = if (current === cast) cast.media3 else local.media3

    /** Only the active player's events reach the controller. */
    private fun relay(source: VideoPlayer) = object : VideoPlayerListener {
        private fun active() = current === source
        override fun onPlayingChanged(playing: Boolean) { if (active()) listener?.onPlayingChanged(playing) }
        override fun onVideoSize(width: Int, height: Int) { if (active()) listener?.onVideoSize(width, height) }
        override fun onEnded() { if (active()) listener?.onEnded() }
        override fun onError(message: String) { if (active()) listener?.onError(message) }
        override fun onSpeedChanged(speed: Float) { if (active()) listener?.onSpeedChanged(speed) }
        override fun onRemoteChanged(deviceName: String?) { if (active()) listener?.onRemoteChanged(deviceName) }
    }

    init {
        local.setListener(relay(local))
        cast?.setListener(relay(cast))
        cast?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = switchTo(cast)

            override fun onCastSessionUnavailable() = switchTo(local)
        })
        if (cast?.isSessionAvailable == true) current = cast // already casting when the screen opened
    }

    private fun switchTo(target: VideoPlayer) {
        if (target === current) return
        val from = current
        val position = from.positionMs
        val playing = from.isPlaying || media == null
        val rate = from.speed
        from.pause()
        current = target
        media?.let { target.load(it, position, playWhenReady = playing) }
        if (rate != 1f) target.setSpeed(rate)
        listener?.onRemoteChanged(if (target === cast) cast.deviceName ?: "Chromecast" else null)
    }

    override val positionMs: Long get() = current.positionMs
    override val durationMs: Long get() = current.durationMs
    override val isPlaying: Boolean get() = current.isPlaying
    override val speed: Float get() = current.speed

    override fun load(media: PlayableMedia, startAtMs: Long, playWhenReady: Boolean) {
        this.media = media
        current.load(media, startAtMs, playWhenReady)
        if (current === cast) listener?.onRemoteChanged(cast.deviceName ?: "Chromecast")
    }

    override fun play() = current.play()

    override fun pause() = current.pause()

    override fun seekTo(positionMs: Long) = current.seekTo(positionMs)

    override fun setSpeed(speed: Float) = current.setSpeed(speed)

    override fun setListener(listener: VideoPlayerListener?) {
        this.listener = listener
    }

    override fun dispose() {
        local.dispose()
        cast?.dispose()
    }
}
