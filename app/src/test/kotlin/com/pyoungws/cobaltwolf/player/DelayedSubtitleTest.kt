package com.pyoungws.cobaltwolf.player

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DelayedSubtitleTest {

    private val cue = Cue.Builder().setText("hi").build()

    private val subtitle = object : Subtitle {
        override fun getNextEventTimeIndex(timeUs: Long) = if (timeUs < 1_000_000L) 0 else 1
        override fun getEventTimeCount() = 1
        override fun getEventTime(index: Int) = 1_000_000L
        override fun getCues(timeUs: Long) = if (timeUs >= 1_000_000L) listOf(cue) else emptyList()
    }

    @Test
    fun `a positive delay shifts event times later and queries earlier`() {
        val delayed = DelayedSubtitle(subtitle) { 500_000L }

        assertEquals(1_500_000L, delayed.getEventTime(0))
        // At the original cue time nothing is due yet; the cue lands half a second later.
        assertEquals(0, delayed.getNextEventTimeIndex(1_000_000L))
        assertEquals(1, delayed.getNextEventTimeIndex(1_500_000L))
        assertEquals(emptyList<Cue>(), delayed.getCues(1_000_000L))
        assertEquals(listOf(cue), delayed.getCues(1_500_000L))
    }

    @Test
    fun `the offset is read live so changes apply to the next cues`() {
        var offset = 0L
        val delayed = DelayedSubtitle(subtitle) { offset }

        assertEquals(1_000_000L, delayed.getEventTime(0))
        offset = -250_000L
        assertEquals(750_000L, delayed.getEventTime(0))
        assertEquals(listOf(cue), delayed.getCues(750_000L))
    }
}
