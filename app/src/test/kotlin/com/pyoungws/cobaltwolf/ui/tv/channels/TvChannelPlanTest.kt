package com.pyoungws.cobaltwolf.ui.tv.channels

import com.pyoungws.cobaltwolf.shared.model.HomeFeed
import com.pyoungws.cobaltwolf.shared.model.Library
import com.pyoungws.cobaltwolf.shared.model.LibraryType
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.MediaKind
import com.pyoungws.cobaltwolf.shared.model.RecentlyAddedRow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun movie(id: String, name: String = "Movie $id") =
    MediaItem(id = id, name = name, kind = MediaKind.Movie, posterUrl = "https://server/Items/$id/poster")

private fun episode(id: String, progress: Double = 40.0) = MediaItem(
    id = id,
    name = "Episode $id",
    kind = MediaKind.Episode,
    seriesName = "Show",
    seasonNumber = 1,
    episodeNumber = 2,
    positionTicks = 1,
    playedPercentage = progress,
    landscapeUrl = "https://server/Items/$id/landscape",
    posterUrl = "https://server/Items/$id/poster",
)

private val library = Library(id = "lib", name = "Movies", type = LibraryType.Movies, imageUrl = null)

class TvChannelPlanTest {
    @Test
    fun `publishes a default continue watching channel and a recently added channel`() {
        val feed = HomeFeed(
            libraries = listOf(library),
            continueWatching = listOf(episode("e1")),
            nextUp = emptyList(),
            recentlyAdded = listOf(RecentlyAddedRow(library, listOf(movie("m1")))),
        )
        val plans = planChannels(feed, "Continue Watching", "Recently Added")
        assertEquals(2, plans.size)

        val continuePlan = plans[0]
        assertEquals("bw_continue_watching", continuePlan.internalId)
        assertEquals("Continue Watching", continuePlan.displayName)
        assertTrue(continuePlan.isDefault)
        assertEquals(1, continuePlan.programs.size)
        val program = continuePlan.programs.single()
        assertEquals("e1", program.itemId)
        assertEquals("Show", program.title)
        assertTrue(program.landscape)
        assertEquals("https://server/Items/e1/landscape", program.imageUrl)
        assertEquals(MediaKind.Episode, program.kind)

        val recentPlan = plans[1]
        assertEquals("bw_recently_added", recentPlan.internalId)
        assertEquals("Recently Added", recentPlan.displayName)
        assertTrue(!recentPlan.isDefault)
        val recent = recentPlan.programs.single()
        assertEquals("m1", recent.itemId)
        assertTrue(!recent.landscape)
        assertEquals("https://server/Items/m1/poster", recent.imageUrl)
    }

    @Test
    fun `flattens all library rows into recently added and caps each channel`() {
        val other = library.copy(id = "shows", name = "Shows", type = LibraryType.TvShows)
        val feed = HomeFeed(
            libraries = listOf(library, other),
            continueWatching = (1..15).map { episode("e$it") },
            nextUp = emptyList(),
            recentlyAdded = listOf(
                RecentlyAddedRow(library, (1..8).map { movie("m$it") }),
                RecentlyAddedRow(other, (1..8).map { movie("s$it") }),
            ),
        )
        val plans = planChannels(feed, "Continue", "Recent")
        assertEquals(10, plans[0].programs.size)
        assertEquals(10, plans[1].programs.size)
        // Both rows contribute, in order.
        assertEquals(listOf("m1", "m8", "s1"), listOf(plans[1].programs.first().itemId, plans[1].programs[7].itemId, plans[1].programs[8].itemId))
    }

    @Test
    fun `keeps the channels planned even when a row is empty`() {
        val feed = HomeFeed(libraries = listOf(library), continueWatching = emptyList(), nextUp = emptyList(), recentlyAdded = emptyList())
        val plans = planChannels(feed, "Continue Watching", "Recently Added")
        assertEquals(2, plans.size)
        assertTrue(plans.all { it.programs.isEmpty() })
        assertNull(plans[0].programs.firstOrNull()?.imageUrl)
    }
}
