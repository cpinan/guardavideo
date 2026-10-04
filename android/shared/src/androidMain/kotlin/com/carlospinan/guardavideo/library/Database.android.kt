package com.carlospinan.guardavideo.library

import android.content.Context
import androidx.room.Room

fun createDatabase(context: Context): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context.applicationContext, context.getDatabasePath(DATABASE_NAME).absolutePath)
        .configured()
