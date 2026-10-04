package com.carlospinan.guardavideo.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory JobDao: same ordering as the real query (newest first). */
class FakeJobDao : JobDao {
    private val rows = MutableStateFlow<Map<String, JobEntity>>(emptyMap())

    private fun sorted(m: Map<String, JobEntity>) = m.values.sortedByDescending { it.createdAt }

    override fun observeAll(): Flow<List<JobEntity>> = rows.map(::sorted)

    override suspend fun get(id: String): JobEntity? = rows.value[id]

    override fun observe(id: String): Flow<JobEntity?> = rows.map { it[id] }

    override suspend fun all(): List<JobEntity> = sorted(rows.value)

    override suspend fun insert(job: JobEntity) {
        check(job.id !in rows.value) { "duplicate id ${job.id}" }
        rows.value += job.id to job
    }

    override suspend fun upsert(job: JobEntity) {
        rows.value += job.id to job
    }

    override suspend fun delete(id: String) {
        rows.value -= id
    }
}
