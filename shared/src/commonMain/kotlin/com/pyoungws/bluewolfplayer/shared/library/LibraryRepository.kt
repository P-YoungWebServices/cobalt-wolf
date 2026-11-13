package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.GenreInfo
import com.pyoungws.bluewolfplayer.shared.model.LiveTimer
import com.pyoungws.bluewolfplayer.shared.model.Lyrics
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.model.Page
import com.pyoungws.bluewolfplayer.shared.model.RecentlyAddedRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SeriesContents(val seasons: List<MediaItem>, val episodes: List<MediaItem>, val selectedSeasonId: String?)

class LibraryRepository(
    private val dataSource: JellyfinLibraryDataSource,
    private val guard: SessionGuard,
    private val settingsRepository: com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsRepository? = null,
) {
    /** Libraries, Continue Watching, Next up and a Recently Added row per library. */
    suspend fun loadHome(): Outcome<HomeFeed> = guard.run { session ->
        coroutineScope {
            val libraries = dataSource.getLibraries(session)
            val resume = async { dataSource.getResumeItems(session) }
            val nextUp = async { dataSource.getNextUp(session, settingsRepository?.settings?.value?.nextUpCutoffDays ?: 0) }
            val rows = libraries.filter { it.isBrowsable }
                .map { library -> async { RecentlyAddedRow(library, dataSource.getLatest(session, library.id)) } }
            // Resume and next-up stay separate rows (they can overlap; each row dedupes by id).
            val nextUpItems = nextUp.await().distinctBy { it.id }
                .filter { next -> resume.await().none { it.id == next.id } }
            HomeFeed(
                libraries = libraries,
                continueWatching = resume.await().distinctBy { it.id },
                nextUp = nextUpItems,
                recentlyAdded = rows.awaitAll().filter { it.items.isNotEmpty() },
            )
        }
    }

    suspend fun libraryItems(library: Library, startIndex: Int, limit: Int): Outcome<Page<MediaItem>> =
        guard.run { dataSource.getLibraryItems(it, library, startIndex, limit) }

    suspend fun item(itemId: String): Outcome<MediaItem> = guard.run { dataSource.getItem(it, itemId) }

    suspend fun seasons(seriesId: String): Outcome<List<MediaItem>> = guard.run { dataSource.getSeasons(it, seriesId) }

    /**
     * The earliest unwatched playable item inside a container (series/season/box set/playlist).
     * Series walk seasons in order and pick the first unwatched episode; other containers
     * scan direct children. Returns null when everything is watched or the item is not a container.
     */
    suspend fun firstUnwatchedItem(item: MediaItem): Outcome<MediaItem?> = guard.run { session ->
        when (item.kind) {
            com.pyoungws.bluewolfplayer.shared.model.MediaKind.Series -> {
                val seasons = dataSource.getSeasons(session, item.id)
                    .sortedBy { it.seasonNumber ?: Int.MAX_VALUE }
                for (season in seasons) {
                    val episodes = dataSource.getEpisodes(session, item.id, season.id)
                        .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
                    episodes.firstOrNull { !it.isPlayed }?.let { return@run it }
                }
                null
            }
            com.pyoungws.bluewolfplayer.shared.model.MediaKind.Season -> {
                if (item.seriesId == null) return@run null
                dataSource.getEpisodes(session, item.seriesId, item.id)
                    .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
                    .firstOrNull { !it.isPlayed }
            }
            com.pyoungws.bluewolfplayer.shared.model.MediaKind.BoxSet,
            com.pyoungws.bluewolfplayer.shared.model.MediaKind.Playlist ->
                dataSource.getItems(session, ItemQuery(parentId = item.id, limit = 100))
                    .items.firstOrNull { it.isPlayable && !it.isPlayed }
            else -> null
        }
    }

    suspend fun episodes(seriesId: String, seasonId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getEpisodes(it, seriesId, seasonId) }

    suspend fun items(query: ItemQuery): Outcome<Page<MediaItem>> =
        guard.run { dataSource.getItems(it, query) }

    suspend fun genres(libraryId: String): Outcome<List<GenreInfo>> =
        guard.run { dataSource.getGenres(it, libraryId) }

    suspend fun suggestions(library: Library): Outcome<List<MediaItem>> =
        guard.run { dataSource.getSuggestions(it, library) }

    suspend fun specialFeatures(itemId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getSpecialFeatures(it, itemId) }

    suspend fun localTrailers(itemId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getLocalTrailers(it, itemId) }

    suspend fun previousEpisode(episode: MediaItem): Outcome<MediaItem?> =
        guard.run { dataSource.getPreviousEpisode(it, episode) }

    suspend fun similarItems(itemId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getSimilar(it, itemId) }

    suspend fun instantMix(itemId: String, limit: Int = 100): Outcome<List<MediaItem>> =
        guard.run { dataSource.getInstantMix(it, itemId, limit) }

    suspend fun lyrics(itemId: String): Outcome<Lyrics?> =
        guard.run { dataSource.getLyrics(it, itemId) }

    suspend fun liveTvChannels(): Outcome<List<MediaItem>> =
        guard.run { dataSource.getLiveTvChannels(it) }

    suspend fun liveTvPrograms(channelIds: List<String>, start: java.time.LocalDateTime, end: java.time.LocalDateTime): Outcome<List<MediaItem>> =
        guard.run { dataSource.getPrograms(it, channelIds, start, end) }

    suspend fun liveTvRecordings(): Outcome<List<MediaItem>> =
        guard.run { dataSource.getLiveTvRecordings(it) }

    suspend fun liveTvTimers(): Outcome<List<LiveTimer>> =
        guard.run { dataSource.getTimers(it) }

    suspend fun liveTvSeriesTimers(): Outcome<List<LiveTimer>> =
        guard.run { dataSource.getSeriesTimers(it) }

    suspend fun recordProgram(programId: String): Outcome<Unit> =
        guard.run { dataSource.recordProgram(it, programId) }

    suspend fun recordSeries(programId: String): Outcome<Unit> =
        guard.run { dataSource.recordSeries(it, programId) }

    suspend fun cancelTimer(timerId: String): Outcome<Unit> =
        guard.run { dataSource.cancelTimer(it, timerId) }

    suspend fun cancelSeriesTimer(timerId: String): Outcome<Unit> =
        guard.run { dataSource.cancelSeriesTimer(it, timerId) }

    suspend fun deleteRecording(recordingId: String): Outcome<Unit> =
        guard.run { dataSource.deleteRecording(it, recordingId) }

    suspend fun upcomingEpisodes(libraryId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getUpcoming(it, libraryId) }

    suspend fun studios(libraryId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getStudios(it, libraryId) }

    suspend fun createPlaylist(name: String, itemIds: List<String>): Outcome<String> =
        guard.run { dataSource.createPlaylist(it, name, itemIds) }

    suspend fun addToPlaylist(playlistId: String, itemIds: List<String>): Outcome<Unit> =
        guard.run { dataSource.addToPlaylist(it, playlistId, itemIds) }

    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>): Outcome<Unit> =
        guard.run { dataSource.removeFromPlaylist(it, playlistId, entryIds) }

    suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Outcome<Unit> =
        guard.run { dataSource.movePlaylistItem(it, playlistId, entryId, newIndex) }

    suspend fun createCollection(name: String, itemIds: List<String>): Outcome<String> =
        guard.run { dataSource.createCollection(it, name, itemIds) }

    suspend fun addToCollection(collectionId: String, itemIds: List<String>): Outcome<Unit> =
        guard.run { dataSource.addToCollection(it, collectionId, itemIds) }

    suspend fun removeFromCollection(collectionId: String, itemIds: List<String>): Outcome<Unit> =
        guard.run { dataSource.removeFromCollection(it, collectionId, itemIds) }

    suspend fun nextUpForSeries(seriesId: String): Outcome<MediaItem?> =
        guard.run { dataSource.getNextUpForSeries(it, seriesId) }

    suspend fun setPlayed(item: MediaItem, played: Boolean): Outcome<Unit> =
        guard.run { dataSource.setPlayed(it, item.id, played) }

    suspend fun deleteItems(items: List<MediaItem>): Outcome<Unit> =
        guard.run { dataSource.deleteItems(it, items.map(MediaItem::id)) }

    /** Authenticated direct-play URL for sharing/copying. Never log it. */
    fun streamUrl(item: MediaItem): String? =
        guard.sessionOrNull()?.let { dataSource.streamUrl(it, item) }

    suspend fun itemDto(itemId: String): Outcome<org.jellyfin.sdk.model.api.BaseItemDto> =
        guard.run { dataSource.getItemDto(it, itemId) }

    suspend fun updateItemDto(itemId: String, dto: org.jellyfin.sdk.model.api.BaseItemDto): Outcome<Unit> =
        guard.run { dataSource.updateItemDto(it, itemId, dto) }

    suspend fun refreshItem(itemId: String, mode: com.pyoungws.bluewolfplayer.shared.model.MetadataRefresh): Outcome<Unit> =
        guard.run { dataSource.refreshItem(it, itemId, mode) }

    suspend fun identifySearch(item: MediaItem, name: String, year: Int?): Outcome<List<com.pyoungws.bluewolfplayer.shared.model.IdentifyMatch>> =
        guard.run { dataSource.identifySearch(it, item, name, year) }

    suspend fun applyIdentify(itemId: String, match: com.pyoungws.bluewolfplayer.shared.model.IdentifyMatch): Outcome<Unit> =
        guard.run { dataSource.applyIdentify(it, itemId, match) }

    suspend fun searchRemoteSubtitles(itemId: String, language: String): Outcome<List<com.pyoungws.bluewolfplayer.shared.model.SubtitleMatch>> =
        guard.run { dataSource.searchRemoteSubtitles(it, itemId, language) }

    suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Outcome<Unit> =
        guard.run { dataSource.downloadRemoteSubtitle(it, itemId, subtitleId) }

    suspend fun setFavorite(item: MediaItem, favorite: Boolean): Outcome<Unit> =
        guard.run { dataSource.setFavorite(it, item.id, favorite) }

    suspend fun search(term: String, limit: Int = SEARCH_LIMIT): Outcome<List<MediaItem>> {
        val query = term.trim()
        if (query.isEmpty()) return Outcome.Success(emptyList())
        return guard.run { dataSource.search(it, query, limit) }
    }

    private companion object {
        const val SEARCH_LIMIT = 50
    }
}
