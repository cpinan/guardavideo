package com.carlospinan.guardavideo.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.media.SearchHit
import com.carlospinan.guardavideo.app.media.SearchSite
import com.carlospinan.guardavideo.app.media.SiteSupport
import com.carlospinan.guardavideo.app.ui.library.UiMessage
import com.carlospinan.guardavideo.library.CreateResult
import com.carlospinan.guardavideo.library.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val loading: Boolean = false,
    /** True once a search finished (or is running): tells "nothing yet" from "no results". */
    val searched: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    /** Sites that failed, so one broken site does not hide the others' results. */
    val failedSites: List<SearchSite> = emptyList(),
    /** Page links already sent to the library from this screen. */
    val queued: Set<String> = emptySet(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    siteSupport: SiteSupport,
    private val repository: JobRepository,
    private val queue: DownloadQueue,
) : ViewModel() {
    private val web = siteSupport.search
    val available: Boolean get() = web != null
    val sites: List<SearchSite> = web?.sites.orEmpty()

    var query by mutableStateOf("")
        private set

    /** Dailymotion (the recommended site) starts on; the user can add others. */
    var selected by mutableStateOf(sites.filter { it.recommended }.toSet().ifEmpty { sites.take(1).toSet() })
        private set

    var state by mutableStateOf(SearchUiState())
        private set

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var running: Job? = null

    fun onQuery(value: String) {
        query = value
    }

    fun clear() {
        running?.cancel()
        query = ""
        state = SearchUiState(queued = state.queued)
    }

    /** At least one site always stays on: searching nowhere makes no sense. */
    fun toggle(site: SearchSite) {
        val next = if (site in selected) selected - site else selected + site
        if (next.isNotEmpty()) selected = next
    }

    fun search() {
        val term = query.trim()
        val engine = web ?: return
        if (term.isEmpty()) return
        running?.cancel()
        val chosen = sites.filter { it in selected }
        state = state.copy(loading = true, searched = true, hits = emptyList(), failedSites = emptyList())
        running = viewModelScope.launch {
            val results = coroutineScope {
                chosen.map { site ->
                    async {
                        site to runCatching { engine.search(site, term) }.onFailure { if (it is CancellationException) throw it }
                    }
                }.awaitAll()
            }
            state = state.copy(
                loading = false,
                hits = interleave(results.map { (_, r) -> r.getOrDefault(emptyList()) }),
                failedSites = results.filter { (_, r) -> r.isFailure }.map { it.first },
            )
        }
    }

    fun download(hit: SearchHit) {
        viewModelScope.launch {
            when (val result = repository.create(hit.url, null)) {
                is CreateResult.Created -> {
                    queue.enqueue(result.job.id)
                    state = state.copy(queued = state.queued + hit.url)
                    _messages.send(UiMessage.Res(R.string.download_started))
                }
                is CreateResult.Existing -> {
                    state = state.copy(queued = state.queued + hit.url)
                    _messages.send(UiMessage.Res(if (result.job.status.isActive) R.string.already_downloading else R.string.already_downloaded))
                }
                is CreateResult.Rejected -> _messages.send(UiMessage.Text(result.reason))
            }
        }
    }
}

/** Round-robin merge: with two sites the list alternates instead of showing one site first. */
internal fun <T> interleave(lists: List<List<T>>): List<T> {
    val out = ArrayList<T>(lists.sumOf { it.size })
    var i = 0
    while (lists.any { i < it.size }) {
        lists.forEach { if (i < it.size) out += it[i] }
        i++
    }
    return out
}
