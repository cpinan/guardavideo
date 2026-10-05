package com.carlospinan.guardavideo.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.di.Now
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.media.StorageInfo
import com.carlospinan.guardavideo.app.media.VideoExporter
import com.carlospinan.guardavideo.download.LinkPolicy
import com.carlospinan.guardavideo.library.CreateResult
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.JobStatus
import com.carlospinan.guardavideo.library.LibraryFilter
import com.carlospinan.guardavideo.library.LibrarySort
import com.carlospinan.guardavideo.library.Shelves
import com.carlospinan.guardavideo.library.browse
import com.carlospinan.guardavideo.library.buildShelves
import com.carlospinan.guardavideo.source.LinkSource
import com.carlospinan.guardavideo.source.detectSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One-off message for the snackbar: a string resource, or text that already comes translated. */
sealed interface UiMessage {
    data class Res(val id: Int) : UiMessage

    data class Text(val text: String) : UiMessage
}

sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Date(val date: LocalDate) : DayLabel
}

/** One day of the list: downloads still going (full width) and finished videos (a carousel). */
data class DayGroup(val day: DayLabel, val pending: List<Job>, val finished: List<Job>)

data class Controls(
    val filter: LibraryFilter = LibraryFilter.All,
    val sort: LibrarySort = LibrarySort.Recent,
    val query: String = "",
)

data class LibraryUiState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val active: Int = 0,
    val done: Int = 0,
    /** Distinct finished files and their total size, plus free space: the summary line. */
    val videos: Int = 0,
    val bytes: Long = 0,
    val freeBytes: Long = 0,
    val shelves: Shelves = Shelves(emptyList(), emptyList(), emptyList(), emptyList()),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val controls: Controls = Controls(),
    /** Filled when sorting by date; otherwise [flat] holds the sorted list. */
    val groups: List<DayGroup> = emptyList(),
    val flat: List<Job> = emptyList(),
) {
    val noMatches: Boolean get() = total > 0 && groups.isEmpty() && flat.isEmpty()

    /** Typing in search: shelves and day groups step aside for a plain list of matches. */
    val searching: Boolean get() = controls.query.isNotBlank()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: JobRepository,
    private val queue: DownloadQueue,
    private val exporter: VideoExporter,
    private val policy: LinkPolicy,
    private val storage: StorageInfo,
    private val now: Now,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())

    /** Everything the library screen shows, derived from the database (single source of truth). */
    val state: StateFlow<LibraryUiState> = combine(repository.observeJobs(), controls) { jobs, c -> buildState(jobs, c) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    // Text field state stays synchronous (Compose guidance for TextField in a ViewModel).
    var url by mutableStateOf("")
        private set
    var quality by mutableStateOf<Int?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set
    var searchOpen by mutableStateOf(false)
        private set

    val source: LinkSource get() = detectSource(url)
    val canSubmit: Boolean get() = policy.canDownload(source)
    val linkMessage: String get() = policy.message(source)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        // Self-healing: a job whose worker died (process killed, refused foreground service…)
        // still says "running". Re-enqueueing is idempotent (unique work, KEEP), so opening the
        // library always gets stuck downloads moving again.
        viewModelScope.launch { repository.active().forEach { queue.enqueue(it.id) } }
    }

    fun onUrlChange(value: String) {
        url = value
    }

    fun onQualityChange(value: Int?) {
        quality = value
    }

    fun useExample() {
        url = EXAMPLE_URL
    }

    fun onFilter(filter: LibraryFilter) = controls.update { it.copy(filter = filter) }

    fun onSort(sort: LibrarySort) = controls.update { it.copy(sort = sort) }

    fun onQuery(query: String) = controls.update { it.copy(query = query) }

    fun openSearch() {
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        controls.update { it.copy(query = "") }
    }

    fun submit() {
        if (submitting || !canSubmit) return
        submitting = true
        viewModelScope.launch {
            try {
                when (val result = repository.create(url, quality)) {
                    is CreateResult.Created -> {
                        queue.enqueue(result.job.id)
                        url = ""
                        _messages.send(UiMessage.Res(R.string.download_started))
                    }
                    is CreateResult.Existing -> _messages.send(
                        UiMessage.Res(if (result.job.status.isActive) R.string.already_downloading else R.string.already_downloaded),
                    )
                    is CreateResult.Rejected -> _messages.send(UiMessage.Text(result.reason))
                }
            } finally {
                submitting = false
            }
        }
    }

    fun cancel(id: String) = viewModelScope.launch {
        repository.cancel(id) // first: the worker then knows it was the user, not the system
        queue.cancel(id)
    }

    fun retry(id: String) = viewModelScope.launch { repository.retry(id)?.let { queue.enqueue(it.id) } }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    /** "Limpiar con error": every failed or cancelled job, whatever the filter shows. */
    fun clearFailed() = viewModelScope.launch {
        repository.observeJobs().first().filter(LibraryFilter.Failed.test).forEach { repository.delete(it.id) }
    }

    fun toggleStar(job: Job) = viewModelScope.launch { repository.setStarred(job.id, !job.starred) }

    fun saveToGallery(job: Job) = viewModelScope.launch {
        val ok = exporter.exportToGallery(job)
        _messages.send(UiMessage.Res(if (ok) R.string.saved_to_gallery else R.string.save_failed))
    }

    private fun buildState(jobs: List<Job>, c: Controls): LibraryUiState {
        val nowMs = now.millis()
        val visible = browse(jobs, c.filter, c.query, c.sort)
        val files = jobs.filter { it.status == JobStatus.Done }.mapNotNull { j -> j.filePath?.let { it to (j.sizeBytes ?: 0) } }.toMap()
        return LibraryUiState(
            loaded = true,
            total = jobs.size,
            active = jobs.count { it.status.isActive },
            done = jobs.count { it.status == JobStatus.Done },
            videos = files.size,
            bytes = files.values.sum(),
            freeBytes = storage.freeBytes(),
            shelves = buildShelves(jobs, nowMs),
            counts = LibraryFilter.entries.associateWith { f -> jobs.count(f.test) },
            controls = c,
            groups = if (c.sort == LibrarySort.Recent && c.query.isBlank()) groupByDay(visible, nowMs) else emptyList(),
            flat = if (c.sort == LibrarySort.Recent && c.query.isBlank()) emptyList() else visible,
        )
    }

    companion object {
        /** Public HLS test stream (Big Buck Bunny), like the web's "probar con un ejemplo". */
        const val EXAMPLE_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    }
}

/** Consecutive jobs of the same local day; the list arrives newest first. */
internal fun groupByDay(jobs: List<Job>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<DayGroup> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return jobs.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        .entries.sortedByDescending { it.key }
        .map { (date, dayJobs) ->
            val label = when (date) {
                today -> DayLabel.Today
                today.minusDays(1) -> DayLabel.Yesterday
                else -> DayLabel.Date(date)
            }
            DayGroup(label, dayJobs.filter { it.status != JobStatus.Done }, dayJobs.filter { it.status == JobStatus.Done })
        }
}
