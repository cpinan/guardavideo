package com.carlospinan.guardavideo.app.di

import com.carlospinan.guardavideo.app.media.FragmentedMp4Fixer
import com.carlospinan.guardavideo.app.media.SiteSupport
import com.carlospinan.guardavideo.download.VideoDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Play flavor: direct links only. No yt-dlp, no downloaded code, no bundled ffmpeg (Play policy:
 * no YouTube/site downloading, no code fetched outside Play, 16 KB-page native libraries).
 */
@Module
@InstallIn(SingletonComponent::class)
object FlavorModule {
    @Provides
    fun siteSupport(): SiteSupport = object : SiteSupport {
        override val downloader: VideoDownloader? = null
    }

    // Fragmented MP4s mostly come from yt-dlp; ExoPlayer plays them fine as they are.
    @Provides
    fun fragmentedMp4Fixer(): FragmentedMp4Fixer = FragmentedMp4Fixer { _, _ -> false }
}
