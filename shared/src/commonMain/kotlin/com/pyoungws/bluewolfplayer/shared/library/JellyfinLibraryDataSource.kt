package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.GenreInfo
import com.pyoungws.bluewolfplayer.shared.model.MediaPerson
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
import org.jellyfin.sdk.api.client.extensions.genreApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.suggestionApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest

/** Jellyfin library queries. Implementations throw the SDK's `ApiClientException` subtypes on failure. */
interface JellyfinLibraryDataSource {
    suspend fun getLibraries(session: Session): List<Library>
    suspend fun getResumeItems(session: Session): List<MediaItem>
    suspend fun getNextUp(session: Session, nextUpCutoffDays: Int = 0): List<MediaItem>
    suspend fun getLatest(session: Session, libraryId: String): List<MediaItem>
    suspend fun getLibraryItems(session: Session, library: Library, startIndex: Int, limit: Int): Page<MediaItem>
    suspend fun getItems(session: Session, query: ItemQuery): Page<MediaItem>
    suspend fun getGenres(session: Session, libraryId: String): List<GenreInfo>
    suspend fun getSuggestions(session: Session, library: Library): List<MediaItem>
    suspend fun getItem(session: Session, itemId: String): MediaItem
    suspend fun getSeasons(session: Session, seriesId: String): List<MediaItem>
    suspend fun getEpisodes(session: Session, seriesId: String, seasonId: String): List<MediaItem>
    suspend fun search(session: Session, term: String, limit: Int): List<MediaItem>
    suspend fun getSimilar(session: Session, itemId: String): List<MediaItem>
    suspend fun getNextUpForSeries(session: Session, seriesId: String): MediaItem?
    suspend fun getSpecialFeatures(session: Session, itemId: String): List<MediaItem>
    suspend fun getLocalTrailers(session: Session, itemId: String): List<MediaItem>
    suspend fun getPreviousEpisode(session: Session, episode: MediaItem): MediaItem?
    suspend fun setPlayed(session: Session, itemId: String, played: Boolean)
    suspend fun setFavorite(session: Session, itemId: String, favorite: Boolean)
}

/** Generic item query used by alternate browse views, collection contents and person filmographies. */
data class ItemQuery(
    val libraryId: String? = null,
    val parentId: String? = null,
    val personIds: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    val albumArtistIds: List<String> = emptyList(),
    val nameStartsWith: String? = null,
    val nameLessThan: String? = null,
    val includeItemKinds: Set<MediaKind> = emptySet(),
    val sortBy: ItemSortBy? = null,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val unwatchedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val startIndex: Int = 0,
    val limit: Int = 60,
    val recursive: Boolean = true,
)

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

    override suspend fun getNextUp(session: Session, nextUpCutoffDays: Int) = call(session) {
        showApi.getNextUp(
            GetNextUpRequest(
                userId = session.userId.toUuid(),
                limit = ROW_LIMIT,
                fields = ITEM_FIELDS,
                enableUserData = true,
                nextUpDateCutoff = if (nextUpCutoffDays > 0)
                    java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(nextUpCutoffDays.toLong())
                else null,
            ),
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getSimilar(session: Session, itemId: String) = call(session) {
        libraryApi.getSimilarItems(
            GetSimilarItemsRequest(
                itemId = itemId.toUuid(),
                userId = session.userId.toUuid(),
                limit = ROW_LIMIT,
                fields = ITEM_FIELDS,
            ),
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getNextUpForSeries(session: Session, seriesId: String) = call(session) {
        showApi.getNextUp(
            GetNextUpRequest(
                userId = session.userId.toUuid(),
                seriesId = seriesId.toUuid(),
                limit = 1,
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content.items.firstOrNull()?.toMediaItem(session.serverAddress)
    }

    override suspend fun getSpecialFeatures(session: Session, itemId: String) = call(session) {
        libraryApi.getSpecialFeatures(session.userId.toUuid(), itemId.toUuid()).content
            .map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getLocalTrailers(session: Session, itemId: String) = call(session) {
        libraryApi.getLocalTrailers(session.userId.toUuid(), itemId.toUuid()).content
            .map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun getPreviousEpisode(session: Session, episode: MediaItem) = call(session) {
        val seriesId = episode.seriesId ?: return@call null
        val candidates = showApi.getEpisodes(
            GetEpisodesRequest(
                userId = session.userId.toUuid(),
                seriesId = seriesId.toUuid(),
                adjacentTo = episode.id.toUuid(),
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content.items
        candidates.map { it.toMediaItem(session.serverAddress) }
            .filter { it.episodeNumber != null }
            .filter { ep ->
                (ep.seasonNumber ?: 0) < (episode.seasonNumber ?: 0) ||
                    ((ep.seasonNumber ?: 0) == (episode.seasonNumber ?: 0) && (ep.episodeNumber ?: 0) < (episode.episodeNumber ?: 0))
            }
            .maxWithOrNull(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
    }

    override suspend fun getItems(session: Session, query: ItemQuery) = call(session) {
        val filters = buildList {
            if (query.unwatchedOnly) add(ItemFilter.IS_UNPLAYED)
            if (query.favoritesOnly) add(ItemFilter.IS_FAVORITE)
        }
        val result = libraryApi.getItems(
            GetItemsRequest(
                userId = session.userId.toUuid(),
                parentId = (query.parentId ?: query.libraryId)?.toUuid(),
                includeItemTypes = query.includeItemKinds.mapNotNull { it.toBaseItemKind() }.ifEmpty { null },
                genres = query.genres.ifEmpty { null },
                artistIds = query.artistIds.map { it.toUuid() }.ifEmpty { null },
                albumArtistIds = query.albumArtistIds.map { it.toUuid() }.ifEmpty { null },
                personIds = query.personIds.map { it.toUuid() }.ifEmpty { null },
                nameStartsWith = query.nameStartsWith,
                nameLessThan = query.nameLessThan,
                sortBy = query.sortBy?.let { listOf(it) },
                sortOrder = listOf(query.sortOrder),
                filters = filters.ifEmpty { null },
                startIndex = query.startIndex,
                limit = query.limit,
                recursive = query.recursive,
                fields = ITEM_FIELDS,
                enableUserData = true,
            ),
        ).content
        Page(withContainerArt(session, result.items.map { it.toMediaItem(session.serverAddress) }), result.totalRecordCount ?: result.items.size)
    }

    /**
     * Collections and playlists often have no server-side artwork. When the tag list is
     * empty, fall back to the first child's primary image so cards never render blank.
     */
    private suspend fun ApiClient.withContainerArt(session: Session, items: List<MediaItem>): List<MediaItem> =
        items.map { item ->
            if ((item.kind == MediaKind.BoxSet || item.kind == MediaKind.Playlist) && item.posterUrl == null) {
                val child = runCatching {
                    libraryApi.getItems(
                        GetItemsRequest(
                            userId = session.userId.toUuid(),
                            parentId = item.id.toUuid(),
                            recursive = true,
                            limit = 1,
                            fields = ITEM_FIELDS,
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                        ),
                    ).content.items.firstOrNull()
                }.getOrNull()
                child?.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                    item.copy(posterUrl = imageUrl(session.serverAddress, child.id.toString(), ImageType.PRIMARY, tag, POSTER_WIDTH))
                } ?: item
            } else item
        }

    override suspend fun getGenres(session: Session, libraryId: String) = call(session) {
        genreApi.getGenres(
            GetGenresRequest(
                userId = session.userId.toUuid(),
                parentId = libraryId.toUuid(),
            ),
        ).content.items.map {
            GenreInfo(
                id = it.id.toString(),
                name = it.name.orEmpty(),
                imageUrl = it.imageTags?.get(ImageType.PRIMARY)?.let { tag -> imageUrl(session.serverAddress, it.id.toString(), ImageType.PRIMARY, tag, LANDSCAPE_WIDTH) },
            )
        }
    }

    override suspend fun getSuggestions(session: Session, library: Library) = call(session) {
        val kinds = when (library.type) {
            LibraryType.Movies -> listOf(BaseItemKind.MOVIE)
            LibraryType.TvShows -> listOf(BaseItemKind.SERIES)
            LibraryType.Music -> listOf(BaseItemKind.MUSIC_ALBUM)
            else -> emptyList()
        }
        if (kinds.isEmpty()) emptyList()
        else suggestionApi.getSuggestions(
            GetSuggestionsRequest(
                userId = session.userId.toUuid(),
                type = kinds,
                limit = ROW_LIMIT,
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
        ).content.let { withContainerArt(session, it.map { item -> item.toMediaItem(session.serverAddress) }) }
    }

    override suspend fun getLibraryItems(session: Session, library: Library, startIndex: Int, limit: Int) = call(session) {
        val (kinds, recursive) = when (library.type) {
            LibraryType.TvShows -> listOf(BaseItemKind.SERIES) to true
            LibraryType.Music -> listOf(BaseItemKind.MUSIC_ALBUM) to true
            LibraryType.MusicVideos -> listOf(BaseItemKind.MUSIC_VIDEO) to true
            LibraryType.LiveTv -> listOf(BaseItemKind.TV_CHANNEL) to false
            LibraryType.BoxSets -> listOf(BaseItemKind.BOX_SET) to true
            LibraryType.Playlists -> listOf(BaseItemKind.PLAYLIST) to true
            LibraryType.HomeVideos -> listOf(BaseItemKind.VIDEO) to true
            LibraryType.Mixed -> emptyList<BaseItemKind>() to false
            else -> listOf(BaseItemKind.MOVIE) to true
        }
        val result = libraryApi.getItems(
            userId = session.userId.toUuid(),
            parentId = library.id.toUuid(),
            recursive = recursive,
            includeItemTypes = kinds.ifEmpty { null },
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = limit,
            fields = ITEM_FIELDS,
            enableUserData = true,
        ).content
        Page(withContainerArt(session, result.items.map { it.toMediaItem(session.serverAddress) }), result.totalRecordCount)
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
            includeItemTypes = listOf(
                BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.MUSIC_VIDEO,
                BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST,
                BaseItemKind.TV_CHANNEL, BaseItemKind.PERSON,
            ),
            limit = limit,
            fields = ITEM_FIELDS,
            enableUserData = true,
        ).content.items.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun setPlayed(session: Session, itemId: String, played: Boolean) {
        call(session) {
            if (played) userDataApi.markPlayedItem(itemId = itemId.toUuid(), userId = session.userId.toUuid())
            else userDataApi.markUnplayedItem(itemId = itemId.toUuid(), userId = session.userId.toUuid())
        }
    }

    override suspend fun setFavorite(session: Session, itemId: String, favorite: Boolean) {
        call(session) {
            if (favorite) userDataApi.markFavoriteItem(itemId = itemId.toUuid(), userId = session.userId.toUuid())
            else userDataApi.unmarkFavoriteItem(itemId = itemId.toUuid(), userId = session.userId.toUuid())
        }
    }

    private companion object {
        const val ROW_LIMIT = 20
        val ITEM_FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.MEDIA_STREAMS, ItemFields.PEOPLE, ItemFields.TAGLINES, ItemFields.CHAPTERS, ItemFields.REMOTE_TRAILERS, ItemFields.MEDIA_SOURCES, ItemFields.TRICKPLAY)
    }
}

private fun String.toUuid(): UUID = toUUID()

private fun MediaKind.toBaseItemKind(): BaseItemKind? = when (this) {
    MediaKind.Movie -> BaseItemKind.MOVIE
    MediaKind.Series -> BaseItemKind.SERIES
    MediaKind.Season -> BaseItemKind.SEASON
    MediaKind.Episode -> BaseItemKind.EPISODE
    MediaKind.MusicVideo -> BaseItemKind.MUSIC_VIDEO
    MediaKind.BoxSet -> BaseItemKind.BOX_SET
    MediaKind.Playlist -> BaseItemKind.PLAYLIST
    MediaKind.Video -> BaseItemKind.VIDEO
    MediaKind.Song -> BaseItemKind.AUDIO
    MediaKind.MusicAlbum -> BaseItemKind.MUSIC_ALBUM
    MediaKind.MusicArtist -> BaseItemKind.MUSIC_ARTIST
    MediaKind.TvChannel -> BaseItemKind.TV_CHANNEL
    MediaKind.Program -> BaseItemKind.TV_PROGRAM
    MediaKind.Recording -> BaseItemKind.RECORDING
    MediaKind.Folder -> BaseItemKind.FOLDER
    MediaKind.Person -> BaseItemKind.PERSON
    MediaKind.Other -> null
}

internal fun BaseItemDto.toLibrary(serverAddress: String) = Library(
    id = id.toString(),
    name = name.orEmpty(),
    type = when (collectionType) {
        CollectionType.MOVIES -> LibraryType.Movies
        CollectionType.TVSHOWS -> LibraryType.TvShows
        CollectionType.MUSIC -> LibraryType.Music
        CollectionType.MUSICVIDEOS -> LibraryType.MusicVideos
        CollectionType.LIVETV -> LibraryType.LiveTv
        CollectionType.HOMEVIDEOS -> LibraryType.HomeVideos
        CollectionType.FOLDERS -> LibraryType.Mixed
        CollectionType.BOXSETS -> LibraryType.BoxSets
        CollectionType.PLAYLISTS -> LibraryType.Playlists
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
        BaseItemKind.BOX_SET -> MediaKind.BoxSet
        BaseItemKind.PLAYLIST -> MediaKind.Playlist
        BaseItemKind.VIDEO, BaseItemKind.TRAILER -> MediaKind.Video
        BaseItemKind.AUDIO, BaseItemKind.AUDIO_BOOK -> MediaKind.Song
        BaseItemKind.MUSIC_ALBUM -> MediaKind.MusicAlbum
        BaseItemKind.MUSIC_ARTIST -> MediaKind.MusicArtist
        BaseItemKind.TV_CHANNEL, BaseItemKind.LIVE_TV_CHANNEL -> MediaKind.TvChannel
        BaseItemKind.TV_PROGRAM, BaseItemKind.LIVE_TV_PROGRAM, BaseItemKind.PROGRAM -> MediaKind.Program
        BaseItemKind.RECORDING -> MediaKind.Recording
        BaseItemKind.FOLDER -> MediaKind.Folder
        BaseItemKind.PERSON -> MediaKind.Person
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
            isHearingImpaired = stream.isHearingImpaired == true,
        )
    }
    val primaryTag = imageTags?.get(ImageType.PRIMARY)
    val backdropTag = backdropImageTags?.firstOrNull()
    val parentBackdropTag = parentBackdropImageTags?.firstOrNull()
    val seriesIdString = seriesId?.toString()
    val seriesPrimaryTag = seriesPrimaryImageTag
    val parentBackdropId = parentBackdropItemId?.toString()

    val logoUrl = imageTags?.get(ImageType.LOGO)
        ?.let { imageUrl(serverAddress, itemId, ImageType.LOGO, it, LANDSCAPE_WIDTH) }
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
        logoUrl = logoUrl,
        streams = streams,
        criticRating = criticRating,
        tagline = taglines?.firstOrNull()?.takeIf { it.isNotBlank() },
        premiereDate = premiereDate?.toString()?.take(10)?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() },
        chapters = chapters.orEmpty().mapIndexed { index, ch ->
            com.pyoungws.bluewolfplayer.shared.model.MediaChapter(
                name = ch.name,
                startTicks = ch.startPositionTicks,
                imageUrl = ch.imageTag?.let { "$serverAddress/Items/$itemId/Images/Chapter/$index?tag=$it" },
            )
        },
        remoteTrailers = remoteTrailers.orEmpty().map { com.pyoungws.bluewolfplayer.shared.model.MediaTrailer(name = it.name, url = it.url.orEmpty()) },
        mediaSources = mediaSources.orEmpty().map { com.pyoungws.bluewolfplayer.shared.model.MediaVersion(id = it.id.orEmpty(), name = it.name ?: it.path?.substringAfterLast('/') ?: it.id.orEmpty()) },
        albumId = albumId?.toString(),
        albumName = album,
        artists = artists.orEmpty(),
        artistIds = artistItems.orEmpty().map { it.id.toString() },
        trackNumber = if (kind == MediaKind.Song) indexNumber else null,
        programInfo = if (kind == MediaKind.Program || kind == MediaKind.TvChannel) com.pyoungws.bluewolfplayer.shared.model.ProgramInfo(
            channelId = channelId?.toString(),
            channelName = channelName,
            channelNumber = channelNumber ?: number?.toString(),
            start = startDate,
            end = endDate,
            isLive = isLive == true,
            isPremiere = isPremiere == true,
            isRepeat = isRepeat == true,
            isSeries = isSeries == true,
            timerId = timerId,
            seriesTimerId = seriesTimerId,
        ) else null,
        canDelete = canDelete == true,
        canDownload = canDownload == true,
        trickplay = trickplay?.values?.filterNotNull()?.flatMap { it.entries }?.maxByOrNull { it.key.toIntOrNull() ?: 0 }?.let { entry ->
            val tileWidthPx = entry.key.toIntOrNull() ?: entry.value.width
            com.pyoungws.bluewolfplayer.shared.model.TrickplayTiles(
                width = tileWidthPx,
                height = entry.value.height,
                tileWidth = entry.value.tileWidth,
                tileHeight = entry.value.tileHeight,
                thumbnailCount = entry.value.thumbnailCount,
                interval = entry.value.interval,
                urlPrefix = "$serverAddress/Videos/$itemId/Trickplay/$tileWidthPx/",
            )
        },
        seriesStatus = when (status) {
            "Continuing" -> com.pyoungws.bluewolfplayer.shared.model.SeriesStatus.Continuing
            "Ended" -> com.pyoungws.bluewolfplayer.shared.model.SeriesStatus.Ended
            "Unreleased" -> com.pyoungws.bluewolfplayer.shared.model.SeriesStatus.Unreleased
            else -> null
        },
        isFavorite = userData?.isFavorite == true,
        childCount = childCount,
        runtimeTicks = runTimeTicks,
        people = people.orEmpty().map { person ->
            MediaPerson(
                id = person.id.toString(),
                name = person.name.orEmpty(),
                role = person.role,
                kind = person.type?.serialName,
                imageUrl = person.primaryImageTag?.let { imageUrl(serverAddress, person.id.toString(), ImageType.PRIMARY, it, POSTER_WIDTH) },
            )
        },
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
