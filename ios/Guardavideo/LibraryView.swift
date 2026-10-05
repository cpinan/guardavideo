import Shared
import SwiftUI

struct LibraryView: View {
    @EnvironmentObject private var library: LibraryModel

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField("URL del video", text: $library.url, prompt: Text("Enlace .m3u8 o .mp4"))
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.go)
                            .onSubmit { Task { await library.submit() } }
                        Button { library.paste() } label: { Image(systemName: "doc.on.clipboard") }
                            .accessibilityLabel("Pegar")
                    }
                    if !library.url.isEmpty {
                        Text(library.link.message)
                            .font(.footnote)
                            .foregroundStyle(library.link.canDownload ? Color.secondary : Color.red)
                    }
                    Picker("Calidad", selection: $library.quality) {
                        Text("Mejor").tag(Int32(0))
                        Text("1080p").tag(Int32(1080))
                        Text("720p").tag(Int32(720))
                        Text("480p").tag(Int32(480))
                    }
                    Button {
                        Task { await library.submit() }
                    } label: {
                        Label("Descargar", systemImage: "arrow.down.circle.fill").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!library.link.canDownload || library.submitting)
                }

                if library.jobs.isEmpty {
                    Section {
                        ContentUnavailableCompat()
                    }
                } else {
                    Section("Videos") {
                        ForEach(library.jobs, id: \.id) { job in
                            JobRow(job: job)
                                .swipeActions {
                                    if !job.status.isActive {
                                        Button("Eliminar", role: .destructive) { library.delete(job) }
                                    }
                                }
                                .contextMenu {
                                    if let file = exportFile(job) {
                                        ShareLink(item: file) { Label("Compartir o guardar", systemImage: "square.and.arrow.up") }
                                    }
                                }
                        }
                    }
                }
            }
            .navigationTitle("Guardavideo")
            .alert(library.message ?? "", isPresented: Binding(get: { library.message != nil }, set: { if !$0 { library.message = nil } })) {
                Button("OK", role: .cancel) {}
            }
        }
    }
}

/// The file worth sharing: for a local playlist, the .ts it indexes (a playlist alone is useless).
private func exportFile(_ job: Shared.Job) -> URL? {
    guard job.status == JobStatus.done, let path = job.filePath else { return nil }
    let url = URL(fileURLWithPath: path)
    return url.pathExtension == "m3u8" ? url.deletingPathExtension().appendingPathExtension("ts") : url
}

private struct ContentUnavailableCompat: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "film.stack").font(.largeTitle).foregroundStyle(.tint)
            Text("Aún no hay videos").font(.headline)
            Text("Pega un enlace directo a un video (.m3u8 o .mp4).")
                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}

private struct JobRow: View {
    @EnvironmentObject private var library: LibraryModel
    let job: Shared.Job

    var body: some View {
        if job.status == JobStatus.done, let path = job.filePath {
            NavigationLink {
                PlayerView(job: job, fileURL: URL(fileURLWithPath: path))
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "play.rectangle.fill").font(.title2).foregroundStyle(.tint)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(job.displayTitle).font(.subheadline.weight(.semibold)).lineLimit(2)
                        Text(details).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { library.toggleStar(job) } label: {
                        Image(systemName: job.starred ? "star.fill" : "star").foregroundStyle(job.starred ? .yellow : .secondary)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(job.starred ? "Quitar de favoritos" : "Marcar como favorito")
                }
            }
        } else if job.status.isActive {
            VStack(alignment: .leading, spacing: 6) {
                Text(job.displayTitle).font(.subheadline.weight(.semibold)).lineLimit(2)
                if job.status == JobStatus.queued || job.percent <= 0 {
                    ProgressView().frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    ProgressView(value: Double(job.percent), total: 100)
                }
                HStack {
                    Text(job.retries > 0 ? "Reconectando… intento \(job.retries)" : "\(Int(job.percent)) %")
                        .font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Button("Cancelar") { library.cancel(job) }.buttonStyle(.borderless)
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Text(job.displayTitle).font(.subheadline.weight(.semibold)).lineLimit(2)
                Text(job.error ?? (job.status == JobStatus.cancelled ? "Cancelado" : "Error"))
                    .font(.caption).foregroundStyle(.red)
                Button("Reintentar") { library.retry(job) }.buttonStyle(.borderless)
            }
        }
    }

    private var details: String {
        var parts: [String] = []
        if let size = job.sizeBytes?.int64Value {
            parts.append(ByteCountFormatter.string(fromByteCount: size, countStyle: .file))
        }
        if job.watched { parts.append("Visto") } else if job.positionMs >= 1000 { parts.append("Visto \(clock(job.positionMs))") }
        return parts.joined(separator: " · ")
    }

    private func clock(_ ms: Int64) -> String {
        let s = Int(ms / 1000)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }
}
