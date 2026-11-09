package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.model.HomeFeed
import com.pyoungws.bluewolfplayer.shared.model.Library
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
) {
    /** Libraries, Continue Watching (in-progress items, then next-up episodes) and a Recently Added row per library. */
    suspend fun loadHome(): Outcome<HomeFeed> = guard.run { session ->
        coroutineScope {
            val libraries = dataSource.getLibraries(session)
            val resume = async { dataSource.getResumeItems(session) }
            val nextUp = async { dataSource.getNextUp(session) }
            val rows = libraries.filter { it.isBrowsable }
                .map { library -> async { RecentlyAddedRow(library, dataSource.getLatest(session, library.id)) } }
            HomeFeed(
                libraries = libraries,
                continueWatching = (resume.await() + nextUp.await()).distinctBy { it.id },
                recentlyAdded = rows.awaitAll().filter { it.items.isNotEmpty() },
            )
        }
    }

    suspend fun libraryItems(library: Library, startIndex: Int, limit: Int): Outcome<Page<MediaItem>> =
        guard.run { dataSource.getLibraryItems(it, library, startIndex, limit) }

    suspend fun item(itemId: String): Outcome<MediaItem> = guard.run { dataSource.getItem(it, itemId) }

    suspend fun seasons(seriesId: String): Outcome<List<MediaItem>> = guard.run { dataSource.getSeasons(it, seriesId) }

    suspend fun episodes(seriesId: String, seasonId: String): Outcome<List<MediaItem>> =
        guard.run { dataSource.getEpisodes(it, seriesId, seasonId) }

    suspend fun search(term: String, limit: Int = SEARCH_LIMIT): Outcome<List<MediaItem>> {
        val query = term.trim()
        if (query.isEmpty()) return Outcome.Success(emptyList())
        return guard.run { dataSource.search(it, query, limit) }
    }

    private companion object {
        const val SEARCH_LIMIT = 50
    }
}
