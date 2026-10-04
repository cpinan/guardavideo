package com.carlospinan.guardavideo.app.ui.player

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.toRoute
import com.carlospinan.guardavideo.app.PlayerRoute
import com.carlospinan.guardavideo.app.di.ApplicationScope
import com.carlospinan.guardavideo.library.Job
import com.carlospinan.guardavideo.library.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Owns the player so playback survives rotation. The position is saved every few seconds
 * and when leaving, so the next play resumes where the user stopped.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    val player: Player,
    private val repository: JobRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val id = savedState.toRoute<PlayerRoute>().id

    private val _job = MutableStateFlow<Job?>(null)
    val job: StateFlow<Job?> = _job

    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing

    init {
        viewModelScope.launch {
            val job = repository.get(id)
            _job.value = job
            val file = job?.filePath?.let(::File)
            if (file == null || !file.exists()) {
                _missing.value = true
                return@launch
            }
            player.setMediaItem(MediaItem.fromUri(file.toUri()), job.positionMs)
            player.prepare()
            player.playWhenReady = true
        }
        viewModelScope.launch {
            while (isActive) {
                delay(SAVE_EVERY_MS)
                if (player.isPlaying) savePosition()
            }
        }
    }

    fun pause() {
        player.pause()
        savePosition()
    }

    private fun savePosition() {
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 }
        appScope.launch { repository.savePosition(id, position, duration) } // outlives this screen
    }

    override fun onCleared() {
        if (_job.value != null && !_missing.value) savePosition()
        player.release()
    }

    private companion object {
        const val SAVE_EVERY_MS = 3_000L
    }
}
