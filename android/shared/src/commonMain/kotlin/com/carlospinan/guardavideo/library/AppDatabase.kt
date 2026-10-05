package com.carlospinan.guardavideo.library

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Schema changes: bump [version], add a Migration, and keep the exported JSON in
 * shared/schemas (checked in) so migrations can be verified.
 */
@Database(
    entities = [JobEntity::class],
    version = 2,
    exportSchema = true,
    // v2: view_count, last_viewed_at, position_updated_at (new nullable / defaulted columns).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao
}

// Room's KSP processor generates the actual implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

const val DATABASE_NAME = "guardavideo.db"

/** Same driver and dispatcher on every platform: bundled SQLite, queries off the main thread. */
fun RoomDatabase.Builder<AppDatabase>.configured(): AppDatabase =
    setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
