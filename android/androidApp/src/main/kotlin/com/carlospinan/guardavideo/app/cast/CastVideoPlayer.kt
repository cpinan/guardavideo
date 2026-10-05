package com.carlospinan.guardavideo.app.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.carlospinan.guardavideo.player.PlayableMedia
import com.carlospinan.guardavideo.player.VideoPlayer
import com.carlospinan.guardavideo.player.VideoPlayerListener
import com.google.android.gms.cast.framework.CastContext
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Media3 CastPlayer behind the shared [VideoPlayer]: the Chromecast plays the file that
 * [LanMediaServer] exposes on the Wi-Fi network. Same interface as the local player, so the
 * shared PlaybackController (resume, position saving, speed) works while casting.
 */
class CastVideoPlayer(
    private val context: Context,
    val castContext: CastContext,
    private val server: LanMediaServer,
) : VideoPlayer {
    val media3: CastPlayer = CastPlayer(castContext)
    private var listener: VideoPlayerListener? = null

    private val events = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onPlayingChanged(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) listener?.onEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(error.errorCodeName)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            listener?.onSpeedChanged(playbackParameters.speed)
        }
    }

    init {
        media3.addListener(events)
    }

    val isSessionAvailable: Boolean get() = media3.isCastSessionAvailable

    val deviceName: String? get() = castContext.sessionManager.currentCastSession?.castDevice?.friendlyName

    fun setSessionAvailabilityListener(listener: SessionAvailabilityListener?) = media3.setSessionAvailabilityListener(listener)

    override val positionMs: Long get() = media3.currentPosition
    override val durationMs: Long get() = media3.duration.takeIf { it > 0 } ?: 0
    override val isPlaying: Boolean get() = media3.isPlaying
    override val speed: Float get() = media3.playbackParameters.speed

    override fun load(media: PlayableMedia, startAtMs: Long, playWhenReady: Boolean) {
        val address = wifiAddress() ?: run {
            listener?.onError("NO_WIFI")
            return
        }
        val url = server.serve(File(media.path), address)
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (media.path.endsWith(".webm")) MimeTypes.VIDEO_WEBM else MimeTypes.VIDEO_MP4)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(media.title).build())
            .build()
        media3.setMediaItem(item, startAtMs)
        media3.prepare()
        media3.playWhenReady = playWhenReady
    }

    override fun play() = media3.play()

    override fun pause() = media3.pause()

    override fun seekTo(positionMs: Long) = media3.seekTo(positionMs)

    override fun setSpeed(speed: Float) = media3.setPlaybackSpeed(speed)

    override fun setListener(listener: VideoPlayerListener?) {
        this.listener = listener
    }

    override fun dispose() {
        media3.setSessionAvailabilityListener(null)
        media3.removeListener(events)
        media3.release()
        server.stop()
    }

    /** IPv4 of the Wi-Fi (or Ethernet) network: what the Chromecast can reach. */
    private fun wifiAddress(): InetAddress? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        } ?: return null
        return cm.getLinkProperties(network)?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }
}
