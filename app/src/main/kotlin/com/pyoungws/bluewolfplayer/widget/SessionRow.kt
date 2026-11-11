package com.pyoungws.bluewolfplayer.widget

import com.pyoungws.bluewolfplayer.shared.admin.ActiveSession

/** One row of the sessions widget: what to show for an [ActiveSession]. */
data class SessionRow(
    /** The item name, or null when the session is idle (the UI shows the localized "Idle"). */
    val title: String?,
    val subtitle: String,
    val paused: Boolean,
    /** Playback progress as a whole percentage, or null when unknown or idle. */
    val progressPercent: Int?,
)

fun ActiveSession.toSessionRow(): SessionRow = SessionRow(
    title = item?.name,
    subtitle = "$userName · $deviceName",
    paused = isPaused,
    progressPercent = item?.let { progressFraction?.let { f -> (f * 100).toInt().coerceIn(0, 100) } },
)
