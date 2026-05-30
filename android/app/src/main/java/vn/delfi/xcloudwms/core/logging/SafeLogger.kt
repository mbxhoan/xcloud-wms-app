package vn.delfi.xcloudwms.core.logging

import android.util.Log

enum class LogLevel {
    DEBUG,
    INFO,
    ERROR,
}

class SafeLogger(
    private val isDebug: Boolean,
    private val minimumLevel: LogLevel = LogLevel.DEBUG,
) {
    private val sensitivePatterns = listOf(
        Regex("(?i)(bearer\\s+)[^\\s]+"),
        Regex("(?i)(password=)[^&\\s]+"),
        Regex("(?i)(token=)[^&\\s]+"),
        Regex("(?i)(authorization:)[^\\n]+"),
    )

    fun debug(tag: String, message: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            Log.d(tag, sanitize(message))
        }
    }

    fun info(tag: String, message: String) {
        if (shouldLog(LogLevel.INFO)) {
            Log.i(tag, sanitize(message))
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (!shouldLog(LogLevel.ERROR)) {
            return
        }

        val safeMessage = sanitize(message)
        if (throwable != null && isDebug) {
            Log.e(tag, safeMessage, throwable)
            return
        }

        Log.e(tag, safeMessage)
    }

    private fun shouldLog(level: LogLevel): Boolean {
        if (!isDebug && level == LogLevel.DEBUG) {
            return false
        }
        return level.ordinal >= minimumLevel.ordinal
    }

    private fun sanitize(message: String): String {
        return sensitivePatterns.fold(message) { current, pattern ->
            current.replace(pattern) { match ->
                val groupValue = match.groups[1]?.value.orEmpty()
                "${groupValue}***"
            }
        }
    }
}
