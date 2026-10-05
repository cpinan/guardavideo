package com.carlospinan.guardavideo.app.di

import android.content.Context
import com.carlospinan.guardavideo.app.media.ExoVideoPlayer
import com.carlospinan.guardavideo.player.VideoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * The player needs a Context; the ViewModel must not. One per player screen (ViewModel scope),
 * exposed as the shared [VideoPlayer] interface (Media3 ExoPlayer underneath).
 */
@Module
@InstallIn(ViewModelComponent::class)
object PlayerModule {
    @Provides @ViewModelScoped
    fun player(@ApplicationContext context: Context): VideoPlayer = ExoVideoPlayer(context)
}
