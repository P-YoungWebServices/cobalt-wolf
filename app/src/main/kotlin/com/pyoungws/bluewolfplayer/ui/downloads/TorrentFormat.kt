package com.pyoungws.bluewolfplayer.ui.downloads

import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.qbittorrent.Torrent
import java.util.Locale

/** "12.4 MB/s" style transfer rate. */
fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"

/** "1.4 GB" style size using 1024-based units. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024
        unit++
    } while (value >= 1024 && unit < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

/** Compact ETA: "∞" when unknown, "45 s", "12 m", "2 h 10 m", "3 d 4 h". */
fun formatEta(seconds: Long?): String {
    val s = seconds ?: return "∞"
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} m"
        s < 86_400 -> "${s / 3600} h ${s % 3600 / 60} m"
        else -> "${s / 86_400} d ${s % 86_400 / 3600} h"
    }
}

/** String resource for a qBittorrent state string; null for unknown states (UI shows the raw value). */
fun torrentStateRes(state: String): Int? = when (state) {
    "downloading", "forcedDL", "metaDL", "allocating" -> R.string.torrent_state_downloading
    "stalledDL" -> R.string.torrent_state_stalled
    "queuedDL" -> R.string.torrent_state_queued
    "uploading", "stalledUP", "queuedUP", "forcedUP" -> R.string.torrent_state_seeding
    "pausedDL", "pausedUP", "stoppedDL", "stoppedUP" -> R.string.torrent_state_paused
    "checkingDL", "checkingUP", "checkingResumeData" -> R.string.torrent_state_checking
    "moving" -> R.string.torrent_state_moving
    "error", "missingFiles" -> R.string.torrent_state_error
    else -> null
}

/** One-line summary: "Downloading · 1.2 MB/s ↓ · 45 m left · 4.2 GB". */
fun Torrent.detailLine(stateLabel: String, etaSuffix: String): String {
    val parts = mutableListOf(stateLabel)
    if (downloadSpeedBps > 0) parts += "↓ ${formatSpeed(downloadSpeedBps)}"
    if (uploadSpeedBps > 0) parts += "↑ ${formatSpeed(uploadSpeedBps)}"
    if (progressFraction < 1f) parts += etaSuffix
    parts += formatBytes(sizeBytes)
    return parts.joinToString(" · ")
}
