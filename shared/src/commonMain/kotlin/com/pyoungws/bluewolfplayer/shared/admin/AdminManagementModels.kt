package com.pyoungws.bluewolfplayer.shared.admin

/** A user account as an administrator sees it. Timestamps are ISO-8601 strings; null when unknown. */
data class AdminUser(
    val id: String,
    val name: String,
    val isAdministrator: Boolean,
    val isDisabled: Boolean,
    val isHidden: Boolean,
    val hasPassword: Boolean,
    val lastLogin: String?,
    val lastActivity: String?,
)

/** Changes an administrator can apply to an account; null fields keep their current value. */
data class AdminUserEdit(
    val name: String? = null,
    val isAdministrator: Boolean? = null,
    val isDisabled: Boolean? = null,
    val isHidden: Boolean? = null,
    /** New password; null leaves it unchanged, empty clears it. */
    val password: String? = null,
)

/** A device the server has seen a client connect from. */
data class AdminDevice(
    val id: String,
    val name: String,
    /** Admin-assigned name shown in dashboards; falls back to [name] for display. */
    val customName: String?,
    val appName: String?,
    val appVersion: String?,
    val lastUserName: String?,
    val lastActivity: String?,
) {
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: name
}

/** A media library folder configured on the server. */
data class AdminLibrary(
    val id: String,
    val name: String,
    /** Jellyfin collection type, e.g. "movies" or "tvshows"; null for mixed/unknown. */
    val collectionType: String?,
    val locations: List<String>,
    /** Set while a library scan is in progress. */
    val refreshStatus: String?,
    /** Scan progress in 0..1, or null when no scan is running. */
    val refreshProgress: Float?,
)

enum class AdminTaskState { Idle, Running, Cancelling }

/** A scheduled task on the server, with its live state and last outcome. */
data class ScheduledTask(
    val id: String,
    val name: String,
    val category: String?,
    val description: String?,
    val state: AdminTaskState,
    /** Progress in 0..1 while running, or null when the task does not report progress. */
    val progressFraction: Float?,
    /** ISO-8601 timestamp of the last completed run, or null when it never ran. */
    val lastRun: String?,
    /** Outcome of the last run ("Completed", "Failed", ...), or null when it never ran. */
    val lastResult: String?,
)

/** One log file the server offers through `System/Logs`. */
data class ServerLogFile(
    val name: String,
    val sizeBytes: Long,
    val modified: String?,
)

/** Severity parsed from a Jellyfin log line's `[INF]`/`[WRN]`/... tag. */
enum class LogSeverity { Debug, Info, Warning, Error, Fatal }

/** A raw log line with its severity. Continuation lines inherit the previous line's severity. */
data class ServerLogLine(
    val severity: LogSeverity?,
    val text: String,
)

private val LOG_SEVERITY = Regex("""^\[[^\]]*\]\s*\[(DBG|INF|WRN|ERR|FTL)\]""")

private fun severityOf(tag: String): LogSeverity? = when (tag) {
    "DBG" -> LogSeverity.Debug
    "INF" -> LogSeverity.Info
    "WRN" -> LogSeverity.Warning
    "ERR" -> LogSeverity.Error
    "FTL" -> LogSeverity.Fatal
    else -> null
}

/**
 * Parses a Jellyfin log file into lines tagged with severity. Lines without a severity tag
 * (e.g. exception stack traces) belong to the entry above them and inherit its severity.
 */
fun parseServerLog(content: String): List<ServerLogLine> {
    var current: LogSeverity? = null
    return content.lines().mapNotNull { raw ->
        val line = raw.trimEnd('\r')
        if (line.isBlank()) return@mapNotNull null
        val tag = LOG_SEVERITY.find(line)?.groupValues?.get(1)
        tag?.let { current = severityOf(it) }
        ServerLogLine(severity = current, text = line)
    }
}

/**
 * Filters log lines by minimum severity and a case-insensitive substring.
 * Lines whose severity could not be determined only pass the [minimum] filter when it is null.
 */
fun List<ServerLogLine>.filteredBy(minimum: LogSeverity?, query: String): List<ServerLogLine> {
    val needle = query.trim()
    return filter { line ->
        (minimum == null || (line.severity != null && line.severity >= minimum)) &&
            (needle.isEmpty() || line.text.contains(needle, ignoreCase = true))
    }
}

/** One storage location reported by the server (a system folder or a library's folders combined). */
data class StorageVolume(
    val label: String,
    val path: String?,
    val usedBytes: Long,
    val freeBytes: Long,
)

/** Server identity, capabilities and update status for the system information screen. */
data class SystemInformation(
    val serverName: String?,
    val version: String?,
    val productName: String?,
    val operatingSystem: String?,
    val architecture: String?,
    val localAddress: String?,
    val packageName: String?,
    val hasPendingRestart: Boolean,
    val hasUpdateAvailable: Boolean,
    val webSocketPort: Int,
    val storage: List<StorageVolume>,
)
