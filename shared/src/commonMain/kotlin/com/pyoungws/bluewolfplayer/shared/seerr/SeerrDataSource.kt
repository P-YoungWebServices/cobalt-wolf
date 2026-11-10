package com.pyoungws.bluewolfplayer.shared.seerr

/**
 * Seerr (Jellyseerr/Overseerr) REST API calls. Implementations throw Ktor client exceptions on
 * failure; the repository maps them to [com.pyoungws.bluewolfplayer.shared.model.Outcome].
 */
interface SeerrDataSource {
    /** POST /auth/jellyfin. On success the session cookie is stored by the HTTP client's cookie storage. */
    suspend fun signInWithJellyfin(serverUrl: String, username: String, password: String, hostname: String): SeerrAccount
    /** GET /auth/me. Throws a 401/403 response exception when the stored cookie is invalid. */
    suspend fun currentUser(serverUrl: String): SeerrAccount
    /** POST /auth/logout; best-effort, the local cookie is always cleared by the caller. */
    suspend fun signOut(serverUrl: String)
    /** GET /status; unauthenticated liveness check used to pick the scheme during setup. */
    suspend fun status(serverUrl: String)

    suspend fun discoverMovies(serverUrl: String, page: Int = 1): SeerrPage<SeerrTitle>
    suspend fun discoverSeries(serverUrl: String, page: Int = 1): SeerrPage<SeerrTitle>
    suspend fun trending(serverUrl: String, page: Int = 1): SeerrPage<SeerrTitle>
    suspend fun search(serverUrl: String, query: String, page: Int = 1): SeerrPage<SeerrTitle>

    suspend fun movieDetails(serverUrl: String, tmdbId: Int): SeerrTitleDetails
    suspend fun seriesDetails(serverUrl: String, tmdbId: Int): SeerrTitleDetails

    /** POST /request. [seasons] is required for series (the season numbers to request) and ignored for movies. */
    suspend fun createRequest(serverUrl: String, type: SeerrMediaType, tmdbId: Int, seasons: List<Int>?): SeerrRequest
    /** GET /request, scoped to the signed-in user (the server restricts non-managers to their own). */
    suspend fun requests(serverUrl: String, take: Int = 50, skip: Int = 0): SeerrPage<SeerrRequest>
}
