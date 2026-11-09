package com.pyoungws.bluewolfplayer.shared.model

/** A Jellyfin server that responded and passed validation. */
data class ServerInfo(
    val address: String,
    val name: String?,
    val version: String?,
    val id: String?,
)

/** The signed-in user, with the role read from the Jellyfin user policy. */
data class UserProfile(
    val id: String,
    val name: String,
    val isAdministrator: Boolean,
)

/** A user the server lists on its login screen (`/Users/Public`; hidden users are excluded by the server). */
data class PublicUser(
    val id: String,
    val name: String,
    val hasPassword: Boolean,
)

/** Persisted credentials for the signed-in user. */
data class Session(
    val serverAddress: String,
    val userId: String,
    val accessToken: String,
)

enum class MediaKind { Movie, Series, Season, Episode, Other }

enum class LibraryType { Movies, TvShows, Other }

/** A top-level library (Jellyfin "user view"). Only movie and series libraries can be browsed so far. */
data class Library(
    val id: String,
    val name: String,
    val type: LibraryType,
    val imageUrl: String?,
) {
    val isBrowsable: Boolean get() = type != LibraryType.Other
}

/** A movie, series, season or episode with everything the browse and detail screens need. */
data class MediaItem(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val overview: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val positionTicks: Long = 0,
    val playedPercentage: Double? = null,
    val isPlayed: Boolean = false,
    val unplayedCount: Int? = null,
    val posterUrl: String? = null,
    val landscapeUrl: String? = null,
    val backdropUrl: String? = null,
) {
    val isPlayable: Boolean get() = kind == MediaKind.Movie || kind == MediaKind.Episode
    val hasProgress: Boolean get() = positionTicks > 0 && !isPlayed

    /** Progress through the item in 0..1, or null if not started. */
    val progressFraction: Float?
        get() = playedPercentage?.takeIf { hasProgress && it > 0 }?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }
}

/** "S1:E3" style label for an episode, or null if the numbers are unknown. */
fun MediaItem.episodeLabel(): String? =
    if (kind == MediaKind.Episode && episodeNumber != null) "S${seasonNumber ?: 0}:E$episodeNumber" else null

data class Page<T>(val items: List<T>, val totalCount: Int)

/** Continue Watching plus one Recently Added row per library. */
data class HomeFeed(
    val libraries: List<Library>,
    val continueWatching: List<MediaItem>,
    val recentlyAdded: List<RecentlyAddedRow>,
)

data class RecentlyAddedRow(val library: Library, val items: List<MediaItem>)

sealed interface LoadError {
    data object SessionExpired : LoadError
    data object ServerUnreachable : LoadError
    data object SecureConnectionFailed : LoadError
    data object NotFound : LoadError
    data class Unexpected(val message: String?) : LoadError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: LoadError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
