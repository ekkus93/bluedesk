package com.augustusmachin.android_bt_kbmouse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    enum class Level { ALL, INFO, ERROR }

    private const val MAX_LINES = 500

    @Volatile private var enabled: Boolean = false

    @Volatile private var level: Level = Level.ALL

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines = _lines.asStateFlow()

    private val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun setEnabled(v: Boolean) {
        enabled = v
    }

    fun setLevel(l: Level) {
        level = l
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun log(
        tag: String,
        msg: String,
    ) {
        if (!enabled) return
        if (level == Level.ERROR) return
        val line = "${ts.format(Date())} [$tag] $msg"
        append(line)
    }

    fun e(
        tag: String,
        msg: String,
    ) {
        if (!enabled) return
        if (level == Level.INFO) return
        val line = "${ts.format(Date())} E [$tag] $msg"
        append(line)
    }

    private fun append(line: String) {
        val cur = _lines.value
        val next = if (cur.size >= MAX_LINES) cur.drop(cur.size - (MAX_LINES - 1)) + line else cur + line
        _lines.value = next
    }

    fun dump(): String = _lines.value.joinToString("\n")
}
