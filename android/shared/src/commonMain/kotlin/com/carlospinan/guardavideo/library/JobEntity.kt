package com.carlospinan.guardavideo.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "jobs", indices = [Index("url"), Index("status")])
data class JobEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String?,
    val status: String,
    val percent: Float,
    val quality: Int?,
    val error: String?,
    val retries: Int,
    @ColumnInfo(name = "file_path") val filePath: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val starred: Boolean,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    val watched: Boolean,
    // v2
    @ColumnInfo(name = "view_count", defaultValue = "0") val viewCount: Int = 0,
    @ColumnInfo(name = "last_viewed_at") val lastViewedAt: Long? = null,
    @ColumnInfo(name = "position_updated_at") val positionUpdatedAt: Long? = null,
    // v3
    @ColumnInfo(name = "playback_speed", defaultValue = "1.0") val playbackSpeed: Float = 1f,
)

internal fun JobEntity.toJob() = Job(
    id, url, title, JobStatus.valueOf(status), percent, quality, error, retries, filePath, sizeBytes,
    createdAt, starred, positionMs, durationMs, watched, viewCount, lastViewedAt, positionUpdatedAt, playbackSpeed,
)

internal fun Job.toEntity() = JobEntity(
    id, url, title, status.name, percent, quality, error, retries, filePath, sizeBytes,
    createdAt, starred, positionMs, durationMs, watched, viewCount, lastViewedAt, positionUpdatedAt, playbackSpeed,
)
