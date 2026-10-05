package com.carlospinan.guardavideo.app.ui.player

import android.util.Rational
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.carlospinan.guardavideo.app.PlayerRoute
import com.carlospinan.guardavideo.app.di.ApplicationScope
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.player.PlaybackController
import com.carlospinan.guardavideo.player.PlayerUiState
import com.carlospinan.guardavideo.player.VideoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Thin Android wrapper around the shared [PlaybackController]: it owns the controller for the
 * screen's lifetime (so playback survives rotation) and exposes its state. All playback rules
 * (resume, periodic save, view count) live in the shared module, reused by iOS.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    val player: VideoPlayer,
    repository: JobRepository,
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {
    private val controller = PlaybackController(
        player = player,
        repository = repository,
        scope = viewModelScope, // main thread: ExoPlayer is a main-thread object
        fileExists = { File(it).exists() },
        persistScope = appScope, // the last save must outlive this screen
    )

    val state: StateFlow<PlayerUiState> = controller.state

    init {
        viewModelScope.launch { controller.open(savedState.toRoute<PlayerRoute>().id) }
    }

    fun pause() = controller.pause()

    override fun onCleared() = controller.close()
}

/** PiP accepts aspect ratios between 1:2.39 and 2.39:1; clamp anything outside. */
internal fun pipSafe(width: Int, height: Int): Rational {
    val ratio = width.toFloat() / height
    return when {
        ratio > 2.39f -> Rational(239, 100)
        ratio < 1 / 2.39f -> Rational(100, 239)
        else -> Rational(width, height)
    }
}
