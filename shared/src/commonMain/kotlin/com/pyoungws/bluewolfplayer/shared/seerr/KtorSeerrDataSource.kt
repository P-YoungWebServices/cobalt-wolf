package com.pyoungws.bluewolfplayer.shared.seerr

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class KtorSeerrDataSource(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SeerrDataSource {

    override suspend fun signInWithJellyfin(serverUrl: String, username: String, password: String, hostname: String): SeerrAccount =
        call {
            client.post("$serverUrl/api/v1/auth/jellyfin") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("username", username)
                        put("password", password)
                        put("hostname", hostname)
                    },
                )
            }.body<UserDto>().toAccount()
        }

    override suspend fun currentUser(serverUrl: String): SeerrAccount = call {
        client.get("$serverUrl/api/v1/auth/me").body<UserDto>().toAccount()
    }

    override suspend fun signOut(serverUrl: String) = call {
        client.post("$serverUrl/api/v1/auth/logout")
        Unit
    }

    override suspend fun status(serverUrl: String) = call {
        client.get("$serverUrl/api/v1/status")
        Unit
    }

    override suspend fun discoverMovies(serverUrl: String, page: Int): SeerrPage<SeerrTitle> = call {
        client.get("$serverUrl/api/v1/discover/movies") { parameter("page", page) }
            .body<ResultPageDto>().toPage(SeerrMediaType.Movie)
    }

    override suspend fun discoverSeries(serverUrl: String, page: Int): SeerrPage<SeerrTitle> = call {
        client.get("$serverUrl/api/v1/discover/tv") { parameter("page", page) }
            .body<ResultPageDto>().toPage(SeerrMediaType.Series)
    }

    override suspend fun trending(serverUrl: String, page: Int): SeerrPage<SeerrTitle> = call {
        client.get("$serverUrl/api/v1/discover/trending") { parameter("page", page) }
            .body<ResultPageDto>().toPage()
    }

    override suspend fun search(serverUrl: String, query: String, page: Int): SeerrPage<SeerrTitle> = call {
        client.get("$serverUrl/api/v1/search") {
            parameter("query", query)
            parameter("page", page)
        }.body<ResultPageDto>().toPage()
    }

    override suspend fun movieDetails(serverUrl: String, tmdbId: Int): SeerrTitleDetails = call {
        client.get("$serverUrl/api/v1/movie/$tmdbId").body<MovieDetailsDto>().toDetails()
    }

    override suspend fun seriesDetails(serverUrl: String, tmdbId: Int): SeerrTitleDetails = call {
        client.get("$serverUrl/api/v1/tv/$tmdbId").body<TvDetailsDto>().toDetails()
    }

    override suspend fun createRequest(serverUrl: String, type: SeerrMediaType, tmdbId: Int, seasons: List<Int>?): SeerrRequest =
        call {
            client.post("$serverUrl/api/v1/request") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("mediaType", if (type == SeerrMediaType.Movie) "movie" else "tv")
                        put("mediaId", tmdbId)
                        if (seasons != null) putJsonArray("seasons") { seasons.forEach(::add) }
                    },
                )
            }.body<RequestDto>().toRequest()
        }

    override suspend fun requests(serverUrl: String, filter: SeerrRequestFilter, take: Int, skip: Int): SeerrPage<SeerrRequest> = call {
        client.get("$serverUrl/api/v1/request") {
            parameter("take", take)
            parameter("skip", skip)
            parameter("filter", filter.apiValue)
            parameter("sort", "added")
            parameter("sortDirection", "desc")
        }.body<RequestPageDto>().toRequestPage()
    }

    override suspend fun requestDetails(serverUrl: String, requestId: Int): SeerrRequest = call {
        client.get("$serverUrl/api/v1/request/$requestId").body<RequestDto>().toRequest()
    }

    override suspend fun approveRequest(serverUrl: String, requestId: Int): SeerrRequest = call {
        client.post("$serverUrl/api/v1/request/$requestId/approve").body<RequestDto>().toRequest()
    }

    override suspend fun declineRequest(serverUrl: String, requestId: Int): SeerrRequest = call {
        client.post("$serverUrl/api/v1/request/$requestId/decline").body<RequestDto>().toRequest()
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(ioDispatcher) { block() }
}

// region DTOs

@Serializable
internal data class UserDto(
    val id: Int,
    val username: String? = null,
    val jellyfinUsername: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val permissions: Int = 0,
)

@Serializable
internal data class MediaInfoDto(
    val id: Int? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val mediaType: String? = null,
    val status: Int? = null,
    val seasons: List<SeasonStatusDto> = emptyList(),
    val requests: List<RequestDto> = emptyList(),
)

@Serializable
internal data class SeasonStatusDto(val seasonNumber: Int, val status: Int? = null)

@Serializable
internal data class RequestedSeasonDto(val seasonNumber: Int, val status: Int? = null)

@Serializable
internal data class RequestDto(
    val id: Int,
    val status: Int,
    val type: String? = null,
    val is4k: Boolean = false,
    val seasons: List<RequestedSeasonDto> = emptyList(),
    val media: MediaInfoDto? = null,
    val requestedBy: UserDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class ResultDto(
    val id: Int,
    val mediaType: String? = null,
    val title: String? = null,
    val originalTitle: String? = null,
    val name: String? = null,
    val originalName: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: MediaInfoDto? = null,
)

@Serializable
internal data class ResultPageDto(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<ResultDto> = emptyList(),
)

@Serializable
internal data class PageInfoDto(
    val page: Int = 1,
    val pages: Int = 1,
    val pageSize: Int = 20,
    val results: Int = 0,
)

@Serializable
internal data class RequestPageDto(
    val pageInfo: PageInfoDto? = null,
    val results: List<RequestDto> = emptyList(),
)

@Serializable
internal data class SeasonDto(
    val id: Int? = null,
    val seasonNumber: Int,
    val name: String? = null,
    val episodeCount: Int? = null,
)

@Serializable
internal data class MovieDetailsDto(
    val id: Int,
    val title: String? = null,
    val originalTitle: String? = null,
    val releaseDate: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: MediaInfoDto? = null,
)

@Serializable
internal data class TvDetailsDto(
    val id: Int,
    val name: String? = null,
    val originalName: String? = null,
    val firstAirDate: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Double? = null,
    val seasons: List<SeasonDto> = emptyList(),
    val mediaInfo: MediaInfoDto? = null,
)

// endregion

// region mapping

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
private const val PERMISSION_ADMIN = 2

private fun UserDto.toAccount() = SeerrAccount(
    id = id,
    name = displayName ?: jellyfinUsername ?: username ?: email ?: "user $id",
    isAdmin = permissions and PERMISSION_ADMIN != 0,
)

private fun posterUrl(path: String?) = path?.let { "$TMDB_IMAGE_BASE/w342$it" }
private fun backdropUrl(path: String?) = path?.let { "$TMDB_IMAGE_BASE/w780$it" }
private fun yearOf(date: String?) = date?.take(4)?.toIntOrNull()

internal fun Int?.toMediaStatus(): SeerrMediaStatus = when (this) {
    null -> SeerrMediaStatus.NotRequested
    1 -> SeerrMediaStatus.Unknown
    2 -> SeerrMediaStatus.Pending
    3 -> SeerrMediaStatus.Processing
    4 -> SeerrMediaStatus.PartiallyAvailable
    5 -> SeerrMediaStatus.Available
    6 -> SeerrMediaStatus.Deleted
    else -> SeerrMediaStatus.Unknown
}

internal fun Int.toRequestStatus(): SeerrRequestStatus = when (this) {
    1 -> SeerrRequestStatus.Pending
    2 -> SeerrRequestStatus.Approved
    3 -> SeerrRequestStatus.Declined
    else -> SeerrRequestStatus.Failed
}

private fun String?.toMediaType(): SeerrMediaType? = when (this) {
    "movie" -> SeerrMediaType.Movie
    "tv" -> SeerrMediaType.Series
    else -> null
}

/** The status of the newest request on a title, if Seerr reports one. */
private fun MediaInfoDto?.latestRequestStatus(): SeerrRequestStatus? =
    this?.requests?.maxByOrNull { it.updatedAt ?: it.createdAt.orEmpty() }?.status?.toRequestStatus()

private fun ResultDto.toTitle(fallbackType: SeerrMediaType?): SeerrTitle? {
    // A declared but unknown mediaType ("person", "collection") is always dropped; a missing one
    // falls back to the discover endpoint's kind.
    val declaredType = mediaType.toMediaType()
    if (mediaType != null && declaredType == null) return null
    val type = declaredType ?: fallbackType ?: return null
    val title = title ?: name ?: originalTitle ?: originalName ?: return null
    return SeerrTitle(
        tmdbId = id,
        type = type,
        title = title,
        overview = overview,
        year = yearOf(releaseDate ?: firstAirDate),
        rating = voteAverage?.toFloat(),
        posterUrl = posterUrl(posterPath),
        backdropUrl = backdropUrl(backdropPath),
        mediaStatus = mediaInfo?.status.toMediaStatus(),
        requestStatus = mediaInfo.latestRequestStatus(),
    )
}

private fun ResultPageDto.toPage(assumedType: SeerrMediaType? = null) = SeerrPage(
    items = results.mapNotNull { it.toTitle(assumedType) },
    page = page,
    totalPages = totalPages,
    totalResults = totalResults,
)

private fun MediaInfoDto?.seasonStatuses(): Map<Int, SeerrMediaStatus> =
    this?.seasons?.associate { it.seasonNumber to it.status.toMediaStatus() } ?: emptyMap()

private fun MovieDetailsDto.toDetails() = SeerrTitleDetails(
    title = SeerrTitle(
        tmdbId = id,
        type = SeerrMediaType.Movie,
        title = title ?: originalTitle ?: "Movie $id",
        overview = overview,
        year = yearOf(releaseDate),
        rating = voteAverage?.toFloat(),
        posterUrl = posterUrl(posterPath),
        backdropUrl = backdropUrl(backdropPath),
        mediaStatus = mediaInfo?.status.toMediaStatus(),
        requestStatus = mediaInfo.latestRequestStatus(),
    ),
)

private fun TvDetailsDto.toDetails(): SeerrTitleDetails {
    val statuses = mediaInfo.seasonStatuses()
    return SeerrTitleDetails(
        title = SeerrTitle(
            tmdbId = id,
            type = SeerrMediaType.Series,
            title = name ?: originalName ?: "Series $id",
            overview = overview,
            year = yearOf(firstAirDate),
            rating = voteAverage?.toFloat(),
            posterUrl = posterUrl(posterPath),
            backdropUrl = backdropUrl(backdropPath),
            mediaStatus = mediaInfo?.status.toMediaStatus(),
            requestStatus = mediaInfo.latestRequestStatus(),
        ),
        seasons = seasons
            .sortedBy { it.seasonNumber }
            .map { SeerrSeason(it.seasonNumber, it.name, it.episodeCount, statuses[it.seasonNumber] ?: SeerrMediaStatus.NotRequested) },
    )
}

private fun RequestDto.toRequest() = SeerrRequest(
    id = id,
    status = status.toRequestStatus(),
    mediaStatus = media?.status.toMediaStatus(),
    type = (type ?: media?.mediaType).toMediaType() ?: SeerrMediaType.Movie,
    tmdbId = media?.tmdbId ?: 0,
    seasons = seasons.map { it.seasonNumber },
    is4k = is4k,
    createdAt = createdAt,
    updatedAt = updatedAt,
    requestedById = requestedBy?.id,
    requestedByName = requestedBy?.let { it.displayName ?: it.jellyfinUsername ?: it.username ?: it.email },
)

private fun RequestPageDto.toRequestPage() = SeerrPage(
    items = results.map { it.toRequest() },
    page = pageInfo?.page ?: 1,
    totalPages = pageInfo?.pages ?: 1,
    totalResults = pageInfo?.results ?: results.size,
)

// endregion
