package com.carlospinan.guardavideo.app.di

import android.content.Context
import com.carlospinan.guardavideo.app.cast.CastVideoPlayer
import com.carlospinan.guardavideo.app.cast.LanMediaServer
import com.carlospinan.guardavideo.app.cast.SwitchingVideoPlayer
import com.carlospinan.guardavideo.app.media.ExoVideoPlayer
import com.carlospinan.guardavideo.player.VideoPlayer
import com.google.android.gms.cast.framework.CastContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * The player needs a Context; the ViewModel must not. One per player screen (ViewModel scope),
 * exposed as the shared [VideoPlayer] interface: ExoPlayer here, CastPlayer on a Chromecast.
 */
@Module
@InstallIn(ViewModelComponent::class)
object PlayerModule {
    @Provides @ViewModelScoped
    fun player(@ApplicationContext context: Context, server: LanMediaServer): VideoPlayer {
        // No Google Play services (or Cast disabled): local playback only, never a crash.
        val cast = runCatching { CastContext.getSharedInstance(context) }.getOrNull()
            ?.let { CastVideoPlayer(context, it, server) }
        return SwitchingVideoPlayer(ExoVideoPlayer(context), cast)
    }
}
