package com.pyoungws.bluewolfplayer.shared.seerr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The API client against mocked HTTP responses: parsing, cookies, request bodies and errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorSeerrDataSourceTest {
    private val sessionStore = InMemorySeerrSessionStore()
    private val sent = mutableListOf<HttpRequestData>()
    private val responses = mutableMapOf<String, Triple<String, HttpStatusCode, Headers>>()

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK, extra: Headers = Headers.Empty) =
        Triple(
            body,
            status,
            Headers.build {
                append(HttpHeaders.ContentType, "application/json")
                appendAll(extra)
            },
        )

    private fun TestScope.dataSource(): KtorSeerrDataSource {
        val engine = MockEngine { request ->
            sent += request
            val (body, status, headers) = responses[request.url.encodedPath]
                ?: return@MockEngine respondError(HttpStatusCode.NotFound)
            respond(body, status, headers)
        }
        return KtorSeerrDataSource(HttpClient(engine) { seerrDefaults(sessionStore) }, StandardTestDispatcher(testScheduler))
    }

    private fun HttpRequestData.bodyText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    @Test
    fun `sign in posts the jellyfin credentials and stores the session cookie`() = runTest {
        responses["/api/v1/auth/jellyfin"] = json(
            """{"id":7,"jellyfinUsername":"alice","permissions":0}""",
            extra = headersOf(HttpHeaders.SetCookie, "connect.sid=abc123; Path=/; HttpOnly"),
        )
        val ds = dataSource()

        val account = ds.signInWithJellyfin(SEERR_URL, "alice", "secret", "https://jellyfin.example.com")

        assertEquals(SeerrAccount(7, "alice", isAdmin = false), account)
        assertEquals("connect.sid=abc123", sessionStore.stored)
        val request = sent.single()
        assertEquals("/api/v1/auth/jellyfin", request.url.encodedPath)
        val body = request.bodyText()
        assertTrue("\"alice\"" in body && "\"secret\"" in body && "https://jellyfin.example.com" in body)
    }

    @Test
    fun `seerr servers using the sb sid cookie are stored and replayed too`() = runTest {
        responses["/api/v1/auth/jellyfin"] = json(
            """{"id":7,"jellyfinUsername":"alice","permissions":0}""",
            extra = headersOf(HttpHeaders.SetCookie, "sb.sid=xyz789; Path=/; HttpOnly"),
        )
        responses["/api/v1/auth/me"] = json("""{"id":7,"jellyfinUsername":"alice","permissions":0}""")
        val ds = dataSource()

        ds.signInWithJellyfin(SEERR_URL, "alice", "secret", "https://jellyfin.example.com")
        assertEquals("sb.sid=xyz789", sessionStore.stored)

        ds.currentUser(SEERR_URL)
        assertEquals("sb.sid=xyz789", sent.last().headers[HttpHeaders.Cookie])
    }

    @Test
    fun `a percent encoded cookie value is replayed verbatim, not double encoded`() = runTest {
        // Real express-session values look like `s%3Asid.signature`; re-encoding the `%`
        // breaks the signature and every authed call fails.
        sessionStore.stored = "sb.sid=s%3Aabc.signature"
        responses["/api/v1/auth/me"] = json("""{"id":7,"jellyfinUsername":"alice","permissions":2}""")
        val ds = dataSource()

        ds.currentUser(SEERR_URL)

        assertEquals("sb.sid=s%3Aabc.signature", sent.single().headers[HttpHeaders.Cookie])
    }

    @Test
    fun `the stored cookie is sent with later requests`() = runTest {
        sessionStore.stored = "connect.sid=abc123"
        responses["/api/v1/auth/me"] = json("""{"id":7,"jellyfinUsername":"alice","permissions":2}""")
        val ds = dataSource()

        val account = ds.currentUser(SEERR_URL)

        assertTrue(account.isAdmin)
        assertEquals("connect.sid=abc123", sent.single().headers[HttpHeaders.Cookie])
    }

    @Test
    fun `an expired cookie fails currentUser with 401`() = runTest {
        responses["/api/v1/auth/me"] = json("""{"message":"Unauthorized"}""", HttpStatusCode.Unauthorized)
        val ds = dataSource()

        val error = assertFailsWith<ClientRequestException> { ds.currentUser(SEERR_URL) }
        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
    }

    @Test
    fun `search maps movies and series and drops people`() = runTest {
        responses["/api/v1/search"] = json(
            """
            {"page":1,"totalPages":2,"totalResults":30,"results":[
              {"id":11,"mediaType":"movie","title":"Wolf","releaseDate":"2020-05-01","posterPath":"/p.jpg","mediaInfo":{"status":5}},
              {"id":22,"mediaType":"tv","name":"Wolves","firstAirDate":"2021-01-01","mediaInfo":{"status":2,"requests":[{"id":5,"status":1,"createdAt":"2024-01-01T00:00:00.000Z"}]}},
              {"id":33,"mediaType":"person","name":"Wolf Person"}
            ]}
            """.trimIndent(),
        )
        val ds = dataSource()

        val page = ds.search(SEERR_URL, "wolf")

        assertEquals("wolf", sent.single().url.parameters["query"])
        assertEquals(2, page.items.size)
        assertEquals(2, page.totalPages)
        val movie = page.items[0]
        assertEquals(SeerrMediaType.Movie, movie.type)
        assertEquals("Wolf", movie.title)
        assertEquals(2020, movie.year)
        assertEquals(SeerrMediaStatus.Available, movie.mediaStatus)
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", movie.posterUrl)
        val series = page.items[1]
        assertEquals(SeerrMediaType.Series, series.type)
        assertEquals(SeerrRequestStatus.Pending, series.requestStatus)
    }

    @Test
    fun `discover movies maps the page`() = runTest {
        responses["/api/v1/discover/movies"] = json(
            """{"page":1,"totalPages":1,"totalResults":1,"results":[{"id":9,"mediaType":"movie","title":"Moon","overview":"dark"}]}""",
        )
        val ds = dataSource()

        val page = ds.discoverMovies(SEERR_URL)

        assertEquals(listOf("Moon"), page.items.map { it.title })
        assertEquals(SeerrMediaStatus.NotRequested, page.items.single().mediaStatus)
    }

    @Test
    fun `series details include per-season availability`() = runTest {
        responses["/api/v1/tv/42"] = json(
            """
            {"id":42,"name":"Show","firstAirDate":"2019-03-04","seasons":[
              {"seasonNumber":0,"name":"Specials","episodeCount":1},
              {"seasonNumber":1,"name":"Season 1","episodeCount":8},
              {"seasonNumber":2,"name":"Season 2","episodeCount":8}
            ],"mediaInfo":{"status":4,"seasons":[
              {"seasonNumber":1,"status":5},
              {"seasonNumber":2,"status":3}
            ]}}
            """.trimIndent(),
        )
        val ds = dataSource()

        val details = ds.seriesDetails(SEERR_URL, 42)

        assertEquals(SeerrMediaStatus.PartiallyAvailable, details.title.mediaStatus)
        assertEquals(listOf(0, 1, 2), details.seasons.map { it.seasonNumber })
        assertEquals(SeerrMediaStatus.Available, details.seasons[1].status)
        assertEquals(SeerrMediaStatus.Processing, details.seasons[2].status)
        assertEquals(SeerrMediaStatus.NotRequested, details.seasons[0].status)
    }

    @Test
    fun `requesting a series posts the selected seasons`() = runTest {
        responses["/api/v1/request"] = json(
            """{"id":15,"status":1,"type":"tv","seasons":[{"seasonNumber":2},{"seasonNumber":3}],"media":{"tmdbId":42,"mediaType":"tv","status":2}}""",
            HttpStatusCode.Created,
        )
        val ds = dataSource()

        val request = ds.createRequest(SEERR_URL, SeerrMediaType.Series, 42, listOf(2, 3))

        val body = sent.single().bodyText()
        assertTrue("\"mediaType\":\"tv\"" in body)
        assertTrue("\"mediaId\":42" in body)
        assertTrue("\"seasons\":[2,3]" in body)
        assertEquals(15, request.id)
        assertEquals(SeerrRequestStatus.Pending, request.status)
        assertEquals(listOf(2, 3), request.seasons)
    }

    @Test
    fun `requesting a movie sends no seasons`() = runTest {
        responses["/api/v1/request"] = json(
            """{"id":16,"status":2,"type":"movie","media":{"tmdbId":55,"mediaType":"movie","status":3}}""",
            HttpStatusCode.Created,
        )
        val ds = dataSource()

        val request = ds.createRequest(SEERR_URL, SeerrMediaType.Movie, 55, null)

        val body = sent.single().bodyText()
        assertTrue("\"mediaType\":\"movie\"" in body)
        assertTrue("seasons" !in body)
        assertEquals(SeerrRequestStatus.Approved, request.status)
        assertEquals(SeerrMediaStatus.Processing, request.mediaStatus)
    }

    @Test
    fun `the request list maps statuses`() = runTest {
        responses["/api/v1/request"] = json(
            """
            {"pageInfo":{"page":1,"pages":1,"pageSize":20,"results":3},"results":[
              {"id":1,"status":1,"media":{"tmdbId":10,"mediaType":"movie","status":2}},
              {"id":2,"status":2,"media":{"tmdbId":20,"mediaType":"tv","status":5},"seasons":[{"seasonNumber":1}]},
              {"id":3,"status":3,"media":{"tmdbId":30,"mediaType":"movie","status":1}}
            ]}
            """.trimIndent(),
        )
        val ds = dataSource()

        val page = ds.requests(SEERR_URL)

        assertEquals(3, page.totalResults)
        assertEquals(SeerrRequestStatus.Pending, page.items[0].status)
        assertEquals(SeerrRequestStatus.Approved, page.items[1].status)
        assertEquals(SeerrMediaType.Series, page.items[1].type)
        assertEquals(SeerrMediaStatus.Available, page.items[1].mediaStatus)
        assertEquals(SeerrRequestStatus.Declined, page.items[2].status)
    }

    @Test
    fun `the request list sends the pending filter`() = runTest {
        responses["/api/v1/request"] = json(
            """{"pageInfo":{"page":1,"pages":1,"pageSize":20,"results":1},
               "results":[{"id":7,"status":1,"media":{"tmdbId":10,"mediaType":"movie","status":2}}]}""",
        )
        val ds = dataSource()

        val page = ds.requests(SEERR_URL, SeerrRequestFilter.Pending)

        assertEquals("pending", sent.single().url.parameters["filter"])
        assertEquals(7, page.items.single().id)
    }

    @Test
    fun `request details map the requester and timestamps`() = runTest {
        responses["/api/v1/request/9"] = json(
            """{"id":9,"status":1,"type":"tv","is4k":true,
               "seasons":[{"seasonNumber":1},{"seasonNumber":2}],
               "media":{"tmdbId":42,"mediaType":"tv","status":2},
               "requestedBy":{"id":5,"jellyfinUsername":"bob"},
               "createdAt":"2026-01-01T10:00:00.000Z","updatedAt":"2026-01-02T10:00:00.000Z"}""",
        )
        val ds = dataSource()

        val request = ds.requestDetails(SEERR_URL, 9)

        assertEquals("/api/v1/request/9", sent.single().url.encodedPath)
        assertEquals(SeerrMediaType.Series, request.type)
        assertEquals(5, request.requestedById)
        assertEquals("bob", request.requestedByName)
        assertTrue(request.is4k)
        assertEquals(listOf(1, 2), request.seasons)
        assertEquals("2026-01-02T10:00:00.000Z", request.updatedAt)
    }

    @Test
    fun `approving posts to the approve endpoint`() = runTest {
        responses["/api/v1/request/9/approve"] = json(
            """{"id":9,"status":2,"media":{"tmdbId":42,"mediaType":"movie","status":3}}""",
        )
        val ds = dataSource()

        val request = ds.approveRequest(SEERR_URL, 9)

        val sent = sent.single()
        assertEquals("/api/v1/request/9/approve", sent.url.encodedPath)
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals(SeerrRequestStatus.Approved, request.status)
        assertEquals(SeerrMediaStatus.Processing, request.mediaStatus)
    }

    @Test
    fun `declining posts to the decline endpoint`() = runTest {
        responses["/api/v1/request/9/decline"] = json(
            """{"id":9,"status":3,"media":{"tmdbId":42,"mediaType":"movie","status":2}}""",
        )
        val ds = dataSource()

        val request = ds.declineRequest(SEERR_URL, 9)

        assertEquals("/api/v1/request/9/decline", sent.single().url.encodedPath)
        assertEquals(SeerrRequestStatus.Declined, request.status)
    }

    @Test
    fun `normalizeSeerrUrl accepts common input shapes`() {
        assertEquals("https://seerr.example.com", normalizeSeerrUrl("seerr.example.com"))
        assertEquals("https://seerr.example.com", normalizeSeerrUrl(" https://seerr.example.com/ "))
        assertEquals("http://192.168.1.10:5055", normalizeSeerrUrl("http://192.168.1.10:5055/"))
        assertEquals("https://seerr.example.com/base", normalizeSeerrUrl("seerr.example.com/base"))
        assertEquals("https://localhost:5055", normalizeSeerrUrl("localhost:5055"))
        assertNull(normalizeSeerrUrl(""))
        assertNull(normalizeSeerrUrl("not a url"))
        assertNull(normalizeSeerrUrl("ftp://seerr.example.com"))
    }
}
