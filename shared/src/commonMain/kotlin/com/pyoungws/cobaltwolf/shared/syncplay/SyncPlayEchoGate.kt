package com.pyoungws.cobaltwolf.shared.syncplay

import kotlin.math.abs
import kotlin.time.TimeSource

/**
 * Distinguishes a player event caused by a SyncPlay command from a real local action. Applying a
 * remote pause/seek/resume fires the same player listeners the user's own transport keys do;
 * reporting them back would echo every remote command to the group a second time. The UI records
 * each remote command with [remoteApplied] right before applying it, then asks [suppressLocalEcho]
 * for every local-looking event.
 */
class SyncPlayEchoGate(
    private val nowMs: () -> Long = { TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val toleranceTicks: Long = DEFAULT_TOLERANCE_TICKS,
) {
    enum class Kind { Pause, Resume, Seek }

    private var appliedKind: Kind? = null
    private var appliedTicks: Long = 0
    private var appliedAtMs: Long = 0

    /** Records a remote command as it is applied to the player. */
    fun remoteApplied(kind: Kind, positionTicks: Long) {
        appliedKind = kind
        appliedTicks = positionTicks
        appliedAtMs = nowMs()
    }

    /**
     * True when the local event is the echo of the last remote command: it is consumed and must
     * not be reported to the group. A real local action is unaffected.
     */
    fun suppressLocalEcho(kind: Kind, positionTicks: Long): Boolean {
        val applied = appliedKind ?: return false
        if (nowMs() - appliedAtMs > windowMs) {
            appliedKind = null
            return false
        }
        val matches = when (applied) {
            Kind.Pause -> kind == Kind.Pause
            Kind.Resume -> kind == Kind.Resume ||
                (kind == Kind.Seek && abs(positionTicks - appliedTicks) <= toleranceTicks)
            Kind.Seek -> kind == Kind.Seek && abs(positionTicks - appliedTicks) <= toleranceTicks
        }
        if (matches) appliedKind = null
        return matches
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
        /** ~1.5 s of drift still counts as the same position. */
        const val DEFAULT_TOLERANCE_TICKS = 15_000_000L
    }
}
