package com.carlospinan.guardavideo.library

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createDatabase(): AppDatabase {
    val dir = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
    return Room.databaseBuilder<AppDatabase>("${requireNotNull(dir?.path)}/$DATABASE_NAME").configured()
}
