import AVKit
import Shared
import SwiftUI

/// Native player (controls, AirPlay, Picture-in-Picture) that resumes where the user stopped and
/// saves the position every few seconds and on exit.
struct PlayerView: View {
    @EnvironmentObject private var library: LibraryModel
    let job: Shared.Job
    let fileURL: URL
    @State private var player = AVPlayer()
    @State private var observer: Any?

    var body: some View {
        PlayerController(player: player)
            .ignoresSafeArea()
            .navigationTitle(job.displayTitle)
            .navigationBarTitleDisplayMode(.inline)
            .onAppear(perform: start)
            .onDisappear(perform: stop)
    }

    /// A local playlist must go through the loopback server; plain video files play from disk.
    private var playableURL: URL {
        fileURL.pathExtension == "m3u8" ? (LocalMediaServer.shared.url(for: fileURL) ?? fileURL) : fileURL
    }

    private func start() {
        player.replaceCurrentItem(with: AVPlayerItem(url: playableURL))
        if job.positionMs > 0 {
            player.seek(to: CMTime(value: job.positionMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero)
        }
        player.play()
        observer = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 3, preferredTimescale: 1), queue: .main) { _ in
            save()
        }
    }

    private func stop() {
        save()
        if let observer { player.removeTimeObserver(observer) }
        player.pause()
    }

    private func save() {
        let position = player.currentTime().seconds
        let duration = player.currentItem?.duration.seconds ?? 0
        guard position.isFinite else { return }
        library.savePosition(job, positionMs: Int64(position * 1000), durationMs: duration.isFinite ? Int64(duration * 1000) : 0)
    }
}

private struct PlayerController: UIViewControllerRepresentable {
    let player: AVPlayer

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.allowsPictureInPicturePlayback = true
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {}
}
