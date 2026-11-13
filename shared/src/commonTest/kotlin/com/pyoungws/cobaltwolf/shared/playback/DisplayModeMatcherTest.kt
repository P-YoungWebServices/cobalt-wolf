package com.pyoungws.cobaltwolf.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayModeMatcherTest {
    private val uhd60 = DisplayMode(1, 3840, 2160, 60f)
    private val uhd5994 = DisplayMode(2, 3840, 2160, 59.94f)
    private val uhd50 = DisplayMode(3, 3840, 2160, 50f)
    private val uhd24 = DisplayMode(4, 3840, 2160, 24f)
    private val uhd23976 = DisplayMode(5, 3840, 2160, 23.976f)
    private val hd24 = DisplayMode(6, 1920, 1080, 24f)
    private val modes = listOf(uhd60, uhd5994, uhd50, uhd24, uhd23976, hd24)

    @Test
    fun `film rates get their exact mode at the current resolution`() {
        assertEquals(uhd23976, chooseDisplayMode(modes, 1, 23.976f))
        assertEquals(uhd24, chooseDisplayMode(modes, 1, 24f))
    }

    @Test
    fun `pal and ntsc rates use a whole multiple`() {
        assertEquals(uhd50, chooseDisplayMode(modes, 1, 25f))
        assertEquals(uhd50, chooseDisplayMode(modes, 1, 50f))
        assertEquals(uhd5994, chooseDisplayMode(modes, 1, 29.97f))
        assertEquals(uhd5994, chooseDisplayMode(modes, 1, 59.94f))
    }

    @Test
    fun `a mode that already fits is kept`() {
        assertNull(chooseDisplayMode(modes, 1, 30f))
        assertNull(chooseDisplayMode(modes, 1, 60f))
        assertNull(chooseDisplayMode(modes, 4, 24f))
        assertNull(chooseDisplayMode(listOf(uhd60, DisplayMode(7, 3840, 2160, 120f)), 7, 24f))
    }

    @Test
    fun `a near match beats judder when there is no exact mode`() {
        assertEquals(uhd24, chooseDisplayMode(listOf(uhd60, uhd24), 1, 23.976f))
        assertNull(chooseDisplayMode(listOf(uhd60, uhd24), 4, 23.976f))
        // But an exact mode is preferred over staying on a near one.
        assertEquals(uhd23976, chooseDisplayMode(modes, 4, 23.976f))
    }

    @Test
    fun `the resolution is never changed`() {
        assertNull(chooseDisplayMode(listOf(uhd60, hd24), 1, 24f))
    }

    @Test
    fun `nothing is chosen without a match, an active mode or a frame rate`() {
        assertNull(chooseDisplayMode(listOf(uhd60), 1, 24f))
        assertNull(chooseDisplayMode(modes, null, 24f))
        assertNull(chooseDisplayMode(modes, 99, 24f))
        assertNull(chooseDisplayMode(modes, 1, 0f))
        assertNull(chooseDisplayMode(modes, 1, -1f))
    }
}
