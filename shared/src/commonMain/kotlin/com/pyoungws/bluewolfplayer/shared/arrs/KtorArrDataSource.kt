package com.pyoungws.bluewolfplayer.shared.arrs

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

class KtorArrDataSource(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ArrDataSource {

    override suspend fun status(service: ArrService, settings: ArrServiceSettings) = call {
        client.get("${settings.serverUrl}/api/v3/system/status") { apiKey(settings) }
        Unit
    }

    override suspend fun calendar(
        service: ArrService,
        settings: ArrServiceSettings,
        start: LocalDate,
        end: LocalDate,
    ): List<UpcomingItem> = call {
        when (service) {
            ArrService.Radarr -> client.get("${settings.serverUrl}/api/v3/calendar") {
                apiKey(settings)
                parameter("start", start.toString())
                parameter("end", end.toString())
            }.body<List<RadarrCalendarDto>>().mapNotNull { it.toItem(settings.serverUrl.orEmpty()) }

            ArrService.Sonarr -> client.get("${settings.serverUrl}/api/v3/calendar") {
                apiKey(settings)
                parameter("start", start.toString())
                parameter("end", end.toString())
                parameter("includeSeries", true)
            }.body<List<SonarrCalendarDto>>().mapNotNull { it.toItem(settings.serverUrl.orEmpty()) }
        }
    }

    private fun HttpRequestBuilder.apiKey(settings: ArrServiceSettings) {
        header("X-Api-Key", settings.apiKey)
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(ioDispatcher) { block() }
}

@Serializable
internal data class ArrImageDto(
    val coverType: String? = null,
    val url: String? = null,
    val remoteUrl: String? = null,
)

@Serializable
internal data class RadarrCalendarDto(
    val title: String = "",
    val year: Int? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val inCinemas: String? = null,
    val digitalRelease: String? = null,
    val physicalRelease: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val images: List<ArrImageDto> = emptyList(),
) {
    /** The first available release date — the earliest the movie could appear in the library. */
    private val releaseDay: LocalDate?
        get() = listOf(digitalRelease, physicalRelease, inCinemas, releaseDate)
            .firstNotNullOfOrNull { it?.let(::parseDate) }

    fun toItem(baseUrl: String): UpcomingItem? {
        val date = releaseDay ?: return null
        return UpcomingItem(
            service = ArrService.Radarr,
            title = title,
            subtitle = year?.toString(),
            date = date,
            hasFile = hasFile,
            monitored = monitored,
            posterUrl = images.posterUrl(baseUrl),
            overview = overview,
        )
    }
}

@Serializable
internal data class SonarrCalendarDto(
    val title: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val airDate: String? = null,
    val airDateUtc: String? = null,
    val overview: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val series: SonarrSeriesDto? = null,
) {
    fun toItem(baseUrl: String): UpcomingItem? {
        val date = airDate?.let(::parseDate) ?: airDateUtc?.let(::parseDate) ?: return null
        val episodeCode = "S%02dE%02d".format(seasonNumber, episodeNumber)
        return UpcomingItem(
            service = ArrService.Sonarr,
            title = series?.title?.takeIf { it.isNotBlank() } ?: title,
            subtitle = if (title.isBlank() || title == series?.title) episodeCode else "$episodeCode · $title",
            date = date,
            hasFile = hasFile,
            monitored = monitored,
            posterUrl = series?.images.posterUrl(baseUrl),
            overview = overview,
        )
    }
}

@Serializable
internal data class SonarrSeriesDto(
    val title: String = "",
    val images: List<ArrImageDto> = emptyList(),
)

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

/** Poster served by the service itself (cached), falling back to the remote image URL. */
private fun List<ArrImageDto>?.posterUrl(baseUrl: String): String? =
    this?.firstOrNull { it.coverType == "poster" }?.let { image ->
        when {
            image.url?.startsWith("/") == true -> baseUrl + image.url
            else -> image.remoteUrl ?: image.url
        }
    }
