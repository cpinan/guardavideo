package com.carlospinan.guardavideo.app.di

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * The player needs a Context; the ViewModel must not. It is built here, one per player screen
 * (ViewModel scope), and the ViewModel only sees Media3's [Player] interface.
 */
@Module
@InstallIn(ViewModelComponent::class)
object PlayerModule {
    @Provides @ViewModelScoped
    fun player(@ApplicationContext context: Context): Player = ExoPlayer.Builder(context).build()
}
