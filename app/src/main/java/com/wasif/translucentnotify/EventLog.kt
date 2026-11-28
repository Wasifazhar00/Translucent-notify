package com.wasif.translucentnotify

import android.os.SystemClock

/**
 * Tiny in-memory flight recorder (last 80 events, no files, no extra work). Shown in the app under
 * "Capture windows" so a miss or a late start can be read afterwards, with times.
 */
object EventLog {
    private const val MAX = 80
    private val times = ArrayDeque<Long>()
    private val texts = ArrayDeque<String>()

    @Synchronized
    fun add(text: String) {
        if (texts.size >= MAX) {
            times.removeFirst()
            texts.removeFirst()
        }
        times.addLast(SystemClock.uptimeMillis())
        texts.addLast(text)
    }

    /** Newest last, each line with how long ago it happened. */
    @Synchronized
    fun dump(): String {
        val now = SystemClock.uptimeMillis()
        val sb = StringBuilder()
        for (i in texts.indices) {
            sb.append(String.format("%7d ms ago  %s\n", now - times[i], texts[i]))
        }
        return sb.toString()
    }
}
