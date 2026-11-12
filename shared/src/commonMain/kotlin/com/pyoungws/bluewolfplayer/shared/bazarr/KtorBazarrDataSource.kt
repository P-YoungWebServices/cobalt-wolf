package com.pyoungws.bluewolfplayer.shared.bazarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrServiceSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ParametersBuilder
import io.ktor.http.parameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KtorBazarrDataSource(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BazarrDataSource {

    override suspend fun wantedMovies(settings: ArrServiceSettings): List<WantedMovie> = call {
        client.get("${settings.serverUrl}/api/movies/wanted") { apiKey(settings) }
            .body<BazarrPage<BazarrWantedMovieDto>>().data.map { it.toModel() }
    }

    override suspend fun wantedEpisodes(settings: ArrServiceSettings): List<WantedEpisode> = call {
        client.get("${settings.serverUrl}/api/episodes/wanted") { apiKey(settings) }
            .body<BazarrPage<BazarrWantedEpisodeDto>>().data.map { it.toModel() }
    }

    override suspend fun searchMovieSubtitles(settings: ArrServiceSettings, radarrId: Int): List<SubtitleCandidate> = call {
        client.get("${settings.serverUrl}/api/providers/movies") {
            apiKey(settings)
            parameter("radarrid", radarrId)
            // Provider searches query every enabled subtitle source; they can run well over a minute.
            timeout { requestTimeoutMillis = SEARCH_TIMEOUT_MILLIS }
        }.body<List<SubtitleCandidateDto>>().map { it.toModel() }
    }

    override suspend fun searchEpisodeSubtitles(settings: ArrServiceSettings, sonarrEpisodeId: Int): List<SubtitleCandidate> = call {
        client.get("${settings.serverUrl}/api/providers/episodes") {
            apiKey(settings)
            parameter("episodeid", sonarrEpisodeId)
            timeout { requestTimeoutMillis = SEARCH_TIMEOUT_MILLIS }
        }.body<List<SubtitleCandidateDto>>().map { it.toModel() }
    }

    override suspend fun downloadMovieSubtitle(settings: ArrServiceSettings, radarrId: Int, candidate: SubtitleCandidate) {
        call {
        client.submitForm(
            url = "${settings.serverUrl}/api/providers/movies",
            formParameters = parameters {
                append("radarrid", radarrId.toString())
                appendCandidate(candidate)
            },
        ) { apiKey(settings) }
        }
    }

    override suspend fun downloadEpisodeSubtitle(
        settings: ArrServiceSettings,
        sonarrSeriesId: Int,
        sonarrEpisodeId: Int,
        candidate: SubtitleCandidate,
    ) {
        call {
        client.submitForm(
            url = "${settings.serverUrl}/api/providers/episodes",
            formParameters = parameters {
                append("seriesid", sonarrSeriesId.toString())
                append("episodeid", sonarrEpisodeId.toString())
                appendCandidate(candidate)
            },
        ) { apiKey(settings) }
        }
    }

    /** Bazarr's reqparse fields want capitalized "True"/"False" for its booleans. */
    private fun ParametersBuilder.appendCandidate(candidate: SubtitleCandidate) {
        append("provider", candidate.provider)
        append("subtitle", candidate.subtitle)
        append("hi", candidate.hearingImpaired.toBazarrBoolean())
        append("forced", candidate.forced.toBazarrBoolean())
        append("original_format", candidate.originalFormat.toBazarrBoolean())
    }

    private fun Boolean.toBazarrBoolean() = if (this) "True" else "False"

    private fun HttpRequestBuilder.apiKey(settings: ArrServiceSettings) {
        header("X-Api-Key", settings.apiKey)
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    private companion object {
        const val SEARCH_TIMEOUT_MILLIS = 120_000L
    }
}

/** The `{"data": [...], "total": n}` envelope every paged Bazarr endpoint answers with. */
@Serializable
internal data class BazarrPage<T>(
    val data: List<T> = emptyList(),
    val total: Int = 0,
)

@Serializable
internal data class BazarrSubtitleDto(
    val name: String = "",
    val forced: Boolean = false,
    val hi: Boolean = false,
) {
    fun toModel() = MissingSubtitle(name, forced, hi)
}

@Serializable
internal data class BazarrWantedMovieDto(
    val title: String = "",
    val radarrId: Int? = null,
    @SerialName("missing_subtitles") val missingSubtitles: List<BazarrSubtitleDto> = emptyList(),
) {
    fun toModel() = WantedMovie(title, missingSubtitles.map { it.toModel() }, radarrId)
}

@Serializable
internal data class BazarrWantedEpisodeDto(
    val seriesTitle: String = "",
    @SerialName("episode_number") val episodeNumber: String? = null,
    val episodeTitle: String? = null,
    val sonarrSeriesId: Int? = null,
    val sonarrEpisodeId: Int? = null,
    @SerialName("missing_subtitles") val missingSubtitles: List<BazarrSubtitleDto> = emptyList(),
) {
    fun toModel() = WantedEpisode(
        seriesTitle = seriesTitle,
        episodeCode = episodeNumber.toEpisodeCode(),
        episodeTitle = episodeTitle?.takeIf { it.isNotBlank() },
        missing = missingSubtitles.map { it.toModel() },
        sonarrSeriesId = sonarrSeriesId,
        sonarrEpisodeId = sonarrEpisodeId,
    )
}

/** One row of a manual provider search (`GET /api/providers/movies|episodes`); extra fields are ignored. */
@Serializable
internal data class SubtitleCandidateDto(
    val provider: String = "",
    val subtitle: String = "",
    val score: Int = 0,
    val hearing_impaired: Boolean = false,
    val forced: Boolean = false,
    val original_format: Boolean = false,
    val language: SubtitleLanguageDto? = null,
) {
    fun toModel() = SubtitleCandidate(provider, subtitle, score, language?.name, hearing_impaired, forced, original_format)
}

@Serializable
internal data class SubtitleLanguageDto(
    val name: String = "",
)

/** Bazarr's "season x episode" code ("2x5") rendered as S02E05; unparseable values pass through. */
private fun String?.toEpisodeCode(): String? {
    val (season, episode) = this?.split('x', limit = 2)
        ?.mapNotNull { it.toIntOrNull() }
        ?: return null
    return "S%02dE%02d".format(season, episode)
}
