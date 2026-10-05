package com.carlospinan.guardavideo.library

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What happened to a link in the end. A deleted video keeps its history entry. */
enum class HistoryStatus { InProgress, Downloaded, Failed, Cancelled, Deleted }

/** One link the user downloaded (or tried to): kept even after the video is deleted. */
data class HistoryEntry(
    val jobId: String,
    val url: String,
    val title: String?,
    val createdAt: Long,
    val status: HistoryStatus,
    val finishedAt: Long? = null,
    val sizeBytes: Long? = null,
    val error: String? = null,
) {
    val displayTitle: String get() = title ?: com.carlospinan.guardavideo.download.titleFrom(url) ?: url
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey @ColumnInfo(name = "job_id") val jobId: String,
    val url: String,
    val title: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val status: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    val error: String?,
)

internal fun HistoryEntity.toEntry() =
    HistoryEntry(jobId, url, title, createdAt, HistoryStatus.valueOf(status), finishedAt, sizeBytes, error)

internal fun HistoryEntry.toEntity() =
    HistoryEntity(jobId, url, title, createdAt, status.name, finishedAt, sizeBytes, error)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY created_at DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE job_id = :jobId")
    suspend fun get(jobId: String): HistoryEntity?

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE job_id = :jobId")
    suspend fun delete(jobId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

/** The history entry that mirrors a job's current state. */
internal fun historyOf(job: Job, previous: HistoryEntry?, now: Long): HistoryEntry {
    val status = when (job.status) {
        JobStatus.Queued, JobStatus.Running -> HistoryStatus.InProgress
        JobStatus.Done -> HistoryStatus.Downloaded
        JobStatus.Error -> HistoryStatus.Failed
        JobStatus.Cancelled -> HistoryStatus.Cancelled
    }
    val finished = if (status == HistoryStatus.InProgress) null else previous?.finishedAt ?: now
    return HistoryEntry(job.id, job.url, job.title ?: previous?.title, job.createdAt, status, finished, job.sizeBytes, job.error)
}
