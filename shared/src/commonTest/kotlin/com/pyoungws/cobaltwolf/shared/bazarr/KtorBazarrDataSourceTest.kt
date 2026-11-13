package com.pyoungws.cobaltwolf.shared.bazarr

import com.pyoungws.cobaltwolf.shared.arrs.ArrServiceSettings
import com.pyoungws.cobaltwolf.shared.arrs.arrDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Bazarr's API client against mocked HTTP responses: header auth, paths and DTO parsing. */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorBazarrDataSourceTest {
    private val settings = ArrServiceSettings("https://bazarr.example.com", "secret-key")
    private val sent = mutableListOf<HttpRequestData>()
    private var responseBody = """{"data":[],"total":0}"""
    private var responseStatus = HttpStatusCode.OK

    /** Per-path overrides for tests exercising more than one endpoint. */
    private val responses = mutableMapOf<String, String>()

    private fun TestScope.dataSource(): KtorBazarrDataSource {
        val engine = MockEngine { request ->
            sent += request
            if (responseStatus != HttpStatusCode.OK) return@MockEngine respondError(responseStatus)
            respond(responses[request.url.encodedPath] ?: responseBody, responseStatus, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return KtorBazarrDataSource(HttpClient(engine) { arrDefaults() }, StandardTestDispatcher(testScheduler))
    }

    private fun HttpRequestData.formField(name: String): String? =
        (body as? io.ktor.http.content.OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            ?.split('&')?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    @Test
    fun `wanted movies hits the movies endpoint with the api key`() = runTest {
        val movies = dataSource().wantedMovies(settings)

        val request = sent.single()
        assertEquals("/api/movies/wanted", request.url.encodedPath)
        assertEquals("secret-key", request.headers["X-Api-Key"])
        assertEquals(emptyList(), movies)
    }

    @Test
    fun `wanted movies map the title and missing subtitles`() = runTest {
        responseBody = """
            {"data":[
              {"title":"Future Film","missing_subtitles":[
                {"name":"English","code2":"en","code3":"eng","forced":false,"hi":false},
                {"name":"French","code2":"fr","code3":"fra","forced":true,"hi":true}
              ]}
            ],"total":1}
        """.trimIndent()

        val movies = dataSource().wantedMovies(settings)

        val movie = movies.single()
        assertEquals("Future Film", movie.title)
        assertEquals(listOf("English", "French (forced, HI)"), movie.missing.map { it.label })
    }

    @Test
    fun `wanted episodes map the series and an episode code`() = runTest {
        responseBody = """
            {"data":[
              {"seriesTitle":"Show Name","episode_number":"2x5","episodeTitle":"The Big One",
               "sonarrSeriesId":3,"sonarrEpisodeId":44,"sceneName":"Show.S02E05.1080p",
               "missing_subtitles":[{"name":"English","forced":false,"hi":false}]}
            ],"total":1}
        """.trimIndent()

        val episode = dataSource().wantedEpisodes(settings).single()

        assertEquals("Show Name", episode.seriesTitle)
        assertEquals("S02E05", episode.episodeCode)
        assertEquals("The Big One", episode.episodeTitle)
        assertEquals("English", episode.missing.single().label)
    }

    @Test
    fun `an unparseable episode number leaves the code null`() = runTest {
        responseBody = """{"data":[{"seriesTitle":"Show","episode_number":null,"missing_subtitles":[]}],"total":1}"""

        val episode = dataSource().wantedEpisodes(settings).single()

        assertNull(episode.episodeCode)
        assertNull(episode.episodeTitle)
    }

    @Test
    fun `a rejected api key surfaces as an http error`() = runTest {
        responseStatus = HttpStatusCode.Unauthorized

        assertFailsWith<ClientRequestException> { dataSource().wantedMovies(settings) }
    }

    @Test
    fun `movie search passes the radarr id and maps scored candidates`() = runTest {
        responses["/api/providers/movies"] = """[
            {"provider":"opensubtitles","subtitle":"dl-link-1","score":74,
             "language":{"code2":"en","name":"English"},"hearing_impaired":false,"forced":false,
             "release_info":["Film.2024.1080p-GRP"]},
            {"provider":"addic7ed","subtitle":"dl-link-2","score":40,
             "language":{"name":"English"},"hearing_impaired":true,"forced":false}
        ]"""

        val candidates = dataSource().searchMovieSubtitles(settings, radarrId = 42)

        val request = sent.single()
        assertEquals("/api/providers/movies", request.url.encodedPath)
        assertEquals("42", request.url.parameters["radarrid"])
        assertEquals("secret-key", request.headers["X-Api-Key"])
        assertEquals(2, candidates.size)
        assertEquals(SubtitleCandidate("opensubtitles", "dl-link-1", 74, "English"), candidates[0])
        assertEquals(true, candidates[1].hearingImpaired)
    }

    @Test
    fun `episode search passes the episode id`() = runTest {
        responses["/api/providers/episodes"] = "[]"

        dataSource().searchEpisodeSubtitles(settings, sonarrEpisodeId = 99)

        assertEquals("99", sent.single().url.parameters["episodeid"])
    }

    @Test
    fun `movie download posts the candidate fields as a form`() = runTest {
        responses["/api/providers/movies"] = """{"result":"ok"}"""
        val candidate = SubtitleCandidate("opensubtitles", "dl-link-1", 74, "English", hearingImpaired = true)

        dataSource().downloadMovieSubtitle(settings, radarrId = 42, candidate)

        val request = sent.single()
        assertEquals("/api/providers/movies", request.url.encodedPath)
        assertEquals("42", request.formField("radarrid"))
        assertEquals("opensubtitles", request.formField("provider"))
        assertEquals("dl-link-1", request.formField("subtitle"))
        assertEquals("True", request.formField("hi"))
        assertEquals("False", request.formField("forced"))
    }

    @Test
    fun `episode download posts both sonarr ids`() = runTest {
        responses["/api/providers/episodes"] = """{"result":"ok"}"""
        val candidate = SubtitleCandidate("opensubtitles", "dl-link-1", 74, "English")

        dataSource().downloadEpisodeSubtitle(settings, sonarrSeriesId = 3, sonarrEpisodeId = 44, candidate)

        val request = sent.single()
        assertEquals("3", request.formField("seriesid"))
        assertEquals("44", request.formField("episodeid"))
    }
}
