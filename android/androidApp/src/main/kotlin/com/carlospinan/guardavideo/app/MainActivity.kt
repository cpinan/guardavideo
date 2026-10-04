package com.carlospinan.guardavideo.app

import android.content.Intent
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
import com.carlospinan.guardavideo.app.ui.library.LibraryScreen
import com.carlospinan.guardavideo.app.ui.library.extractUrl
import com.carlospinan.guardavideo.app.ui.player.PlayerScreen
import com.carlospinan.guardavideo.app.ui.theme.GuardavideoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class PlayerRoute(val id: String)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** A link shared from another app ("Share → Guardavideo"), waiting to fill the URL field. */
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedUrl = sharedLink(intent)
        setContent {
            GuardavideoTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = LibraryRoute) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            sharedUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrl = null },
                            onPlay = { id -> nav.navigate(PlayerRoute(id)) },
                        )
                    }
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
