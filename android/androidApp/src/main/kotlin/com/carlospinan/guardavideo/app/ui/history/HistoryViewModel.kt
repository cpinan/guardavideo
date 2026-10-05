package com.carlospinan.guardavideo.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.di.Now
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.ui.library.UiMessage
import com.carlospinan.guardavideo.library.CreateResult
import com.carlospinan.guardavideo.library.HistoryEntry
import com.carlospinan.guardavideo.library.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HistoryDay(val date: LocalDate, val entries: List<HistoryEntry>)

data class HistoryUiState(val loaded: Boolean = false, val total: Int = 0, val query: String = "", val days: List<HistoryDay> = emptyList())

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: JobRepository,
    private val queue: DownloadQueue,
    private val now: Now,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> = combine(repository.observeHistory(), query) { all, q ->
        val needle = q.trim().lowercase()
        val matches = all.filter { needle.isEmpty() || needle in "${it.displayTitle} ${it.url}".lowercase() }
        HistoryUiState(true, all.size, q, groupByDay(matches))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val today: LocalDate get() = Instant.ofEpochMilli(now.millis()).atZone(ZoneId.systemDefault()).toLocalDate()

    fun onQuery(value: String) {
        query.value = value
    }

    /** Same rules as the library: an existing download is reused, not duplicated. */
    fun downloadAgain(entry: HistoryEntry) = viewModelScope.launch {
        when (val result = repository.create(entry.url, null)) {
            is CreateResult.Created -> {
                queue.enqueue(result.job.id)
                _messages.send(UiMessage.Res(R.string.download_started))
            }
            is CreateResult.Existing -> _messages.send(
                UiMessage.Res(if (result.job.status.isActive) R.string.already_downloading else R.string.already_downloaded),
            )
            is CreateResult.Rejected -> _messages.send(UiMessage.Text(result.reason))
        }
    }

    fun remove(entry: HistoryEntry) = viewModelScope.launch { repository.removeFromHistory(entry.jobId) }

    fun clear() = viewModelScope.launch { repository.clearHistory() }

    private fun groupByDay(entries: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()): List<HistoryDay> =
        entries.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .entries.sortedByDescending { it.key }
            .map { (date, list) -> HistoryDay(date, list.sortedByDescending { it.createdAt }) }
}
