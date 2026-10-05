import Shared
import SwiftUI
import UIKit

/// Bridges the shared Kotlin library to SwiftUI. All logic lives in Kotlin (GuardavideoIos);
/// this only publishes state and keeps the app alive briefly in the background while downloading.
@MainActor
final class LibraryModel: ObservableObject {
    @Published var jobs: [Shared.Job] = []
    @Published var url = ""
    @Published var quality: Int32 = 0
    @Published var message: String?
    @Published var submitting = false

    private let core = GuardavideoIos()
    private var subscription: Subscription?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    init() {
        subscription = core.observeJobs { [weak self] jobs in
            self?.jobs = jobs
            self?.updateBackgroundTask()
        }
        core.resumePending()
        #if DEBUG
        // Test hook: `simctl launch … -deeplink guardavideo://download?url=…` runs the deep-link
        // path without the system's "Open in…" confirmation. Not compiled into release builds.
        if let i = CommandLine.arguments.firstIndex(of: "-deeplink"), i + 1 < CommandLine.arguments.count,
           let link = URL(string: CommandLine.arguments[i + 1]) {
            Task { await open(link) }
        }
        #endif
    }

    deinit { subscription?.cancel() }

    var link: LinkInfo { core.describe(url: url) }

    func submit() async {
        guard link.canDownload, !submitting else { return }
        submitting = true
        defer { submitting = false }
        do {
            let outcome = try await core.submit(url: url, quality: quality)
            message = outcome.message
            if outcome.started { url = "" }
        } catch {
            message = error.localizedDescription
        }
    }

    /// guardavideo://download?url=… starts the download; guardavideo://add?url=… only fills the field.
    func open(_ deepLink: URL) async {
        guard deepLink.scheme == "guardavideo",
              let link = URLComponents(url: deepLink, resolvingAgainstBaseURL: false)?
                .queryItems?.first(where: { $0.name == "url" })?.value else { return }
        url = link
        if deepLink.host == "download" { await submit() }
    }

    func paste() {
        guard let text = UIPasteboard.general.string,
              let range = text.range(of: #"https?://\S+"#, options: .regularExpression) else { return }
        url = String(text[range])
    }

    func cancel(_ job: Shared.Job) { core.cancel(id: job.id) }
    func retry(_ job: Shared.Job) { core.retry(id: job.id) }
    func delete(_ job: Shared.Job) { core.delete(id: job.id) }
    func toggleStar(_ job: Shared.Job) { core.setStarred(id: job.id, starred: !job.starred) }
    func savePosition(_ job: Shared.Job, positionMs: Int64, durationMs: Int64) {
        core.savePosition(id: job.id, positionMs: positionMs, durationMs: durationMs)
    }

    /// iOS suspends apps in the background; ask for the extra minutes it grants while downloading.
    private func updateBackgroundTask() {
        let active = jobs.contains { $0.status.isActive }
        if active && backgroundTask == .invalid {
            backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "downloads") { [weak self] in
                self?.endBackgroundTask()
            }
        } else if !active {
            endBackgroundTask()
        }
    }

    private func endBackgroundTask() {
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }
}
