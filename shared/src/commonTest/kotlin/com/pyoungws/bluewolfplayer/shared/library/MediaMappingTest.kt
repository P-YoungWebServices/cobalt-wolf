package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.episodeLabel
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.UserItemDataDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaMappingTest {
    private val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val seriesId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val server = "https://jellyfin.example.com/"

    private fun userData(positionTicks: Long = 0, percentage: Double? = null, played: Boolean = false) = UserItemDataDto(
        rating = null, playedPercentage = percentage, unplayedItemCount = null, playbackPositionTicks = positionTicks,
        playCount = 0, isFavorite = false, likes = null, lastPlayedDate = null, played = played, key = "k", itemId = id,
    )

    @Test
    fun `movie maps metadata, progress and image urls`() {
        val dto = BaseItemDto(
            id = id, type = BaseItemKind.MOVIE, name = "Wolf Moon", productionYear = 2021, runTimeTicks = 54_000_000_000L,
            overview = "A film.", genres = listOf("Drama"), imageTags = mapOf(ImageType.PRIMARY to "ptag"),
            backdropImageTags = listOf("btag"), userData = userData(1_000, 25.0),
        )

        val item = dto.toMediaItem(server)

        assertEquals(MediaKind.Movie, item.kind)
        assertEquals(90, item.runtimeMinutes)
        assertEquals(2021, item.year)
        assertEquals("https://jellyfin.example.com/Items/$id/Images/Primary?maxWidth=400&tag=ptag", item.posterUrl)
        assertEquals("https://jellyfin.example.com/Items/$id/Images/Backdrop?maxWidth=640&tag=btag", item.landscapeUrl)
        assertTrue(item.hasProgress)
        assertEquals(0.25f, item.progressFraction)
    }

    @Test
    fun `episode uses the series poster and its own still, with season and episode numbers`() {
        val dto = BaseItemDto(
            id = id, type = BaseItemKind.EPISODE, name = "Pilot", seriesId = seriesId, seriesName = "Show", seriesPrimaryImageTag = "stag",
            parentIndexNumber = 2, indexNumber = 5, imageTags = mapOf(ImageType.PRIMARY to "still"),
        )

        val item = dto.toMediaItem(server)

        assertEquals("https://jellyfin.example.com/Items/$seriesId/Images/Primary?maxWidth=400&tag=stag", item.posterUrl)
        assertEquals("https://jellyfin.example.com/Items/$id/Images/Primary?maxWidth=640&tag=still", item.landscapeUrl)
        assertEquals("S2:E5", item.episodeLabel())
        assertEquals(seriesId.toString(), item.seriesId)
    }

    @Test
    fun `episode without its own image falls back to the series backdrop`() {
        val dto = BaseItemDto(
            id = id, type = BaseItemKind.EPISODE, parentBackdropItemId = seriesId, parentBackdropImageTags = listOf("pb"),
        )

        assertEquals("https://jellyfin.example.com/Items/$seriesId/Images/Backdrop?maxWidth=640&tag=pb", dto.toMediaItem(server).landscapeUrl)
    }

    @Test
    fun `finished items have no progress`() {
        val item = BaseItemDto(id = id, type = BaseItemKind.MOVIE, userData = userData(500, 99.0, played = true)).toMediaItem(server)

        assertFalse(item.hasProgress)
        assertNull(item.progressFraction)
        assertTrue(item.isPlayed)
    }

    @Test
    fun `libraries are classified by collection type`() {
        fun library(type: CollectionType?) = BaseItemDto(id = id, type = BaseItemKind.COLLECTION_FOLDER, name = "L", collectionType = type).toLibrary(server)

        assertEquals(LibraryType.Movies, library(CollectionType.MOVIES).type)
        assertEquals(LibraryType.TvShows, library(CollectionType.TVSHOWS).type)
        assertEquals(LibraryType.Other, library(CollectionType.MUSIC).type)
        assertFalse(library(null).isBrowsable)
    }
}
