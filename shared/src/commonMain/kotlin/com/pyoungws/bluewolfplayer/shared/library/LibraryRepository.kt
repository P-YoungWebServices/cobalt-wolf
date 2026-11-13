package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.GenreInfo
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

    suspend fun nextUpForSeries(seriesId: String): Outcome<MediaItem?> =
        guard.run { dataSource.getNextUpForSeries(it, seriesId) }

    suspend fun setPlayed(item: MediaItem, played: Boolean): Outcome<Unit> =
        guard.run { dataSource.setPlayed(it, item.id, played) }

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
