package com.carlospinan.guardavideo.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlospinan.guardavideo.app.R
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.library.CreateResult
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.source.LinkSource
import com.carlospinan.guardavideo.source.detectSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-off message for the snackbar: a string resource, or text that already comes translated. */
sealed interface UiMessage {
    data class Res(val id: Int) : UiMessage

    data class Text(val text: String) : UiMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: JobRepository,
    private val queue: DownloadQueue,
) : ViewModel() {

    /** Server state: the library, straight from the database (single source of truth). */
    val jobs: StateFlow<List<Job>?> = repository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Text field state stays synchronous (Compose guidance for TextField in a ViewModel).
    var url by mutableStateOf("")
        private set
    var quality by mutableStateOf<Int?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set

    val source: LinkSource get() = detectSource(url)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun onUrlChange(value: String) {
        url = value
    }

    fun onQualityChange(value: Int?) {
        quality = value
    }

    fun submit() {
        if (submitting || !source.canDownload) return
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

    fun toggleStar(job: Job) = viewModelScope.launch { repository.setStarred(job.id, !job.starred) }
}
