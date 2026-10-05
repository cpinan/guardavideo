package com.carlospinan.guardavideo.player

import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val job: Job? = null,
    val isPlaying: Boolean = false,
    /** Width / height of the video, for the Picture-in-Picture window. */
    val aspectWidth: Int = 16,
    val aspectHeight: Int = 9,
    /** The job or its file is gone: nothing to play. */
    val missing: Boolean = false,
    val error: String? = null,
)

/**
 * Facade over a [VideoPlayer] with Guardavideo's playback rules, shared by Android and iOS:
 * resume where the user stopped, save the position every few seconds and on pause/close, and
 * count one view per opening. Screens talk to this; the platform player stays swappable.
 *
 * Threads: [scope] runs the save loop and must be the player's thread (main: ExoPlayer and
 * AVPlayer are main-thread objects); [persistScope] writes to the database and outlives the
 * screen, so the last position is saved even after it closes. [fileExists] checks the disk.
 */
class PlaybackController(
    private val player: VideoPlayer,
    private val repository: JobRepository,
    private val scope: CoroutineScope,
    private val fileExists: (String) -> Boolean,
    private val persistScope: CoroutineScope = scope,
    private val saveEveryMs: Long = 3_000,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var jobId: String? = null
    private var counted = false
    private var saver: CoroutineJob? = null

    private val listener = object : VideoPlayerListener {
        override fun onPlayingChanged(playing: Boolean) {
            _state.update { it.copy(isPlaying = playing) }
            if (playing && !counted) { // one view per opening
                counted = true
                jobId?.let { id -> persistScope.launch { repository.recordView(id) } }
            }
            if (!playing) savePosition()
        }

        override fun onVideoSize(width: Int, height: Int) {
            if (width > 0 && height > 0) _state.update { it.copy(aspectWidth = width, aspectHeight = height) }
        }

        override fun onEnded() = savePosition()

        override fun onError(message: String) = _state.update { it.copy(error = message) }
    }

    /** Loads the job's file at its saved position and starts playing. */
    suspend fun open(id: String) {
        jobId = id
        counted = false
        player.setListener(listener)
        val job = repository.get(id)
        val path = job?.filePath
        if (job == null || path == null || !fileExists(path)) {
            _state.value = PlayerUiState(job = job, missing = true)
            return
        }
        _state.value = PlayerUiState(job = job)
        player.load(PlayableMedia.forFile(path), startAtMs = if (job.watched) 0 else job.positionMs, playWhenReady = true)
        saver?.cancel()
        saver = scope.launch {
            while (isActive) {
                delay(saveEveryMs)
                if (player.isPlaying) savePosition()
            }
        }
    }

    fun play() = player.play()

    fun pause() {
        player.pause()
        savePosition()
    }

    /** Saves where the user is and frees the player. Call once, when the screen goes away. */
    fun close() {
        saver?.cancel()
        if (!_state.value.missing) savePosition()
        player.setListener(null)
        player.dispose()
    }

    private fun savePosition() {
        val id = jobId ?: return
        if (_state.value.missing) return
        val position = player.positionMs // read here, on the player's thread
        val duration = player.durationMs.takeIf { it > 0 }
        persistScope.launch { repository.savePosition(id, position, duration) }
    }
}
