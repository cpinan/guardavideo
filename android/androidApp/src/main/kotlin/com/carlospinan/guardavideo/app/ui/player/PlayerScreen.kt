package com.carlospinan.guardavideo.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Share
import com.carlospinan.guardavideo.app.ui.shareOriginalLink
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import com.carlospinan.guardavideo.app.MainActivity
import com.carlospinan.guardavideo.app.media.ExoVideoPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.carlospinan.guardavideo.app.R

@Composable
fun PlayerScreen(onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val missing = state.missing
    val playing = state.isPlaying
    val aspect = remember(state.aspectWidth, state.aspectHeight) { pipSafe(state.aspectWidth, state.aspectHeight) }
    val activity = LocalActivity.current as? MainActivity
    val inPip = rememberIsInPipMode(activity)
    val pipParams = remember(playing, aspect) {
        PictureInPictureParams.Builder().setAspectRatio(aspect).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(playing)
        }.build()
    }
    // Leaving the app while a video plays shrinks it into a floating window instead of stopping.
    DisposableEffect(activity, pipParams) {
        activity?.pipParams = pipParams.takeIf { playing && !missing }
        activity?.setPictureInPictureParams(pipParams)
        onDispose {
            activity?.pipParams = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }
    ImmersiveMode()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() } // leaving the app pauses and saves

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (missing) {
            Text(stringResource(R.string.player_missing), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        } else {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        // The only Media3-specific line in the screen: bind the native surface.
                        player = (viewModel.player as ExoVideoPlayer).media3
                        keepScreenOn = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                update = { view -> view.useController = !inPip }, // the PiP window has system controls
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!inPip) {
            val buttonColors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
            Row(Modifier.safeDrawingPadding().padding(8.dp).fillMaxWidth()) {
                IconButton(onClick = onBack, colors = buttonColors) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Spacer(Modifier.weight(1f))
                state.job?.let { job ->
                    val context = LocalContext.current
                    IconButton(onClick = { shareOriginalLink(context, job) }, colors = buttonColors) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_link))
                    }
                }
                if (!missing) {
                    IconButton(onClick = { activity?.enterPictureInPictureMode(pipParams) }, colors = buttonColors) {
                        Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = stringResource(R.string.pip))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberIsInPipMode(activity: ComponentActivity?): Boolean {
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Hides the status and navigation bars while the player is open; swipe from the edge shows them. */
@Composable
private fun ImmersiveMode() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
