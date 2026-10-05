import AVFoundation
import Shared

/// AVPlayer behind the shared `VideoPlayer` interface (the Android side uses Media3 ExoPlayer).
/// The shared PlaybackController drives it; the screen only shows `avPlayer` in an
/// AVPlayerViewController (the rendering surface stays native).
final class AvVideoPlayer: NSObject, VideoPlayer {
    /// For the video surface only; playback goes through this class.
    let avPlayer = AVPlayer()

    private weak var listener: VideoPlayerListener?
    private var observations: [NSKeyValueObservation] = []
    private var endObserver: NSObjectProtocol?

    override init() {
        super.init()
        observations.append(avPlayer.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            self?.listener?.onPlayingChanged(playing: player.timeControlStatus == .playing)
        })
        // Speed picked in the native controls: remembered per video by the shared controller.
        observations.append(avPlayer.observe(\.rate, options: [.new]) { [weak self] player, _ in
            if player.rate > 0 { self?.listener?.onSpeedChanged(speed: player.rate) }
        })
        // AirPlay is iOS's Cast: the shared state shows where the video is playing.
        observations.append(avPlayer.observe(\.isExternalPlaybackActive, options: [.new]) { [weak self] player, _ in
            self?.listener?.onRemoteChanged(deviceName: player.isExternalPlaybackActive ? "AirPlay" : nil)
        })
    }

    var positionMs: Int64 { ms(avPlayer.currentTime()) }
    var durationMs: Int64 { avPlayer.currentItem.map { ms($0.duration) } ?? 0 }
    var isPlaying: Bool { avPlayer.timeControlStatus == .playing }
    var speed: Float { avPlayer.defaultRate }

    func setSpeed(speed: Float) {
        avPlayer.defaultRate = speed // used by play()
        if avPlayer.rate > 0 { avPlayer.rate = speed }
    }

    func load(media: PlayableMedia, startAtMs: Int64, playWhenReady: Bool) {
        let file = URL(fileURLWithPath: media.path)
        // AVPlayer only plays HLS over HTTP: local playlists go through the loopback server.
        let url = media.kind == PlayableMedia.Kind.localhls ? (LocalMediaServer.shared.url(for: file) ?? file) : file
        let item = AVPlayerItem(url: url)
        observations.append(item.observe(\.presentationSize, options: [.new]) { [weak self] item, _ in
            self?.listener?.onVideoSize(width: Int32(item.presentationSize.width), height: Int32(item.presentationSize.height))
        })
        observations.append(item.observe(\.status, options: [.new]) { [weak self] item, _ in
            if item.status == .failed { self?.listener?.onError(message: item.error?.localizedDescription ?? "Error") }
        })
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.listener?.onEnded()
        }
        avPlayer.replaceCurrentItem(with: item)
        if startAtMs > 0 {
            avPlayer.seek(to: CMTime(value: startAtMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero)
        }
        if playWhenReady { avPlayer.play() }
    }

    func play() { avPlayer.play() }
    func pause() { avPlayer.pause() }
    func seekTo(positionMs: Int64) {
        avPlayer.seek(to: CMTime(value: positionMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func setListener(listener: VideoPlayerListener?) { self.listener = listener }

    func dispose() {
        avPlayer.pause()
        observations.removeAll()
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        avPlayer.replaceCurrentItem(with: nil)
    }

    private func ms(_ time: CMTime) -> Int64 {
        let seconds = time.seconds
        return seconds.isFinite ? Int64(seconds * 1000) : 0
    }
}
