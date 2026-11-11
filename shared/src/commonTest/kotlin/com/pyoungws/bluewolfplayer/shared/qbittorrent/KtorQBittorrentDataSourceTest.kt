package com.pyoungws.bluewolfplayer.shared.qbittorrent

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The API client against mocked HTTP responses: login form, SID cookie, DTO parsing, errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorQBittorrentDataSourceTest {
    private val sessionStore = InMemoryQBittorrentSessionStore()
    private val settings = QBittorrentSettings("https://qbit.example.com", "admin", "hunter2")
    private val sent = mutableListOf<HttpRequestData>()
    private var responses = mutableMapOf<String, Triple<String, HttpStatusCode, io.ktor.http.Headers>>()

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK, extra: io.ktor.http.Headers = io.ktor.http.Headers.Empty) =
        Triple(body, status, headersOf(HttpHeaders.ContentType, "application/json").let { base ->
            io.ktor.http.Headers.build { appendAll(base); appendAll(extra) }
        })

    private fun TestScope.dataSource(): KtorQBittorrentDataSource {
        val engine = MockEngine { request ->
            sent += request
            val (body, status, headers) = responses[request.url.encodedPath]
                ?: return@MockEngine respondError(HttpStatusCode.NotFound)
            respond(body, status, headers)
        }
        return KtorQBittorrentDataSource(
            HttpClient(engine) { qbittorrentDefaults(sessionStore) },
            StandardTestDispatcher(testScheduler),
        )
    }

    private fun HttpRequestData.bodyText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    @Test
    fun `login posts the credentials as a form and stores the SID cookie`() = runTest {
        responses["/api/v2/auth/login"] = json(
            "Ok.",
            extra = headersOf(HttpHeaders.SetCookie, "SID=abc123; path=/"),
        )

        assertTrue(dataSource().login(settings))

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue("username=admin" in request.bodyText())
        assertEquals("SID=abc123", sessionStore.load())
    }

    @Test
    fun `login returns false when the server answers Fails`() = runTest {
        responses["/api/v2/auth/login"] = json("Fails.")

        assertFalse(dataSource().login(settings))
        assertNull(sessionStore.load())
    }

    @Test
    fun `login succeeds on an empty 204 response`() = runTest {
        responses["/api/v2/auth/login"] = Triple(
            "",
            HttpStatusCode.NoContent,
            headersOf(HttpHeaders.SetCookie, "SID=xyz789; path=/"),
        )

        assertTrue(dataSource().login(settings))
        assertEquals("SID=xyz789", sessionStore.load())
    }

    @Test
    fun `login stores the per-port QBT_SID cookie used by newer servers`() = runTest {
        responses["/api/v2/auth/login"] = Triple(
            "",
            HttpStatusCode.NoContent,
            headersOf(HttpHeaders.SetCookie, "QBT_SID_8080=port789; path=/"),
        )

        assertTrue(dataSource().login(settings))
        assertEquals("QBT_SID_8080=port789", sessionStore.load())
    }

    @Test
    fun `torrents parses the list and maps the eta`() = runTest {
        responses["/api/v2/torrents/info"] = json(
            """[
              {"hash":"h1","name":"Movie","state":"downloading","progress":0.42,"size":4000000000,
               "dlspeed":1500000,"upspeed":20000,"eta":2500,"category":"movies"},
              {"hash":"h2","name":"Show","state":"stalledUP","progress":1,"size":8000000,"eta":8640000}
            ]""",
        )

        val torrents = dataSource().torrents(settings)

        assertEquals(2, torrents.size)
        val first = torrents[0]
        assertEquals("Movie", first.name)
        assertEquals(0.42f, first.progressFraction)
        assertEquals(1_500_000, first.downloadSpeedBps)
        assertEquals(2500, first.etaSeconds)
        assertEquals("movies", first.category)
        // Infinite ETAs map to null.
        assertNull(torrents[1].etaSeconds)
    }

    @Test
    fun `transferInfo maps the global speeds`() = runTest {
        responses["/api/v2/transfer/info"] = json("""{"dl_info_speed":5000000,"up_info_speed":500000}""")

        val speeds = dataSource().transferInfo(settings)

        assertEquals(5_000_000, speeds.downloadBps)
        assertEquals(500_000, speeds.uploadBps)
    }

    @Test
    fun `a 403 surfaces as a session expired error`() = runTest {
        responses["/api/v2/torrents/info"] = Triple("Forbidden", HttpStatusCode.Forbidden, io.ktor.http.Headers.Empty)

        assertFailsWith<SessionExpiredException> { dataSource().torrents(settings) }
    }
}
