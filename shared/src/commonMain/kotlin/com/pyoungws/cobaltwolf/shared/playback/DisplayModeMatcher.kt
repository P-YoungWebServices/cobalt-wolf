package com.pyoungws.cobaltwolf.shared.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/** Refresh periods this close (in Hz per content frame) repeat no frames. */
private const val EXACT_TOLERANCE_HZ = 0.01f

/** 23.976 fps on a 24.000 Hz mode (or 29.97 on 60) repeats a frame every 40 s or so, far better than 3:2 pulldown. */
private const val NEAR_TOLERANCE_HZ = 0.035f

/**
 * Picks the display mode that shows [frameRate] content without judder: one at the active
 * resolution whose refresh rate is a whole multiple of the frame rate, exact matches first and
 * then the lowest multiple. Returns null when the active mode is already as good as any other,
 * or nothing matches.
 */
fun chooseDisplayMode(modes: List<DisplayMode>, activeModeId: Int?, frameRate: Float): DisplayMode? {
    val active = modes.firstOrNull { it.id == activeModeId } ?: return null
    if (frameRate <= 0f) return null
    val best = modes
        .filter { it.width == active.width && it.height == active.height }
        .mapNotNull { mode -> matchQuality(mode, frameRate)?.let { mode to it } }
        .minWithOrNull(compareBy({ it.second.first }, { it.second.second }))
        ?: return null
    val activeQuality = matchQuality(active, frameRate)
    return if (activeQuality != null && activeQuality.first <= best.second.first) null else best.first
}

/** (0 = exact or 1 = near, refresh multiple), or null when [mode] would judder. */
private fun matchQuality(mode: DisplayMode, frameRate: Float): Pair<Int, Int>? {
    val multiple = (mode.refreshRate / frameRate).roundToInt()
    if (multiple < 1) return null
    val error = abs(mode.refreshRate - multiple * frameRate) / multiple
    return when {
        error < EXACT_TOLERANCE_HZ -> 0 to multiple
        error < NEAR_TOLERANCE_HZ -> 1 to multiple
        else -> null
    }
}
