import Foundation
import Network

/// AVPlayer only plays HLS over HTTP, never from a file path. Downloads saved as MPEG-TS come with
/// a local .m3u8 that indexes the .ts by byte range; this serves the videos folder on the
/// loopback interface so AVPlayer can stream it like any HLS (seeking included), fully offline.
///
/// Bound to 127.0.0.1 only, and every path needs a random token minted at launch, so other apps
/// on the device cannot guess URLs. Supports GET/HEAD and single `Range: bytes=a-b` requests.
final class LocalMediaServer {
    static let shared = LocalMediaServer()

    private let token = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
    private let queue = DispatchQueue(label: "local-media-server")
    private var listener: NWListener?
    private var root: URL?
    private(set) var port: UInt16 = 0

    /// Starts serving [root] (idempotent). Blocks briefly until the port is known.
    func start(root: URL) throws {
        self.root = root
        guard listener == nil else { return }
        let params = NWParameters.tcp
        params.requiredLocalEndpoint = NWEndpoint.hostPort(host: "127.0.0.1", port: .any)
        let listener = try NWListener(using: params)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { [weak self] state in
            if case .ready = state {
                self?.port = listener.port?.rawValue ?? 0
                ready.signal()
            }
        }
        listener.newConnectionHandler = { [weak self] connection in self?.serve(connection) }
        listener.start(queue: queue)
        self.listener = listener
        _ = ready.wait(timeout: .now() + 2)
    }

    /// http URL for a file inside the served folder.
    func url(for file: URL) -> URL? {
        guard port != 0 else { return nil }
        return URL(string: "http://127.0.0.1:\(port)/\(token)/\(file.lastPathComponent)")
    }

    // MARK: - HTTP

    private func serve(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveRequest(connection, buffer: Data())
    }

    private func receiveRequest(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16 * 1024) { [weak self] data, _, done, error in
            guard let self, error == nil else { connection.cancel(); return }
            var buffer = buffer
            if let data { buffer.append(data) }
            if let end = buffer.range(of: Data("\r\n\r\n".utf8)) {
                let head = String(decoding: buffer[..<end.lowerBound], as: UTF8.self)
                self.respond(to: head, on: connection)
            } else if done || buffer.count > 64 * 1024 {
                connection.cancel()
            } else {
                self.receiveRequest(connection, buffer: buffer)
            }
        }
    }

    private func respond(to head: String, on connection: NWConnection) {
        let lines = head.components(separatedBy: "\r\n")
        let parts = lines.first?.split(separator: " ") ?? []
        guard parts.count >= 2, parts[0] == "GET" || parts[0] == "HEAD" else {
            return send(status: "405 Method Not Allowed", headers: [:], body: nil, on: connection)
        }
        let path = String(parts[1]).split(separator: "?").first.map(String.init) ?? ""
        let pieces = path.split(separator: "/").map(String.init)
        // /<token>/<file name>: no subfolders, no "..", nothing outside the root.
        guard pieces.count == 2, pieces[0] == token, !pieces[1].contains(".."),
              let root, let handle = try? FileHandle(forReadingFrom: root.appendingPathComponent(pieces[1])) else {
            return send(status: "404 Not Found", headers: [:], body: nil, on: connection)
        }
        defer { try? handle.close() }
        let size = (try? handle.seekToEnd()) ?? 0
        var start: UInt64 = 0
        var end: UInt64 = size > 0 ? size - 1 : 0
        var status = "200 OK"
        let rangeHeader = lines.dropFirst().first { $0.lowercased().hasPrefix("range:") }
        if let range = rangeHeader?.split(separator: "=", maxSplits: 1).last {
            let bounds = range.split(separator: "-", omittingEmptySubsequences: false)
            if let a = UInt64(bounds.first ?? "") { start = a }
            if bounds.count > 1, let b = UInt64(bounds[1]) { end = min(b, end) }
            guard start <= end, start < size else {
                return send(status: "416 Range Not Satisfiable", headers: ["Content-Range": "bytes */\(size)"], body: nil, on: connection)
            }
            status = "206 Partial Content"
        }
        let length = size == 0 ? 0 : end - start + 1
        var headers = [
            "Content-Type": contentType(pieces[1]),
            "Content-Length": "\(length)",
            "Accept-Ranges": "bytes",
        ]
        if status.hasPrefix("206") { headers["Content-Range"] = "bytes \(start)-\(end)/\(size)" }
        var body: Data?
        if parts[0] == "GET", length > 0 {
            try? handle.seek(toOffset: start)
            body = try? handle.read(upToCount: Int(length))
        }
        send(status: status, headers: headers, body: body, on: connection)
    }

    private func send(status: String, headers: [String: String], body: Data?, on connection: NWConnection) {
        var head = "HTTP/1.1 \(status)\r\nConnection: close\r\n"
        for (k, v) in headers { head += "\(k): \(v)\r\n" }
        if headers["Content-Length"] == nil { head += "Content-Length: 0\r\n" }
        head += "\r\n"
        var payload = Data(head.utf8)
        if let body { payload.append(body) }
        connection.send(content: payload, completion: .contentProcessed { _ in connection.cancel() })
    }

    private func contentType(_ name: String) -> String {
        switch (name as NSString).pathExtension.lowercased() {
        case "m3u8": return "application/vnd.apple.mpegurl"
        case "ts": return "video/mp2t"
        case "mp4", "m4v": return "video/mp4"
        case "mov": return "video/quicktime"
        default: return "application/octet-stream"
        }
    }
}
