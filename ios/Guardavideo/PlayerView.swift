import AVKit
import Shared
import SwiftUI

/// Native surface (controls, AirPlay, Picture-in-Picture) over the shared playback rules:
/// `PlaybackController` resumes, saves the position and counts views; `AvVideoPlayer` adapts AVPlayer.
struct PlayerView: View {
    @EnvironmentObject private var library: LibraryModel
    let job: Shared.Job
    @StateObject private var session = PlayerSession()

    var body: some View {
        Group {
            if session.missing {
                Text("El archivo ya no está en el teléfono").foregroundStyle(.secondary)
            } else {
                PlayerController(player: session.player.avPlayer).ignoresSafeArea()
            }
        }
        .navigationTitle(job.displayTitle)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { session.start(job: job, library: library) }
        .onDisappear { session.stop() }
    }
}

/// Owns the adapter and the shared controller for one opening of the player.
@MainActor
final class PlayerSession: ObservableObject {
    let player = AvVideoPlayer()
    @Published var missing = false
    private var playback: IosPlayback?
    private var subscription: Subscription?

    func start(job: Shared.Job, library: LibraryModel) {
        guard playback == nil else { return }
        let playback = library.playback(player)
        subscription = playback.observe { [weak self] state in self?.missing = state.missing }
        playback.open(id: job.id)
        self.playback = playback
    }

    func stop() {
        subscription?.cancel()
        playback?.close()
        playback = nil
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
