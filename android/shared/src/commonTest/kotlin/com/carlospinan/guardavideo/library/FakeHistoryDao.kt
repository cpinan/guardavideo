package com.carlospinan.guardavideo.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHistoryDao : HistoryDao {
    private val rows = MutableStateFlow<Map<String, HistoryEntity>>(emptyMap())

    override fun observeAll(): Flow<List<HistoryEntity>> = rows.map { m -> m.values.sortedByDescending { it.createdAt } }

    override suspend fun get(jobId: String) = rows.value[jobId]

    override suspend fun count() = rows.value.size

    override suspend fun upsert(entry: HistoryEntity) { rows.value += entry.jobId to entry }

    override suspend fun delete(jobId: String) { rows.value -= jobId }

    override suspend fun clear() { rows.value = emptyMap() }
}
