package com.pyoungws.bluewolfplayer.shared.library

import com.pyoungws.bluewolfplayer.shared.auth.ADMIN
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.auth.SERVER
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.model.GenreInfo
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaKind
import com.pyoungws.bluewolfplayer.shared.model.Page
import com.pyoungws.bluewolfplayer.shared.model.Session

val SESSION = Session(SERVER, ADMIN.id, "token")

val MOVIES = Library("lib-movies", "Movies", LibraryType.Movies, null)
val SHOWS = Library("lib-shows", "Shows", LibraryType.TvShows, null)
val MUSIC = Library("lib-music", "Music", LibraryType.Other, null)

fun movie(id: String, name: String = id, positionTicks: Long = 0, played: Boolean = false) =
    MediaItem(id, name, MediaKind.Movie, positionTicks = positionTicks, isPlayed = played, playedPercentage = if (positionTicks > 0) 50.0 else null)

fun series(id: String) = MediaItem(id, id, MediaKind.Series)

fun season(id: String, number: Int, seriesId: String = "series-1") =
    MediaItem(id, "Season $number", MediaKind.Season, seriesId = seriesId, seasonNumber = number)

fun episode(id: String, number: Int, seasonNumber: Int = 1, played: Boolean = false, positionTicks: Long = 0) = MediaItem(
    id, id, MediaKind.Episode, seriesId = "series-1", seasonId = "season-$seasonNumber",
    seasonNumber = seasonNumber, episodeNumber = number,
    isPlayed = played, positionTicks = positionTicks, playedPercentage = if (positionTicks > 0) 30.0 else null,
)

/** A signed-in [AuthRepository] backed by in-memory fakes. */
suspend fun signedInAuthRepository(): AuthRepository =
    AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }

class FakeLibraryDataSource : JellyfinLibraryDataSource {
    val calls = mutableListOf<String>()

    var libraries: () -> List<Library> = { listOf(MOVIES, SHOWS, MUSIC) }
    var resume: () -> List<MediaItem> = { emptyList() }
    var nextUp: () -> List<MediaItem> = { emptyList() }
    var latest: (libraryId: String) -> List<MediaItem> = { emptyList() }
    var libraryItems: (library: Library, startIndex: Int, limit: Int) -> Page<MediaItem> = { _, _, _ -> Page(emptyList(), 0) }
    var item: (itemId: String) -> MediaItem = { movie(it) }
    var seasons: (seriesId: String) -> List<MediaItem> = { emptyList() }
    var episodes: (seriesId: String, seasonId: String) -> List<MediaItem> = { _, _ -> emptyList() }
    var search: (term: String) -> List<MediaItem> = { emptyList() }

    override suspend fun getLibraries(session: Session): List<Library> = libraries().also { calls += "libraries" }
    override suspend fun getResumeItems(session: Session): List<MediaItem> = resume().also { calls += "resume" }
    override suspend fun getNextUp(session: Session, nextUpCutoffDays: Int): List<MediaItem> = nextUp().also { calls += "nextUp($nextUpCutoffDays)" }
    override suspend fun getLatest(session: Session, libraryId: String): List<MediaItem> = latest(libraryId).also { calls += "latest($libraryId)" }
    override suspend fun getLibraryItems(session: Session, library: Library, startIndex: Int, limit: Int): Page<MediaItem> =
        libraryItems(library, startIndex, limit).also { calls += "items(${library.id}, $startIndex)" }
    override suspend fun getItem(session: Session, itemId: String): MediaItem = item(itemId).also { calls += "item($itemId)" }
    override suspend fun getSeasons(session: Session, seriesId: String): List<MediaItem> = seasons(seriesId).also { calls += "seasons($seriesId)" }
    override suspend fun getEpisodes(session: Session, seriesId: String, seasonId: String): List<MediaItem> =
        episodes(seriesId, seasonId).also { calls += "episodes($seasonId)" }
    override suspend fun search(session: Session, term: String, limit: Int): List<MediaItem> = search(term).also { calls += "search($term)" }
    var items: (query: ItemQuery) -> Page<MediaItem> = { Page(emptyList(), 0) }
    var genres: (libraryId: String) -> List<GenreInfo> = { emptyList() }
    var suggestions: (library: Library) -> List<MediaItem> = { emptyList() }
    var specialFeatures: (itemId: String) -> List<MediaItem> = { emptyList() }
    var localTrailers: (itemId: String) -> List<MediaItem> = { emptyList() }
    var previousEpisode: (episode: MediaItem) -> MediaItem? = { null }
    override suspend fun getItems(session: Session, query: ItemQuery): Page<MediaItem> = items(query).also { calls += "items" }
    override suspend fun getGenres(session: Session, libraryId: String): List<GenreInfo> = genres(libraryId).also { calls += "genres($libraryId)" }
    override suspend fun getSuggestions(session: Session, library: Library): List<MediaItem> = suggestions(library).also { calls += "suggestions(${library.id})" }
    override suspend fun getSpecialFeatures(session: Session, itemId: String): List<MediaItem> = specialFeatures(itemId).also { calls += "specialFeatures($itemId)" }
    override suspend fun getLocalTrailers(session: Session, itemId: String): List<MediaItem> = localTrailers(itemId).also { calls += "localTrailers($itemId)" }
    override suspend fun getPreviousEpisode(session: Session, episode: MediaItem): MediaItem? = previousEpisode(episode).also { calls += "previousEpisode(${episode.id})" }
    var similar: (itemId: String) -> List<MediaItem> = { emptyList() }
    var nextUpForSeries: (seriesId: String) -> MediaItem? = { null }
    override suspend fun getSimilar(session: Session, itemId: String): List<MediaItem> = similar(itemId).also { calls += "similar($itemId)" }
    override suspend fun getNextUpForSeries(session: Session, seriesId: String): MediaItem? = nextUpForSeries(seriesId).also { calls += "nextUpSeries($seriesId)" }
    override suspend fun setPlayed(session: Session, itemId: String, played: Boolean) { calls += "setPlayed($itemId,$played)" }
    override suspend fun setFavorite(session: Session, itemId: String, favorite: Boolean) { calls += "setFavorite($itemId,$favorite)" }
}
