package com.pyoungws.bluewolfplayer.widget

import com.pyoungws.bluewolfplayer.shared.admin.ActiveSession
import com.pyoungws.bluewolfplayer.shared.admin.SessionPlayMethod
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun session(
    item: MediaItem? = MediaItem(id = "m1", name = "The Movie", kind = MediaKind.Movie),
    progress: Float? = 0.42f,
    paused: Boolean = false,
) = ActiveSession(
    id = "s1",
    userName = "alice",
    deviceName = "Shield TV",
    client = "Cobalt Wolf",
    item = item,
    progressFraction = progress,
    isPaused = paused,
    playMethod = SessionPlayMethod.DirectPlay,
    videoCodec = "hevc",
    audioCodec = "aac",
    transcoding = null,
)

class SessionRowTest {

    @Test
    fun `a playing session shows the item, the owner and the progress`() {
        val row = session().toSessionRow()

        assertEquals("The Movie", row.title)
        assertEquals("alice · Shield TV", row.subtitle)
        assertEquals(42, row.progressPercent)
        assertEquals(false, row.paused)
    }

    @Test
    fun `an idle session has no title or progress`() {
        val row = session(item = null).toSessionRow()

        assertNull(row.title)
        assertNull(row.progressPercent)
    }

    @Test
    fun `progress without an item or fraction stays empty`() {
        assertNull(session(item = null, progress = 0.5f).toSessionRow().progressPercent)
        assertNull(session(progress = null).toSessionRow().progressPercent)
    }

    @Test
    fun `the progress percentage is clamped to the valid range`() {
        assertEquals(100, session(progress = 1.2f).toSessionRow().progressPercent)
        assertEquals(0, session(progress = -0.1f).toSessionRow().progressPercent)
    }

    @Test
    fun `a paused session keeps its paused flag`() {
        assertEquals(true, session(paused = true).toSessionRow().paused)
    }
}
