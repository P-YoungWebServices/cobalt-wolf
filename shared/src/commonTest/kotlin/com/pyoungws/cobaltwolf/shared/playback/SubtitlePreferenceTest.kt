package com.pyoungws.cobaltwolf.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitlePreferenceTest {

    @Test
    fun `fresh install defaults to forced english subtitles`() {
        assertEquals(SubtitlePlaybackMode.Forced, PlaybackSettings().subtitleMode)
        assertEquals("eng", PlaybackSettings().preferredSubtitleLanguage)
    }

    @Test
    fun `untouched subtitle prefs upgrade to forced english`() {
        assertEquals(
            SubtitlePlaybackMode.Forced to "eng",
            resolveSubtitlePreference(storedMode = null, storedLanguage = null),
        )
        // The old factory default stored mode Default with no language is untouched.
        assertEquals(
            SubtitlePlaybackMode.Forced to "eng",
            resolveSubtitlePreference(SubtitlePlaybackMode.Default, null),
        )
    }

    @Test
    fun `a stored language without a mode keeps it under the forced default`() {
        assertEquals(
            SubtitlePlaybackMode.Forced to "spa",
            resolveSubtitlePreference(storedMode = null, storedLanguage = "spa"),
        )
    }

    @Test
    fun `explicit choices survive the migration`() {
        assertEquals(
            SubtitlePlaybackMode.Always to "spa",
            resolveSubtitlePreference(SubtitlePlaybackMode.Always, "spa"),
        )
        assertEquals(
            SubtitlePlaybackMode.None to "eng",
            resolveSubtitlePreference(SubtitlePlaybackMode.None, null),
        )
        // Deliberately keeping server behaviour with a chosen language is kept too.
        assertEquals(
            SubtitlePlaybackMode.Default to "fra",
            resolveSubtitlePreference(SubtitlePlaybackMode.Default, "fra"),
        )
    }

    @Test
    fun `a missing language under an explicit mode falls back to english`() {
        assertEquals(
            SubtitlePlaybackMode.Always to "eng",
            resolveSubtitlePreference(SubtitlePlaybackMode.Always, null),
        )
    }
}
