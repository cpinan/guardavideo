package com.carlospinan.guardavideo.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.carlospinan.guardavideo.player.PlayableMedia
import com.carlospinan.guardavideo.player.VideoPlayer
import com.carlospinan.guardavideo.player.VideoPlayerListener
import java.io.File

/**
 * Media3 ExoPlayer behind the shared [VideoPlayer] interface. The shared PlaybackController
 * drives it; the screen only binds [media3] to a PlayerView (the rendering surface stays native).
 * iOS implements the same interface with AVPlayer.
 */
class ExoVideoPlayer(context: Context) : VideoPlayer {
    /** For the video surface (PlayerView) only; playback goes through this class. */
    val media3: ExoPlayer = ExoPlayer.Builder(context).build()

    private var listener: VideoPlayerListener? = null

    private val events = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onPlayingChanged(isPlaying)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            listener?.onVideoSize(videoSize.width, videoSize.height)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) listener?.onEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(error.errorCodeName)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            listener?.onSpeedChanged(playbackParameters.speed) // chosen in PlayerView's ⚙ menu
        }
    }

    init {
        media3.addListener(events)
    }

    override val positionMs: Long get() = media3.currentPosition
    override val durationMs: Long get() = media3.duration.takeIf { it > 0 } ?: 0
    override val isPlaying: Boolean get() = media3.isPlaying
    override val speed: Float get() = media3.playbackParameters.speed

    override fun setSpeed(speed: Float) = media3.setPlaybackSpeed(speed)

    override fun load(media: PlayableMedia, startAtMs: Long, playWhenReady: Boolean) {
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(File(media.path)))
            // A local byte-range playlist: tell Media3 it is HLS (it plays it straight from disk).
            .apply { if (media.kind == PlayableMedia.Kind.LocalHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        media3.setMediaItem(item, startAtMs)
        media3.prepare()
        media3.playWhenReady = playWhenReady
    }

    override fun play() = media3.play()

    override fun pause() = media3.pause()

    override fun seekTo(positionMs: Long) = media3.seekTo(positionMs)

    override fun setListener(listener: VideoPlayerListener?) {
        this.listener = listener
    }

    override fun dispose() {
        media3.removeListener(events)
        media3.release()
    }
}
