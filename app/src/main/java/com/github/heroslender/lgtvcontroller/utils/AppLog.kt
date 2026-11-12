package com.github.heroslender.lgtvcontroller.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Logcat wrapper that also keeps the latest lines in memory, so the connection log can be shared
 * from the app when something doesn't work (there's no computer attached to read logcat).
 */
object AppLog {
    private const val MAX_LINES = 500
    private val lines = ArrayDeque<String>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        add("D", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        add("W", tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        add("E", tag, message)
    }

    @Synchronized
    private fun add(level: String, tag: String, message: String) {
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast("${format.format(Date())} $level/$tag: $message")
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}
