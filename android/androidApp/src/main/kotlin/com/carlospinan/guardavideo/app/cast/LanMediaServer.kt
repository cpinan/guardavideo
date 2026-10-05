package com.carlospinan.guardavideo.app.cast

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * A Chromecast cannot read the phone's files: it fetches the video over Wi-Fi. This serves the
 * one file being cast, with byte ranges (seeking), at http://<phone-ip>:<port>/<token>/<name>.
 *
 * Safety: only the file currently registered is served, behind a random 128-bit token that
 * changes every time the server starts; anything else is 404. Bound to the given address (the
 * Wi-Fi interface), so it is not exposed on mobile data. Plain Java sockets: unit-testable.
 */
@Singleton
class LanMediaServer @Inject constructor() {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var file: File? = null
    private var token = ""

    /** Serves [video] and returns the URL a Cast device can open. */
    @Synchronized
    fun serve(video: File, bindAddress: InetAddress): String {
        val current = server
        val socket = if (current != null && !current.isClosed && current.inetAddress == bindAddress) {
            current
        } else {
            stop()
            token = newToken()
            ServerSocket(0, 16, bindAddress).also { s ->
                server = s
                thread(name = "lan-media-server", isDaemon = true) { acceptLoop(s) }
            }
        }
        file = video
        val host = bindAddress.hostAddress
        return "http://$host:${socket.localPort}/$token/${video.name}"
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        file = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(name = "lan-media-client", isDaemon = true) { client.use { handle(it) } }
        }
    }

    private fun handle(client: Socket) {
        val input = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = input.readLine() ?: return
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
        val (method, path) = requestLine.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val out = client.getOutputStream()
        val video = file
        if (method != "GET" && method != "HEAD") return out.respond("405 Method Not Allowed")
        if (video == null || path.substringBefore('?') != "/$token/${video.name}" || !video.exists()) {
            return out.respond("404 Not Found")
        }
        val size = video.length()
        var start = 0L
        var end = size - 1
        var partial = false
        headers.firstOrNull { it.startsWith("range:", ignoreCase = true) }?.substringAfter("=")?.let { range ->
            val (a, b) = range.split("-", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (a.isEmpty() && b.isNotEmpty()) { // suffix: last N bytes
                start = (size - b.toLong()).coerceAtLeast(0)
            } else {
                start = a.toLongOrNull() ?: 0
                b.toLongOrNull()?.let { end = minOf(it, end) }
            }
            if (start > end || start >= size) {
                return out.respond("416 Range Not Satisfiable", "Content-Range: bytes */$size")
            }
            partial = true
        }
        val length = end - start + 1
        val head = buildString {
            append("HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\n")
            append("Content-Type: ${contentType(video.name)}\r\n")
            append("Content-Length: $length\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Access-Control-Allow-Origin: *\r\n") // the Cast receiver is a web page
            if (partial) append("Content-Range: bytes $start-$end/$size\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        if (method == "GET") {
            RandomAccessFile(video, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n <= 0) break
                    out.write(buffer, 0, n) // throws when the receiver hangs up after a seek: fine
                    remaining -= n
                }
            }
        }
        out.flush()
    }

    private fun OutputStream.respond(status: String, extra: String? = null) {
        val head = "HTTP/1.1 $status\r\nContent-Length: 0\r\n${extra?.let { "$it\r\n" }.orEmpty()}Connection: close\r\n\r\n"
        write(head.toByteArray(Charsets.ISO_8859_1))
        flush()
    }

    private fun contentType(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }

    private fun newToken(): String = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
}
