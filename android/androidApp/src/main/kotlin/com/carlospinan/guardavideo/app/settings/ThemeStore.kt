package com.carlospinan.guardavideo.app.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Like the web toggle: follow the system, or force light / dark. */
enum class ThemeMode { System, Light, Dark;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

interface ThemeStore {
    val mode: StateFlow<ThemeMode>

    fun set(mode: ThemeMode)
}

@Singleton
class PrefsThemeStore @Inject constructor(@ApplicationContext context: Context) : ThemeStore {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ThemeMode.System),
    )
    override val mode: StateFlow<ThemeMode> = state

    override fun set(mode: ThemeMode) {
        state.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val KEY = "theme"
    }
}
