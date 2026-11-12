package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.MediaStreamInfo
import com.pyoungws.bluewolfplayer.shared.model.Page
import com.pyoungws.bluewolfplayer.shared.model.Session
import com.pyoungws.bluewolfplayer.shared.model.StreamKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest

/** Jellyfin library queries. Implementations throw the SDK's `ApiClientException` subtypes on failure. */
interface JellyfinLibraryDataSource {
    suspend fun getLibraries(session: Session): List<Library>
    suspend fun getResumeItems(session: Session): List<MediaItem>
    suspend fun getNextUp(session: Session): List<MediaItem>
    suspend fun getLatest(session: Session, libraryId: String): List<MediaItem>
    suspend fun getLibraryItems(session: Session, library: Library, startIndex: Int, limit: Int): Page<MediaItem>
    suspend fun getItem(session: Session, itemId: String): MediaItem
    suspend fun getSeasons(session: Session, seriesId: String): List<MediaItem>
    suspend fun getEpisodes(session: Session, seriesId: String, seasonId: String): List<MediaItem>
    suspend fun search(session: Session, term: String, limit: Int): List<MediaItem>
}

class SdkJellyfinLibraryDataSource(
    private val jellyfin: Jellyfin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JellyfinLibraryDataSource {

    private suspend fun <T> call(session: Session, block: suspend ApiClient.() -> T): T =
        withContext(ioDispatcher) {
            jellyfin.createApi(baseUrl = session.serverAddress, accessToken = session.accessToken).block()
        }

    override suspend fun getLibraries(session: Session) = call(session) {
        userViewApi.getUserViews(userId = session.userId.toUuid()).content.items
            .map { it.toLibrary(session.serverAddress) }
    }

    override suspend fun getResumeItems(session: Session) = call(session) {
        libraryApi.getResumeItems(
            GetResumeItemsRequest(
                userId = session.userId.toUuid(),
                limit = ROW_LIMIT,
                mediaTypes = listOf(org.jellyfin.sdk.model.api.MediaType.VIDEO),
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getNextUp(session: Session) = call(session) {
        showApi.getNextUp(
            GetNextUpRequest(
                userId = session.userId.toUuid(),
                limit = ROW_LIMIT,
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getLatest(session: Session, libraryId: String) = call(session) {
        libraryApi.getLatestMedia(
            GetLatestMediaRequest(
                userId = session.userId.toUuid(),
                parentId = libraryId.toUuid(),
                fields = ITEM_FIELDS,
                limit = ROW_LIMIT,
                enableUserData = true,
            ),
        ).content.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getLibraryItems(session: Session, library: Library, startIndex: Int, limit: Int) = call(session) {
        val result = libraryApi.getItems(
            userId = session.userId.toUuid(),
            parentId = library.id.toUuid(),
            recursive = true,
            includeItemTypes = listOf(
                when (library.type) {
                    LibraryType.TvShows -> BaseItemKind.SERIES
                    LibraryType.MusicVideos -> BaseItemKind.MUSIC_VIDEO
                    else -> BaseItemKind.MOVIE
                },
            ),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = limit,
            fields = ITEM_FIELDS,
            enableUserData = true,
        ).content
        Page(result.items.map { it.toMediaItem(session.serverAddress) }, result.totalRecordCount)
    }

    override suspend fun getItem(session: Session, itemId: String) = call(session) {
        libraryApi.getItem(itemId = itemId.toUuid(), userId = session.userId.toUuid()).content
            .toMediaItem(session.serverAddress)
    }

    override suspend fun getSeasons(session: Session, seriesId: String) = call(session) {
        showApi.getSeasons(
            GetSeasonsRequest(
                seriesId = seriesId.toUuid(),
                userId = session.userId.toUuid(),
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getEpisodes(session: Session, seriesId: String, seasonId: String) = call(session) {
        showApi.getEpisodes(
            GetEpisodesRequest(
                seriesId = seriesId.toUuid(),
                userId = session.userId.toUuid(),
                seasonId = seasonId.toUuid(),
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun search(session: Session, term: String, limit: Int) = call(session) {
        libraryApi.getItems(
            userId = session.userId.toUuid(),
            searchTerm = term,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.MUSIC_VIDEO),
            limit = limit,
            fields = ITEM_FIELDS,
            enableUserData = true,
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    private companion object {
        const val ROW_LIMIT = 20
        val ITEM_FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.MEDIA_STREAMS)
    }
}

private fun String.toUuid(): UUID = UUID.fromString(this)

internal fun BaseItemDto.toLibrary(serverAddress: String) = Library(
    id = id.toString(),
    name = name.orEmpty(),
    type = when (collectionType) {
        CollectionType.MOVIES -> LibraryType.Movies
        CollectionType.TVSHOWS -> LibraryType.TvShows
        CollectionType.MUSICVIDEOS -> LibraryType.MusicVideos
        else -> LibraryType.Other
    },
    imageUrl = imageTags?.get(ImageType.PRIMARY)?.let { imageUrl(serverAddress, id.toString(), ImageType.PRIMARY, it, LANDSCAPE_WIDTH) },
)

internal fun BaseItemDto.toMediaItem(serverAddress: String): MediaItem {
    val itemId = id.toString()
    val kind = when (type) {
        BaseItemKind.MOVIE -> MediaKind.Movie
        BaseItemKind.SERIES -> MediaKind.Series
        BaseItemKind.SEASON -> MediaKind.Season
        BaseItemKind.EPISODE -> MediaKind.Episode
        BaseItemKind.MUSIC_VIDEO -> MediaKind.MusicVideo
        else -> MediaKind.Other
    }
    val streams = mediaStreams.orEmpty().map { stream ->
        MediaStreamInfo(
            kind = when (stream.type) {
                MediaStreamType.VIDEO -> StreamKind.Video
                MediaStreamType.AUDIO -> StreamKind.Audio
                else -> StreamKind.Subtitle
            },
            codec = stream.codec,
            width = stream.width,
            height = stream.height,
            videoRange = when (stream.videoRangeType) {
                VideoRangeType.HDR10 -> "HDR10"
                VideoRangeType.HDR10_PLUS -> "HDR10+"
                VideoRangeType.HLG -> "HLG"
                VideoRangeType.DOVI, VideoRangeType.DOVI_WITH_HDR10, VideoRangeType.DOVI_WITH_HLG,
                VideoRangeType.DOVI_WITH_SDR, VideoRangeType.DOVI_WITH_EL, VideoRangeType.DOVI_WITH_HDR10_PLUS,
                VideoRangeType.DOVI_WITH_ELHDR10_PLUS, VideoRangeType.DOVI_INVALID -> "Dolby Vision"
                else -> null
            },
            channels = stream.channels,
            channelLayout = stream.channelLayout,
            profile = stream.profile,
            language = stream.language,
        )
    }
    val primaryTag = imageTags?.get(ImageType.PRIMARY)
    val backdropTag = backdropImageTags?.firstOrNull()
    val parentBackdropTag = parentBackdropImageTags?.firstOrNull()
    val seriesIdString = seriesId?.toString()
    val seriesPrimaryTag = seriesPrimaryImageTag
    val parentBackdropId = parentBackdropItemId?.toString()

    val posterUrl = when {
        kind == MediaKind.Episode && seriesIdString != null && seriesPrimaryTag != null ->
            imageUrl(serverAddress, seriesIdString, ImageType.PRIMARY, seriesPrimaryTag, POSTER_WIDTH)
        primaryTag != null -> imageUrl(serverAddress, itemId, ImageType.PRIMARY, primaryTag, POSTER_WIDTH)
        else -> null
    }
    val landscapeUrl = when {
        kind == MediaKind.Episode && primaryTag != null -> imageUrl(serverAddress, itemId, ImageType.PRIMARY, primaryTag, LANDSCAPE_WIDTH)
        backdropTag != null -> imageUrl(serverAddress, itemId, ImageType.BACKDROP, backdropTag, LANDSCAPE_WIDTH)
        parentBackdropTag != null && parentBackdropId != null ->
            imageUrl(serverAddress, parentBackdropId, ImageType.BACKDROP, parentBackdropTag, LANDSCAPE_WIDTH)
        primaryTag != null -> imageUrl(serverAddress, itemId, ImageType.PRIMARY, primaryTag, LANDSCAPE_WIDTH)
        else -> null
    }
    val backdropUrl = when {
        backdropTag != null -> imageUrl(serverAddress, itemId, ImageType.BACKDROP, backdropTag, BACKDROP_WIDTH)
        parentBackdropTag != null && parentBackdropId != null ->
            imageUrl(serverAddress, parentBackdropId, ImageType.BACKDROP, parentBackdropTag, BACKDROP_WIDTH)
        else -> null
    }

    return MediaItem(
        id = itemId,
        name = name.orEmpty(),
        kind = kind,
        overview = overview?.takeIf { it.isNotBlank() },
        year = productionYear,
        runtimeMinutes = runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() }?.takeIf { it > 0 },
        communityRating = communityRating,
        officialRating = officialRating,
        genres = genres.orEmpty(),
        seriesId = seriesIdString,
        seriesName = seriesName,
        seasonId = seasonId?.toString(),
        seasonNumber = if (kind == MediaKind.Season) indexNumber else parentIndexNumber,
        episodeNumber = if (kind == MediaKind.Episode) indexNumber else null,
        positionTicks = userData?.playbackPositionTicks ?: 0,
        playedPercentage = userData?.playedPercentage,
        isPlayed = userData?.played == true,
        unplayedCount = userData?.unplayedItemCount,
        posterUrl = posterUrl,
        landscapeUrl = landscapeUrl,
        backdropUrl = backdropUrl,
        streams = streams,
    )
}

internal fun imageUrl(serverAddress: String, itemId: String, type: ImageType, tag: String, maxWidth: Int): String {
    val segment = when (type) {
        ImageType.PRIMARY -> "Primary"
        ImageType.BACKDROP -> "Backdrop"
        else -> type.serialName
    }
    return "${serverAddress.trimEnd('/')}/Items/$itemId/Images/$segment?maxWidth=$maxWidth&tag=$tag"
}

internal const val TICKS_PER_MINUTE = 600_000_000L
private const val POSTER_WIDTH = 400
private const val LANDSCAPE_WIDTH = 640
private const val BACKDROP_WIDTH = 1280
