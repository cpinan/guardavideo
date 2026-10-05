package com.carlospinan.guardavideo.app.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.carlospinan.guardavideo.app.R
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * The standard Cast button (it opens the device picker and shows the connected state). It hides
 * itself while no Cast device is on the network, and is not shown at all without Play services.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val available = remember { runCatching { CastContext.getSharedInstance(context) }.isSuccess }
    if (!available) return
    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ContextThemeWrapper(ctx, R.style.Theme_Guardavideo_Cast)).also { button ->
                CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, button)
            }
        },
        modifier = modifier,
    )
}
