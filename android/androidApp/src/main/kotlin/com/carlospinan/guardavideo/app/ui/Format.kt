package com.carlospinan.guardavideo.app.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import android.content.Context

fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

/** "hace 5 minutos", in the device language. */
fun formatWhen(epochMs: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS).toString()

/** 1:05:09 / 4:07 */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
