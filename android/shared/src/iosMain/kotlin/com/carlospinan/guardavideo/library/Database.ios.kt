package com.carlospinan.guardavideo.library

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Application Support, not Documents: Documents is shared with the Files app (that is how the
 * user takes videos out), and the database has no business being there.
 */
@OptIn(ExperimentalForeignApi::class)
fun createDatabase(): AppDatabase {
    val dir = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    return Room.databaseBuilder<AppDatabase>("${requireNotNull(dir?.path)}/$DATABASE_NAME").configured()
}
