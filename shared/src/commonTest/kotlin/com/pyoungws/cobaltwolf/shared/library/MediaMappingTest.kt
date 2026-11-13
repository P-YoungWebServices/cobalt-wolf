package com.pyoungws.cobaltwolf.shared.library

import com.pyoungws.cobaltwolf.shared.model.LibraryType
import com.pyoungws.cobaltwolf.shared.model.MediaKind
import com.pyoungws.cobaltwolf.shared.model.audioStreamSummaries
import com.pyoungws.cobaltwolf.shared.model.episodeLabel
import com.pyoungws.cobaltwolf.shared.model.videoStreamSummary
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.VideoRangeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaMappingTest {
    private val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val seriesId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val albumId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val artistId = UUID.fromString("44444444-4444-4444-4444-444444444444")
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
        assertEquals(LibraryType.MusicVideos, library(CollectionType.MUSICVIDEOS).type)
        assertTrue(library(CollectionType.MUSICVIDEOS).isBrowsable)
        assertEquals(LibraryType.Music, library(CollectionType.MUSIC).type)
        assertEquals(LibraryType.LiveTv, library(CollectionType.LIVETV).type)
        assertEquals(LibraryType.HomeVideos, library(CollectionType.HOMEVIDEOS).type)
        assertEquals(LibraryType.Mixed, library(CollectionType.FOLDERS).type)
        assertEquals(LibraryType.Other, library(CollectionType.PHOTOS).type)
        assertFalse(library(null).isBrowsable)
    }

    @Test
    fun `music and live tv kinds map to playable or container kinds`() {
        fun item(type: BaseItemKind) = BaseItemDto(id = id, type = type, name = "X").toMediaItem(server)

        assertTrue(item(BaseItemKind.AUDIO).isPlayable)
        assertEquals(MediaKind.Song, item(BaseItemKind.AUDIO).kind)
        assertTrue(item(BaseItemKind.MUSIC_ALBUM).isContainer)
        assertTrue(item(BaseItemKind.MUSIC_ARTIST).isContainer)
        assertTrue(item(BaseItemKind.TV_CHANNEL).isPlayable)
        assertEquals(MediaKind.Program, item(BaseItemKind.TV_PROGRAM).kind)
        assertEquals(MediaKind.Person, item(BaseItemKind.PERSON).kind)
    }

    @Test
    fun `songs carry album and artist fields`() {
        val dto = BaseItemDto(
            id = id, type = BaseItemKind.AUDIO, name = "Song",
            album = "Album", albumId = albumId, indexNumber = 7,
            artists = listOf("Artist One"),
            artistItems = listOf(NameGuidPair(name = "Artist One", id = artistId)),
        ).toMediaItem(server)

        assertEquals("Album", dto.albumName)
        assertEquals(albumId.toString(), dto.albumId)
        assertEquals(listOf("Artist One"), dto.artists)
        assertEquals(listOf(artistId.toString()), dto.artistIds)
        assertEquals(7, dto.trackNumber)
    }

    @Test
    fun `music videos map to a playable kind`() {
        val item = BaseItemDto(id = id, type = BaseItemKind.MUSIC_VIDEO, name = "Video").toMediaItem(server)

        assertEquals(MediaKind.MusicVideo, item.kind)
        assertTrue(item.isPlayable)
    }

    @Test
    fun `media streams map into video and audio summaries`() {
        val dto = BaseItemDto(
            id = id, type = BaseItemKind.MOVIE, name = "M",
            mediaStreams = listOf(
                stream(index = 0, type = MediaStreamType.VIDEO, codec = "hevc",
                    width = 3840, height = 2160, videoRangeType = VideoRangeType.DOVI_WITH_HDR10),
                stream(index = 1, type = MediaStreamType.AUDIO, codec = "truehd",
                    channelLayout = "7.1", channels = 8, profile = "Dolby TrueHD + Dolby Atmos", language = "eng"),
                stream(index = 2, type = MediaStreamType.AUDIO, codec = "eac3", channels = 6, language = "deu"),
                stream(index = 3, type = MediaStreamType.SUBTITLE, codec = "PGSSUB", language = "eng"),
            ),
        )

        val item = dto.toMediaItem(server)

        assertEquals("2160p HEVC Dolby Vision", item.videoStreamSummary())
        assertEquals(listOf("TrueHD Atmos 7.1 eng", "E-AC-3 6 ch deu"), item.audioStreamSummaries())
    }

    private fun stream(
        index: Int,
        type: MediaStreamType,
        codec: String? = null,
        width: Int? = null,
        height: Int? = null,
        videoRangeType: VideoRangeType = VideoRangeType.SDR,
        channelLayout: String? = null,
        channels: Int? = null,
        profile: String? = null,
        language: String? = null,
    ) = MediaStream(
        index = index, type = type, codec = codec,
        width = width, height = height, videoRangeType = videoRangeType,
        channelLayout = channelLayout, channels = channels, profile = profile, language = language,
        isInterlaced = false, isDefault = false, isForced = false,
        isHearingImpaired = false, isOriginal = false, isExternal = false,
        isTextSubtitleStream = false, supportsExternalStream = false,
    )
}
