package com.pyoungws.bluewolfplayer.shared.seerr

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository

const val SEERR_URL = "https://seerr.example.com"
val SEERR_ACCOUNT = SeerrAccount(id = 7, name = "alice", isAdmin = false)

class InMemorySeerrSessionStore(var stored: String? = null) : SeerrSessionStore {
    override suspend fun load(): String? = stored
    override suspend fun save(cookieValue: String) {
        stored = cookieValue
    }
    override suspend fun clear() {
        stored = null
    }
}

class InMemorySeerrSettingsStore(var saved: SeerrSettings = SeerrSettings()) : SeerrSettingsStore {
    override fun load() = saved
    override fun save(settings: SeerrSettings) {
        saved = settings
    }
}

fun seerrMovie(
    tmdbId: Int,
    title: String = "Movie $tmdbId",
    mediaStatus: SeerrMediaStatus = SeerrMediaStatus.NotRequested,
    requestStatus: SeerrRequestStatus? = null,
) = SeerrTitle(tmdbId, SeerrMediaType.Movie, title, year = 2024, mediaStatus = mediaStatus, requestStatus = requestStatus)

fun seerrSeries(
    tmdbId: Int,
    title: String = "Series $tmdbId",
    mediaStatus: SeerrMediaStatus = SeerrMediaStatus.NotRequested,
    requestStatus: SeerrRequestStatus? = null,
) = SeerrTitle(tmdbId, SeerrMediaType.Series, title, year = 2024, mediaStatus = mediaStatus, requestStatus = requestStatus)

fun seerrRequest(
    id: Int,
    tmdbId: Int = id,
    type: SeerrMediaType = SeerrMediaType.Movie,
    status: SeerrRequestStatus = SeerrRequestStatus.Pending,
    mediaStatus: SeerrMediaStatus = SeerrMediaStatus.Pending,
    seasons: List<Int> = emptyList(),
) = SeerrRequest(id, status, mediaStatus, type, tmdbId, seasons)

fun <T> pageOf(items: List<T>) = SeerrPage(items, page = 1, totalPages = 1, totalResults = items.size)

class FakeSeerrDataSource : SeerrDataSource {
    val calls = mutableListOf<String>()
    var signInArgs: Triple<String, String, String>? = null
    var requestArgs: Triple<SeerrMediaType, Int, List<Int>?>? = null

    var signIn: (username: String, password: String, hostname: String) -> SeerrAccount = { _, _, _ -> SEERR_ACCOUNT }
    var currentUser: () -> SeerrAccount = { SEERR_ACCOUNT }
    var signOut: () -> Unit = {}
    var onStatus: (serverUrl: String) -> Unit = {}
    var trending: () -> SeerrPage<SeerrTitle> = { pageOf(emptyList()) }
    var discoverMovies: () -> SeerrPage<SeerrTitle> = { pageOf(emptyList()) }
    var discoverSeries: () -> SeerrPage<SeerrTitle> = { pageOf(emptyList()) }
    var search: (query: String) -> SeerrPage<SeerrTitle> = { pageOf(emptyList()) }
    var movieDetails: (tmdbId: Int) -> SeerrTitleDetails = { SeerrTitleDetails(seerrMovie(it)) }
    var seriesDetails: (tmdbId: Int) -> SeerrTitleDetails = { SeerrTitleDetails(seerrSeries(it)) }
    var createRequest: (type: SeerrMediaType, tmdbId: Int, seasons: List<Int>?) -> SeerrRequest =
        { type, tmdbId, seasons -> seerrRequest(id = 1, tmdbId = tmdbId, type = type, seasons = seasons.orEmpty()) }
    var requests: () -> SeerrPage<SeerrRequest> = { pageOf(emptyList()) }

    override suspend fun signInWithJellyfin(serverUrl: String, username: String, password: String, hostname: String): SeerrAccount {
        calls += "signIn($username, $hostname)"
        signInArgs = Triple(username, password, hostname)
        return signIn(username, password, hostname)
    }

    override suspend fun currentUser(serverUrl: String): SeerrAccount = currentUser().also { calls += "me" }

    override suspend fun signOut(serverUrl: String) = signOut().also { calls += "signOut" }
    override suspend fun status(serverUrl: String) = onStatus(serverUrl).also { calls += "status($serverUrl)" }

    override suspend fun discoverMovies(serverUrl: String, page: Int) = discoverMovies().also { calls += "discoverMovies" }
    override suspend fun discoverSeries(serverUrl: String, page: Int) = discoverSeries().also { calls += "discoverSeries" }
    override suspend fun trending(serverUrl: String, page: Int) = trending().also { calls += "trending" }
    override suspend fun search(serverUrl: String, query: String, page: Int) = search(query).also { calls += "search($query)" }
    override suspend fun movieDetails(serverUrl: String, tmdbId: Int) = movieDetails(tmdbId).also { calls += "movie($tmdbId)" }
    override suspend fun seriesDetails(serverUrl: String, tmdbId: Int) = seriesDetails(tmdbId).also { calls += "series($tmdbId)" }
    override suspend fun createRequest(serverUrl: String, type: SeerrMediaType, tmdbId: Int, seasons: List<Int>?): SeerrRequest {
        calls += "createRequest($type, $tmdbId, $seasons)"
        requestArgs = Triple(type, tmdbId, seasons)
        return createRequest(type, tmdbId, seasons)
    }
    override suspend fun requests(serverUrl: String, take: Int, skip: Int) = requests().also { calls += "requests" }
}

/** A [SeerrRepository] on in-memory stores, signed in to Jellyfin via [authRepository]. */
fun seerrRepository(
    dataSource: SeerrDataSource,
    authRepository: AuthRepository,
    settings: SeerrSettings = SeerrSettings(SEERR_URL),
    cookie: String? = "sid",
    scope: kotlinx.coroutines.CoroutineScope,
    sessionStore: InMemorySeerrSessionStore = InMemorySeerrSessionStore(cookie),
): SeerrRepository = SeerrRepository(
    dataSource = dataSource,
    settings = SeerrSettingsRepository(InMemorySeerrSettingsStore(settings)),
    sessionStore = sessionStore,
    authRepository = authRepository,
    scope = scope,
)
