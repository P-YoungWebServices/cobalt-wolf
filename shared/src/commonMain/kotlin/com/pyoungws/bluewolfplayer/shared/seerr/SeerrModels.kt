package com.pyoungws.bluewolfplayer.shared.seerr

/** A movie or series as Seerr (Jellyseerr/Overseerr) knows it, identified by its TMDB id. */
enum class SeerrMediaType { Movie, Series }

/** Availability of a title on the media server, as reported by Seerr (mediaInfo.status). */
enum class SeerrMediaStatus {
    /** No mediaInfo yet: never requested. */
    NotRequested,
    /** Seerr does not know the title's availability. */
    Unknown,
    Pending,
    Processing,
    PartiallyAvailable,
    Available,
    Deleted,
}

/** Approval state of a single request (request.status). */
enum class SeerrRequestStatus { Pending, Approved, Declined, Failed }

/** A movie or series that can be requested. [requestStatus] is the state of the newest own request, if any. */
data class SeerrTitle(
    val tmdbId: Int,
    val type: SeerrMediaType,
    val title: String,
    val overview: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val mediaStatus: SeerrMediaStatus = SeerrMediaStatus.NotRequested,
    val requestStatus: SeerrRequestStatus? = null,
) {
    /** Already on the Jellyfin server (fully or partly). */
    val isInLibrary: Boolean
        get() = mediaStatus == SeerrMediaStatus.Available || mediaStatus == SeerrMediaStatus.PartiallyAvailable

    /** A request exists and has not been declined or failed. */
    val isRequested: Boolean
        get() = mediaStatus == SeerrMediaStatus.Pending ||
            mediaStatus == SeerrMediaStatus.Processing ||
            requestStatus == SeerrRequestStatus.Pending ||
            requestStatus == SeerrRequestStatus.Approved

    val canRequest: Boolean get() = !isInLibrary && !isRequested
}

/** A series' season with its own availability, so already-available seasons can be skipped. */
data class SeerrSeason(
    val seasonNumber: Int,
    val name: String?,
    val episodeCount: Int?,
    val status: SeerrMediaStatus = SeerrMediaStatus.NotRequested,
) {
    val isAvailable: Boolean get() = status == SeerrMediaStatus.Available || status == SeerrMediaStatus.PartiallyAvailable
}

data class SeerrTitleDetails(
    val title: SeerrTitle,
    /** Empty for movies. */
    val seasons: List<SeerrSeason> = emptyList(),
)

/** One of the user's requests on the Seerr server. */
data class SeerrRequest(
    val id: Int,
    val status: SeerrRequestStatus,
    val mediaStatus: SeerrMediaStatus,
    val type: SeerrMediaType,
    val tmdbId: Int,
    val seasons: List<Int> = emptyList(),
    val createdAt: String? = null,
    /** Filled in lazily from the title details; Seerr does not embed them in the request list. */
    val title: String? = null,
    val posterUrl: String? = null,
) {
    val isAvailable: Boolean
        get() = mediaStatus == SeerrMediaStatus.Available || mediaStatus == SeerrMediaStatus.PartiallyAvailable
}

/** The Seerr account the Jellyfin sign-in produced. */
data class SeerrAccount(val id: Int, val name: String, val isAdmin: Boolean)

data class SeerrPage<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
)
