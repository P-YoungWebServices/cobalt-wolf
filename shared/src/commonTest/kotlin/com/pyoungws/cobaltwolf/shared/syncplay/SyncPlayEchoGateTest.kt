package com.pyoungws.cobaltwolf.shared.syncplay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPlayEchoGateTest {
    private var now = 0L
    private fun gate() = SyncPlayEchoGate(nowMs = { now })

    @Test
    fun `a local pause after a remote pause is suppressed`() {
        val gate = gate()
        gate.remoteApplied(SyncPlayEchoGate.Kind.Pause, 100_000_000)
        assertTrue(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Pause, 100_000_000))
        // The record is consumed: a later real pause is reported.
        assertFalse(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Pause, 100_000_000))
    }

    @Test
    fun `a local seek matching a remote seek is suppressed, a different one is not`() {
        val gate = gate()
        gate.remoteApplied(SyncPlayEchoGate.Kind.Seek, 500_000_000)
        assertTrue(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Seek, 505_000_000))

        gate.remoteApplied(SyncPlayEchoGate.Kind.Seek, 500_000_000)
        assertFalse(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Seek, 900_000_000))
    }

    @Test
    fun `a remote resume suppresses the follow-up seek and resume`() {
        val gate = gate()
        gate.remoteApplied(SyncPlayEchoGate.Kind.Resume, 200_000_000)
        // The player seeks to the resume position first…
        assertTrue(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Seek, 200_000_000))
        // …and a later resume event is a real user action (the seek consumed the record).
        gate.remoteApplied(SyncPlayEchoGate.Kind.Resume, 200_000_000)
        assertTrue(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Resume, 200_000_000))
    }

    @Test
    fun `records expire after the window`() {
        val gate = gate()
        gate.remoteApplied(SyncPlayEchoGate.Kind.Pause, 0)
        now += SyncPlayEchoGate.DEFAULT_WINDOW_MS + 1
        assertFalse(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Pause, 0))
    }

    @Test
    fun `kinds do not cross-match`() {
        val gate = gate()
        gate.remoteApplied(SyncPlayEchoGate.Kind.Pause, 0)
        assertFalse(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Resume, 0))
        assertFalse(gate.suppressLocalEcho(SyncPlayEchoGate.Kind.Seek, 0))
    }
}
