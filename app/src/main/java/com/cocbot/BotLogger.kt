package com.cocbot

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel { INFO, ACTION, SCAN, WAIT, REKAP, ANTIBOT, SYSTEM, UPGRADE, MENU, ERROR, WARNING }

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

object BotLogger {

    private const val TAG = "COCBot"
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val logList = mutableListOf<LogEntry>()

    fun log(level: LogLevel, message: String) {
        val time = timeFormat.format(Date())
        val entry = LogEntry(time, level, message)
        logList.add(entry)

        // Keep max 500 log entries
        if (logList.size > 500) logList.removeAt(0)

        _logs.value = logList.toList()

        // Juga log ke Android logcat
        val formatted = "[$time] [${level.name}] $message"
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, formatted)
            LogLevel.WARNING -> Log.w(TAG, formatted)
            else -> Log.d(TAG, formatted)
        }
    }

    fun info(msg: String) = log(LogLevel.INFO, msg)
    fun action(msg: String) = log(LogLevel.ACTION, msg)
    fun scan(msg: String) = log(LogLevel.SCAN, msg)
    fun wait(msg: String) = log(LogLevel.WAIT, msg)
    fun rekap(msg: String) = log(LogLevel.REKAP, msg)
    fun antibot(msg: String) = log(LogLevel.ANTIBOT, msg)
    fun system(msg: String) = log(LogLevel.SYSTEM, msg)
    fun upgrade(msg: String) = log(LogLevel.UPGRADE, msg)
    fun menu(msg: String) = log(LogLevel.MENU, msg)
    fun error(msg: String) = log(LogLevel.ERROR, msg)
    fun warning(msg: String) = log(LogLevel.WARNING, msg)

    fun clear() {
        logList.clear()
        _logs.value = emptyList()
    }
}
