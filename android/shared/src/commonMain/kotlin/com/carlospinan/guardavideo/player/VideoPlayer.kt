package com.carlospinan.guardavideo.player

/**
 * What the shared playback logic needs from a platform player. Android implements it with Media3
 * ExoPlayer, iOS with AVPlayer; only the video surface stays native (PlayerView / AVPlayerLayer).
 *
 * Deliberately callback-based (no Flow, no suspend): Swift classes can implement it directly.
 */
interface VideoPlayer {
    /** Current position and length in milliseconds; duration is 0 while unknown. */
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean

    /** Playback rate, 1.0 = normal. */
    val speed: Float

    fun setSpeed(speed: Float)

    fun load(media: PlayableMedia, startAtMs: Long, playWhenReady: Boolean)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setListener(listener: VideoPlayerListener?)

    /** Frees the player. Not called `release`: that name is reserved by NSObject on iOS. */
    fun dispose()
}

/** Events the platform player reports back (always on the main thread). */
interface VideoPlayerListener {
    fun onPlayingChanged(playing: Boolean)

    fun onVideoSize(width: Int, height: Int)

    fun onEnded()

    fun onError(message: String)

    /** The user picked another speed in the native controls. */
    fun onSpeedChanged(speed: Float)

    /** Playback moved to / from another device (Google Cast, AirPlay); [deviceName] when remote. */
    fun onRemoteChanged(deviceName: String?)
}

/**
 * A local video to play. [LocalHls] is a playlist that indexes a .ts by byte range: Media3 plays
 * it from disk, AVPlayer needs it over HTTP (the iOS app serves it on loopback).
 */
data class PlayableMedia(val path: String, val kind: Kind, val title: String = "") {
    enum class Kind { File, LocalHls }

    companion object {
        fun forFile(path: String, title: String = "") =
            PlayableMedia(path, if (path.endsWith(".m3u8")) Kind.LocalHls else Kind.File, title)
    }
}
