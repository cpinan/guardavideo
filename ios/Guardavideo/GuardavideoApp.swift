import AVFoundation
import SwiftUI

@main
struct GuardavideoApp: App {
    @StateObject private var library = LibraryModel()

    init() {
        // Playback category: sound with the silent switch on, and Picture-in-Picture.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        // Local HLS (TS downloads) is played through the loopback server; see LocalMediaServer.
        let videos = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("Videos")
        try? FileManager.default.createDirectory(at: videos, withIntermediateDirectories: true)
        try? LocalMediaServer.shared.start(root: videos)
    }

    var body: some Scene {
        WindowGroup {
            LibraryView()
                .environmentObject(library)
                .onOpenURL { url in Task { await library.open(url) } }
        }
    }
}
