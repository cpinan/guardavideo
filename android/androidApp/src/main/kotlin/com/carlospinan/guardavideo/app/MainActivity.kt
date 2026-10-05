package com.carlospinan.guardavideo.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carlospinan.guardavideo.app.ui.history.HistoryScreen
import com.carlospinan.guardavideo.app.ui.library.LibraryScreen
import com.carlospinan.guardavideo.app.ui.providers.ProvidersScreen
import com.carlospinan.guardavideo.app.ui.library.extractUrl
import com.carlospinan.guardavideo.app.ui.player.PlayerScreen
import com.carlospinan.guardavideo.app.ui.theme.GuardavideoTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlospinan.guardavideo.app.settings.ThemeStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class PlayerRoute(val id: String)

@Serializable
object HistoryRoute

@Serializable
object ProvidersRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeStore: ThemeStore

    /** Set by the player screen while a video plays; used to enter PiP on Android 10-11. */
    var pipParams: PictureInPictureParams? = null

    /** Android 12+ enters PiP by itself (auto-enter); older versions need this hint. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val params = pipParams
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && params != null && !isInPictureInPictureMode) {
            enterPictureInPictureMode(params)
        }
    }

    /** A link shared from another app ("Share → Guardavideo"), waiting to fill the URL field. */
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedUrl = sharedLink(intent)
        setContent {
            val themeMode by themeStore.mode.collectAsStateWithLifecycle()
            GuardavideoTheme(themeMode) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = LibraryRoute) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            sharedUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrl = null },
                            onPlay = { id -> nav.navigate(PlayerRoute(id)) },
                            themeMode = themeMode,
                            onToggleTheme = { themeStore.set(themeMode.next()) },
                            onHistory = { nav.navigate(HistoryRoute) },
                            onProviders = { nav.navigate(ProvidersRoute) },
                        )
                    }
                    composable<HistoryRoute> {
                        HistoryScreen(onBack = { nav.popBackStack() }, onOpenVideo = { id -> nav.navigate(PlayerRoute(id)) })
                    }
                    composable<ProvidersRoute> { ProvidersScreen(onBack = { nav.popBackStack() }) }
                    composable<PlayerRoute> { PlayerScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedLink(intent)?.let { sharedUrl = it }
    }

    private fun sharedLink(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::extractUrl) else null
}
