package com.pyoungws.bluewolfplayer.shared.prowlarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrServiceSettings
import com.pyoungws.bluewolfplayer.shared.arrs.arrDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Prowlarr's /api/v1 client against mocked HTTP responses: header auth, paths and DTO parsing. */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorProwlarrDataSourceTest {
    private val settings = ArrServiceSettings("https://prowlarr.example.com", "secret-key")
    private val sent = mutableListOf<HttpRequestData>()
    private var responseBody = "[]"
    private var responseStatus = HttpStatusCode.OK

    private fun TestScope.dataSource(): KtorProwlarrDataSource {
        val engine = MockEngine { request ->
            sent += request
            respond(responseBody, responseStatus, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return KtorProwlarrDataSource(HttpClient(engine) { arrDefaults() }, StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `indexers hits the v1 indexer endpoint with the api key`() = runTest {
        val indexers = dataSource().indexers(settings)

        val request = sent.single()
        assertEquals("/api/v1/indexer", request.url.encodedPath)
        assertEquals("secret-key", request.headers["X-Api-Key"])
        assertEquals(emptyList(), indexers)
    }

    @Test
    fun `indexers map id name protocol and enable flag`() = runTest {
        responseBody = """[
            {"id":1,"name":"Index One","protocol":"torrent","enable":true},
            {"id":2,"name":"Index Two","protocol":"usenet","enable":false}
        ]"""

        val indexers = dataSource().indexers(settings)

        assertEquals(2, indexers.size)
        assertEquals(ProwlarrIndexer(1, "Index One", "torrent", true), indexers[0])
        assertEquals(false, indexers[1].enabled)
    }

    @Test
    fun `indexer stats map to a per-indexer lookup`() = runTest {
        responseBody = """
            {"indexers":[
              {"indexerId":1,"averageResponseTime":150,"numberOfQueries":40,
               "numberOfGrabs":5,"numberOfFailedQueries":1,"numberOfFailedGrabs":0}
            ]}
        """.trimIndent()

        val stats = dataSource().indexerStats(settings)

        assertEquals("/api/v1/indexerstats", sent.single().url.encodedPath)
        assertEquals(IndexerStats(queries = 40, grabs = 5, failedQueries = 1, failedGrabs = 0, averageResponseMs = 150), stats[1])
    }

    @Test
    fun `disabled indexers map to an id-to-expiry lookup`() = runTest {
        responseBody = """[{"indexerId":2,"disabledTill":"2026-11-12T00:00:00Z"}]"""

        val disabled = dataSource().disabledIndexers(settings)

        assertEquals("/api/v1/indexerstatus", sent.single().url.encodedPath)
        assertEquals(mapOf(2 to "2026-11-12T00:00:00Z"), disabled)
    }

    @Test
    fun `health issues map source type and message`() = runTest {
        responseBody = """[{"source":"IndexerStatusCheck","type":"warning","message":"Indexers unavailable"}]"""

        val issues = dataSource().health(settings)

        assertEquals("/api/v1/health", sent.single().url.encodedPath)
        assertEquals(ProwlarrHealthIssue("IndexerStatusCheck", "warning", "Indexers unavailable"), issues.single())
    }

    @Test
    fun `a rejected api key surfaces as an http error`() = runTest {
        responseStatus = HttpStatusCode.Unauthorized

        assertFailsWith<ClientRequestException> { dataSource().indexers(settings) }
    }

    @Test
    fun `testIndexer posts the resource and returns validation messages`() = runTest {
        responseBody = """{"validationErrors":[{"propertyName":"BaseUrl","errorMessage":"Unable to connect"}]}"""
        val resource = JsonObject(mapOf("id" to JsonPrimitive(1), "name" to JsonPrimitive("Index One")))

        val problems = dataSource().testIndexer(settings, resource)

        val request = sent.single()
        assertEquals("/api/v1/indexer/test", request.url.encodedPath)
        assertEquals("true", request.url.parameters["forceTest"])
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(listOf("Unable to connect"), problems)
    }

    @Test
    fun `testIndexer reads validation errors from a 400 body`() = runTest {
        responseBody = """[{"propertyName":"BaseSettings","errorMessage":"Indexer is disabled"}]"""
        responseStatus = HttpStatusCode.BadRequest

        val problems = dataSource().testIndexer(settings, JsonObject(emptyMap()))

        assertEquals(listOf("Indexer is disabled"), problems)
    }

    @Test
    fun `testAllIndexers posts to the testall endpoint`() = runTest {
        dataSource().testAllIndexers(settings)

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/indexer/testall", request.url.encodedPath)
    }
}
