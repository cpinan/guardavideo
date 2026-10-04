package com.carlospinan.guardavideo.library

import com.carlospinan.guardavideo.source.LinkSource
import com.carlospinan.guardavideo.source.detectSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath

sealed interface CreateResult {
    data class Created(val job: Job) : CreateResult

    /** Same link already downloading or already on the phone: nothing new was queued. */
    data class Existing(val job: Job) : CreateResult

    data class Rejected(val reason: String) : CreateResult
}

/**
 * The library: every read and write of a job goes through here. Read-modify-write updates are
 * serialized with a mutex, so the worker's progress and a tap on "favorite" never overwrite each
 * other.
 */
class JobRepository(
    private val dao: JobDao,
    private val fs: FileSystem,
    private val clock: () -> Long,
    private val newId: () -> String,
) {
    private val mutex = Mutex()

    fun observeJobs(): Flow<List<Job>> = dao.observeAll().map { rows -> rows.map { it.toJob() } }

    fun observe(id: String): Flow<Job?> = dao.observe(id).map { it?.toJob() }

    suspend fun get(id: String): Job? = dao.get(id)?.toJob()

    suspend fun active(): List<Job> = dao.all().map { it.toJob() }.filter { it.status.isActive }

    suspend fun create(rawUrl: String, quality: Int?): CreateResult = mutex.withLock {
        val url = rawUrl.trim()
        val source = detectSource(url)
        if (!source.canDownload) return CreateResult.Rejected(sourceMessage(source))
        val existing = dao.all().map { it.toJob() }.firstOrNull { job ->
            job.url == url && job.quality == quality && (job.status.isActive || hasFile(job))
        }
        if (existing != null) return CreateResult.Existing(existing)
        val job = Job(id = newId(), url = url, quality = quality, createdAt = clock())
        dao.insert(job.toEntity())
        CreateResult.Created(job)
    }

    /** Applies [change] to the current row; null if the job no longer exists. */
    suspend fun update(id: String, change: (Job) -> Job): Job? = mutex.withLock {
        val current = dao.get(id)?.toJob() ?: return null
        change(current).also { dao.upsert(it.toEntity()) }
    }

    /** Failed or cancelled jobs go back to the queue, keeping their id and URL. */
    suspend fun retry(id: String): Job? = mutex.withLock {
        val job = dao.get(id)?.toJob() ?: return null
        if (job.status != JobStatus.Error && job.status != JobStatus.Cancelled) return null
        job.copy(status = JobStatus.Queued, percent = 0f, error = null, retries = 0).also { dao.upsert(it.toEntity()) }
    }

    /** Marks a queued or running job as cancelled (the caller stops the actual work). */
    suspend fun cancel(id: String): Job? =
        update(id) { if (it.status.isActive) it.copy(status = JobStatus.Cancelled, retries = 0) else it }

    suspend fun setStarred(id: String, starred: Boolean): Job? =
        update(id) { if (it.status == JobStatus.Done) it.copy(starred = starred) else it }

    suspend fun savePosition(id: String, positionMs: Long, durationMs: Long?) {
        update(id) { job ->
            val total = durationMs ?: job.durationMs
            val watched = total != null && total > 0 && positionMs >= total - END_MARGIN_MS
            job.copy(positionMs = if (watched) 0 else positionMs, durationMs = total, watched = job.watched || watched)
        }
    }

    /**
     * Removes a finished job and its file. The file stays if another job still points at it.
     * Active jobs must be cancelled first.
     */
    suspend fun delete(id: String): Boolean = mutex.withLock {
        val job = dao.get(id)?.toJob() ?: return false
        if (job.status.isActive) return false
        val path = job.filePath
        val shared = path != null && dao.all().any { it.id != id && it.filePath == path }
        if (path != null && !shared) fs.delete(path.toPath(), mustExist = false)
        dao.delete(id)
        true
    }

    private fun hasFile(job: Job): Boolean =
        job.status == JobStatus.Done && job.filePath?.let { fs.exists(it.toPath()) } == true

    companion object {
        /** Within the last 3 s counts as "watched": the next play starts from the beginning. */
        const val END_MARGIN_MS = 3_000L
    }
}

/** What the URL field shows for a link, in the UI's language. */
fun sourceMessage(source: LinkSource): String = when (source) {
    LinkSource.Invalid -> "Ingresa una URL http(s) válida"
    is LinkSource.Hls -> "Stream HLS directo"
    is LinkSource.DirectFile -> "Archivo de video (.${source.extension})"
    is LinkSource.SitePage ->
        "${source.provider}: en el teléfono solo se descargan enlaces directos (.m3u8 o .mp4)"
    is LinkSource.Drm -> "${source.provider} está protegido con DRM: no se puede descargar"
    is LinkSource.UnknownPage -> "${source.host} no es un enlace directo a un video (.m3u8 o .mp4)"
}
