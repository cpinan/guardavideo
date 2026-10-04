package com.carlospinan.guardavideo.app.di

import android.content.Context
import android.os.Environment
import com.carlospinan.guardavideo.download.MediaDownloader
import com.carlospinan.guardavideo.download.VideoDownloader
import com.carlospinan.guardavideo.library.AppDatabase
import com.carlospinan.guardavideo.library.DownloadRunner
import com.carlospinan.guardavideo.library.JobRepository
import com.carlospinan.guardavideo.library.createDatabase
import com.carlospinan.guardavideo.app.download.DownloadQueue
import com.carlospinan.guardavideo.app.download.DownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadsDir

/** App-wide scope for work that must outlive a screen (saving the playback position on exit). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun downloadQueue(scheduler: DownloadScheduler): DownloadQueue
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = createDatabase(context)

    @Provides
    fun fileSystem(): FileSystem = FileSystem.SYSTEM

    /**
     * App-specific external storage (Android/data/<id>/files/Movies): no storage permission
     * needed, removed with the app. Falls back to internal storage if it is not mounted.
     */
    @Provides @DownloadsDir
    fun downloadsDir(@ApplicationContext context: Context): Path =
        (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir.resolve("videos")).toOkioPath()

    @Provides @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000 // per read: a long video is fine, a stalled one is not
        }
    }

    @Provides @Singleton
    fun downloader(http: HttpClient, fs: FileSystem): VideoDownloader =
        MediaDownloader(http, fs, io = Dispatchers.IO)

    @Provides @Singleton
    fun repository(db: AppDatabase, fs: FileSystem): JobRepository =
        JobRepository(
            db.jobs(),
            fs,
            clock = System::currentTimeMillis,
            newId = { UUID.randomUUID().toString().replace("-", "").take(12) },
        )

    @Provides @Singleton
    fun runner(repo: JobRepository, downloader: VideoDownloader, fs: FileSystem, @DownloadsDir dir: Path): DownloadRunner =
        DownloadRunner(repo, downloader, fs, dir)
}
