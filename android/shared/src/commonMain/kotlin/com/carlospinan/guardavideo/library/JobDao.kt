package com.carlospinan.guardavideo.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY created_at DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs ORDER BY created_at DESC")
    suspend fun all(): List<JobEntity>

    @Insert
    suspend fun insert(job: JobEntity)

    @Upsert
    suspend fun upsert(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)
}
