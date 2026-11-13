package com.pyoungws.cobaltwolf.player

import androidx.media3.extractor.SeekMap
import com.pyoungws.cobaltwolf.shared.playback.MkvCuePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MkvCueSeekMapTest {

    private val map = MkvCueSeekMap(
        listOf(
            MkvCuePoint(0, 100),
            MkvCuePoint(10_000, 1_000_000),
            MkvCuePoint(20_000, 2_000_000),
        ),
        durationUs = 60_000_000,
    )

    @Test
    fun `reports itself seekable with the given duration`() {
        assertTrue(map.isSeekable)
        assertEquals(60_000_000, map.durationUs)
    }

    @Test
    fun `an exact cue time returns the same point twice`() {
        val points = map.getSeekPoints(10_000_000)
        assertEquals(1_000_000, points.first.position)
        assertEquals(1_000_000, points.second.position)
    }

    @Test
    fun `a time between cues brackets it with the surrounding keyframes`() {
        val points = map.getSeekPoints(15_000_000)
        assertEquals(10_000_000, points.first.timeUs)
        assertEquals(1_000_000, points.first.position)
        assertEquals(20_000_000, points.second.timeUs)
        assertEquals(2_000_000, points.second.position)
    }

    @Test
    fun `a time before the first cue clamps to the first point`() {
        val points = map.getSeekPoints(0)
        assertEquals(100, points.first.position)
        assertEquals(100, points.second.position)
    }

    @Test
    fun `a time after the last cue clamps to the last point`() {
        val points = map.getSeekPoints(59_000_000)
        assertEquals(2_000_000, points.first.position)
        assertEquals(2_000_000, points.second.position)
    }
}
