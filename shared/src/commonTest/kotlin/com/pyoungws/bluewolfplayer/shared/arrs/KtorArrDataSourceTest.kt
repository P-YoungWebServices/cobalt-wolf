package com.pyoungws.bluewolfplayer.shared.arrs

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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The API client against mocked HTTP responses: auth header, query params and DTO parsing. */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorArrDataSourceTest {
    private val settings = ArrServiceSettings("https://arr.example.com", "secret-key")
    private val sent = mutableListOf<HttpRequestData>()
    private var responseBody = "[]"
    private var responseStatus = HttpStatusCode.OK

    private fun TestScope.dataSource(): KtorArrDataSource {
        val engine = MockEngine { request ->
            sent += request
            if (responseStatus != HttpStatusCode.OK) return@MockEngine respondError(responseStatus)
            respond(responseBody, responseStatus, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return KtorArrDataSource(HttpClient(engine) { arrDefaults() }, StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `the calendar request sends the api key and the date window`() = runTest {
        dataSource().calendar(ArrService.Radarr, settings, LocalDate(2026, 11, 9), LocalDate(2026, 12, 10))

        val request = sent.single()
        assertEquals("/api/v3/calendar", request.url.encodedPath)
        assertEquals("secret-key", request.headers["X-Api-Key"])
        assertEquals("2026-11-09", request.url.parameters["start"])
        assertEquals("2026-12-10", request.url.parameters["end"])
    }

    @Test
    fun `radarr entries map to upcoming items with the best release date`() = runTest {
        responseBody = """
            [
              {"title":"Future Film","year":2027,"inCinemas":"2026-11-11","digitalRelease":"2026-12-01",
               "hasFile":false,"monitored":true,"images":[{"coverType":"poster","url":"/MediaCover/7/poster.jpg"}]},
              {"title":"Undated","year":2027}
            ]
        """.trimIndent()

        val items = dataSource().calendar(ArrService.Radarr, settings, LocalDate(2026, 11, 9), LocalDate(2026, 12, 10))

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(ArrService.Radarr, item.service)
        assertEquals("Future Film", item.title)
        assertEquals("2027", item.subtitle)
        assertEquals(LocalDate(2026, 12, 1), item.date)
        assertEquals("https://arr.example.com/MediaCover/7/poster.jpg", item.posterUrl)
        assertEquals(true, item.monitored)
    }

    @Test
    fun `sonarr entries include the series and an episode code`() = runTest {
        responseBody = """
            [
              {"title":"The Big One","seasonNumber":2,"episodeNumber":5,"airDate":"2026-11-12",
               "hasFile":false,"monitored":true,
               "series":{"title":"Show Name","images":[{"coverType":"poster","remoteUrl":"https://art.example.com/p.jpg"}]}}
            ]
        """.trimIndent()

        val items = dataSource().calendar(ArrService.Sonarr, settings, LocalDate(2026, 11, 9), LocalDate(2026, 12, 10))

        val item = items.single()
        assertEquals("Show Name", item.title)
        assertEquals("S02E05 · The Big One", item.subtitle)
        assertEquals(LocalDate(2026, 11, 12), item.date)
        assertEquals("https://art.example.com/p.jpg", item.posterUrl)
        assertEquals("true", sent.single().url.parameters["includeSeries"])
    }

    @Test
    fun `a rejected api key surfaces as an http error`() = runTest {
        responseStatus = HttpStatusCode.Unauthorized

        assertFailsWith<ClientRequestException> {
            dataSource().calendar(ArrService.Sonarr, settings, LocalDate(2026, 11, 9), LocalDate(2026, 12, 10))
        }
    }

    @Test
    fun `status probes the service status endpoint`() = runTest {
        responseBody = """{"version":"5.0.0"}"""

        dataSource().status(ArrService.Radarr, settings)

        val request = sent.single()
        assertEquals("/api/v3/system/status", request.url.encodedPath)
        assertEquals("secret-key", request.headers["X-Api-Key"])
    }

    @Test
    fun `entries without a usable date are dropped`() = runTest {
        responseBody = """[{"title":"Show","seasonNumber":1,"episodeNumber":1,"series":{"title":"Show"}}]"""

        val items = dataSource().calendar(ArrService.Sonarr, settings, LocalDate(2026, 11, 9), LocalDate(2026, 12, 10))

        assertEquals(emptyList(), items)
    }
}
