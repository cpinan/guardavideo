package com.carlospinan.guardavideo.download

import com.carlospinan.guardavideo.hls.Playlist
import com.carlospinan.guardavideo.hls.PlaylistException
import com.carlospinan.guardavideo.hls.Segment
import com.carlospinan.guardavideo.hls.parsePlaylist
import com.carlospinan.guardavideo.hls.pickVariant
import com.carlospinan.guardavideo.hls.segmentIv
import com.carlospinan.guardavideo.source.LinkSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.decodeURLPart
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** AES-128-CBC with PKCS7 padding, as HLS defines it (this is not DRM). Platform-specific. */
fun interface SegmentDecryptor {
    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
}

expect fun platformDecryptor(): SegmentDecryptor

/** Exponential backoff for network failures: 0.5 s, 1 s, 2 s… capped at [maxDelayMs]. */
data class RetryPolicy(val attempts: Int = 5, val baseDelayMs: Long = 500, val maxDelayMs: Long = 8_000) {
    fun delayFor(attempt: Int): Long = (baseDelayMs shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(maxDelayMs)
}

/** What the caller hears while a download runs. [percent] is -1 when the size is unknown. */
interface DownloadListener {
    fun progress(percent: Float) {}

    /** A recoverable network failure; `attempt = 0` means it recovered. */
    fun retrying(attempt: Int) {}
}

data class DownloadResult(val path: Path, val title: String?, val sizeBytes: Long)

/** Seam for tests and for the runner: [MediaDownloader] is the real implementation. */
interface VideoDownloader {
    suspend fun download(
        source: LinkSource,
        id: String,
        destDir: Path,
        quality: Int? = null,
        listener: DownloadListener = object : DownloadListener {},
    ): DownloadResult
}

/** A failure the user should read. Messages are in Spanish, like the rest of the UI. */
class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class HttpStatusException(val code: Int) : Exception("HTTP $code") {
    /** 4xx means "this will not work" (except timeout and rate limit); 5xx may be temporary. */
    val retryable: Boolean get() = code >= 500 || code == 408 || code == 429
}

/**
 * Downloads direct media links on the device: HLS playlists (segments fetched in parallel,
 * written in order, AES-128 decrypted) and plain video files.
 *
 * Output goes to `<destDir>/<id>.part` and is renamed to `<id>.<ext>` only when complete, so a
 * crash or cancellation never leaves something that looks finished. Cancellation is the
 * coroutine's: cancel the calling job and the partial file is deleted.
 */
class MediaDownloader(
    private val http: HttpClient,
    private val fs: FileSystem,
    private val decryptor: SegmentDecryptor = platformDecryptor(),
    private val retry: RetryPolicy = RetryPolicy(),
    private val workers: Int = 6,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : VideoDownloader {
    override suspend fun download(
        source: LinkSource,
        id: String,
        destDir: Path,
        quality: Int?,
        listener: DownloadListener,
    ): DownloadResult = withContext(io) {
        val part = destDir / "$id.part"
        try {
            when (source) {
                is LinkSource.Hls -> downloadHls(source.url, id, destDir, part, quality, listener)
                is LinkSource.DirectFile -> downloadFile(source, id, destDir, part, listener)
                else -> throw DownloadException("Este enlace no se puede descargar en el teléfono")
            }
        } catch (t: Throwable) {
            fs.delete(part, mustExist = false)
            throw t
        }
    }

    // ---- direct file ----

    private suspend fun downloadFile(
        source: LinkSource.DirectFile,
        id: String,
        destDir: Path,
        part: Path,
        listener: DownloadListener,
    ): DownloadResult {
        withRetry(listener) {
            http.prepareGet(source.url).execute { response ->
                response.requireOk()
                val total = response.contentLength()?.takeIf { it > 0 }
                fs.sink(part).buffer().use { sink -> copyBody(response, sink, total, listener) }
            }
        }
        val file = destDir / "$id.${source.extension}"
        fs.atomicMove(part, file)
        listener.progress(100f)
        return DownloadResult(file, titleFrom(source.url), fs.metadata(file).size ?: 0)
    }

    private suspend fun copyBody(response: HttpResponse, sink: BufferedSink, total: Long?, listener: DownloadListener) {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        var lastReported = -1
        while (true) {
            val n = channel.readAvailable(buffer, 0, buffer.size)
            if (n == -1) break
            if (n == 0) continue
            sink.write(buffer, 0, n)
            read += n
            if (total == null) {
                if (lastReported != -1) listener.progress(-1f).also { lastReported = -1 }
            } else {
                val pct = (read * 1000 / total).toInt() // tenths of a percent: no flood of updates
                if (pct != lastReported) listener.progress(pct / 10f).also { lastReported = pct }
            }
        }
    }

    // ---- HLS ----

    private suspend fun downloadHls(
        url: String,
        id: String,
        destDir: Path,
        part: Path,
        quality: Int?,
        listener: DownloadListener,
    ): DownloadResult {
        val media = mediaPlaylist(url, quality, listener)
        if (!media.ended) {
            throw DownloadException("Es una transmisión en vivo: solo se pueden descargar videos completos")
        }
        if (media.segments.isEmpty()) throw DownloadException("La lista HLS no tiene segmentos")
        media.segments.firstNotNullOfOrNull { it.key?.method?.takeIf { m -> m != "AES-128" } }?.let {
            throw DownloadException("Cifrado no soportado ($it): puede estar protegido con DRM")
        }

        val keys = KeyCache { keyUrl -> fetchBytes(keyUrl, listener) }
        val total = media.segments.size
        var written = 0
        var currentInit: String? = null
        fs.sink(part).buffer().use { sink ->
            // Batches of `workers`: parallel network, ordered writes, bounded memory.
            for (batch in media.segments.withIndex().chunked(workers)) {
                val bytes = coroutineScope {
                    batch.map { (index, segment) ->
                        async { fetchSegment(segment, index, media.mediaSequence, keys, listener) }
                    }.awaitAll()
                }
                batch.forEachIndexed { i, (_, segment) ->
                    val init = segment.initUri
                    if (init != null && init != currentInit) { // fMP4: the init section goes first
                        sink.write(fetchBytes(init, listener))
                        currentInit = init
                    }
                    sink.write(bytes[i])
                    written++
                    listener.progress(written * 100f / total)
                }
            }
        }
        // Concatenated MPEG-TS plays as .ts; fMP4 segments with their init section form an .mp4.
        val extension = if (media.segments.any { it.initUri != null }) "mp4" else "ts"
        val file = destDir / "$id.$extension"
        fs.atomicMove(part, file)
        return DownloadResult(file, titleFrom(url), fs.metadata(file).size ?: 0)
    }

    private suspend fun mediaPlaylist(url: String, quality: Int?, listener: DownloadListener): Playlist.Media =
        when (val playlist = parse(url, listener)) {
            is Playlist.Media -> playlist
            is Playlist.Master -> {
                val variant = pickVariant(playlist.variants, quality)
                parse(variant.uri, listener) as? Playlist.Media
                    ?: throw DownloadException("La variante elegida no es una lista de segmentos")
            }
        }

    private suspend fun parse(url: String, listener: DownloadListener): Playlist =
        try {
            parsePlaylist(fetchBytes(url, listener).decodeToString(), url)
        } catch (e: PlaylistException) {
            throw DownloadException(e.message ?: "Lista HLS inválida", e)
        }

    private suspend fun fetchSegment(
        segment: Segment,
        index: Int,
        mediaSequence: Long,
        keys: KeyCache,
        listener: DownloadListener,
    ): ByteArray {
        val data = fetchBytes(segment.uri, listener)
        val key = segment.key ?: return data
        val keyUri = key.uri ?: throw DownloadException("Clave AES sin URI")
        return decryptor.decrypt(data, keys.get(keyUri), segmentIv(key, mediaSequence, index))
    }

    // ---- network ----

    private suspend fun fetchBytes(url: String, listener: DownloadListener): ByteArray =
        withRetry(listener) {
            val response = http.get(url)
            response.requireOk()
            response.body<ByteArray>()
        }

    private suspend fun <T> withRetry(listener: DownloadListener, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                val result = block()
                if (attempt > 0) listener.retrying(0) // recovered
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                throw e
            } catch (e: HttpStatusException) {
                if (!e.retryable || attempt >= retry.attempts) {
                    throw DownloadException(httpMessage(e.code), e)
                }
            } catch (e: Exception) {
                if (attempt >= retry.attempts) throw DownloadException("Sin conexión con el origen", e)
            }
            attempt++
            listener.retrying(attempt)
            delay(retry.delayFor(attempt)) // cancellable: cancelling stops the wait at once
        }
    }

    private fun HttpResponse.requireOk() {
        if (status.value !in 200..299) throw HttpStatusException(status.value)
    }
}

internal fun httpMessage(code: Int): String = when (code) {
    401, 403 -> "El origen no permite descargar este video ($code)"
    404, 410 -> "El video ya no existe en el origen ($code)"
    else -> "El origen respondió con un error ($code)"
}

/** Last path segment without its extension, URL-decoded: "Mi%20video.mp4" → "Mi video". */
internal fun titleFrom(url: String): String? =
    url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        .substringBeforeLast('.').decodeURLPart().takeIf { it.isNotBlank() }

/** Each AES key is fetched once, even when several segments ask for it at the same time. */
private class KeyCache(private val fetch: suspend (String) -> ByteArray) {
    private val keys = mutableMapOf<String, ByteArray>()
    private val mutex = Mutex()

    suspend fun get(uri: String): ByteArray = mutex.withLock { keys.getOrPut(uri) { fetch(uri) } }
}
