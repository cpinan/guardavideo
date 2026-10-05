package com.carlospinan.guardavideo.app.di

import com.carlospinan.guardavideo.app.media.FfmpegFaststart
import com.carlospinan.guardavideo.app.media.FragmentedMp4Fixer
import com.carlospinan.guardavideo.app.media.SiteSupport
import com.carlospinan.guardavideo.app.media.YtDlpSiteDownloader
import com.carlospinan.guardavideo.app.media.YtDlpUpdater
import com.carlospinan.guardavideo.download.VideoDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Full flavor (sideload): yt-dlp for site pages and the bundled ffmpeg for fragmented MP4s. */
@Module
@InstallIn(SingletonComponent::class)
object FlavorModule {
    @Provides @Singleton
    fun siteSupport(ytDlp: YtDlpSiteDownloader, updater: YtDlpUpdater): SiteSupport = object : SiteSupport {
        override val downloader: VideoDownloader = ytDlp

        override suspend fun maintenance() {
            updater.updateIfStale()
        }
    }

    @Provides
    fun fragmentedMp4Fixer(ffmpeg: FfmpegFaststart): FragmentedMp4Fixer = FragmentedMp4Fixer(ffmpeg::copy)
}
