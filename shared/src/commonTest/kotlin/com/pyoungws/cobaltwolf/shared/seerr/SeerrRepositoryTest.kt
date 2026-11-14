package com.pyoungws.cobaltwolf.shared.seerr

import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore
import com.pyoungws.cobaltwolf.shared.auth.SERVER
import com.pyoungws.cobaltwolf.shared.auth.SignInError
import com.pyoungws.cobaltwolf.shared.library.SESSION
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrRepositoryTest {
    private val dataSource = FakeSeerrDataSource()

    private suspend fun signedIn() = AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }

    private suspend fun signedInWithPassword(password: String = "hunter2") =
        AuthRepository(FakeAuthDataSource(), InMemorySessionStore()).apply { signIn(SERVER, "alice", password) }

    private fun TestScope.repository(
        authRepository: AuthRepository,
        settings: SeerrSettings = SeerrSettings(SEERR_URL),
        cookie: String? = "sid",
        source: SeerrDataSource = dataSource,
    ) = seerrRepository(source, authRepository, settings, cookie, TestScope(testScheduler))

    @Test
    fun `a stored cookie restores the session`() = runTest {
        val repository = repository(signedIn())
        advanceUntilIdle()

        assertEquals(SeerrAuthState.SignedIn(SEERR_ACCOUNT), repository.authState.value)
        assertEquals(listOf("me"), dataSource.calls)
    }

    @Test
    fun `no cookie means signed out`() = runTest {
        val repository = repository(signedIn(), cookie = null)
        advanceUntilIdle()

        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun `no server url means not configured`() = runTest {
        val repository = repository(signedIn(), settings = SeerrSettings(), cookie = null)
        advanceUntilIdle()

        assertEquals(SeerrAuthState.NotConfigured, repository.authState.value)
    }

    @Test
    fun `an unreachable server keeps the cookie for the next attempt`() = runTest {
        dataSource.currentUser = { throw IOException("no route") }
        val sessionStore = InMemorySeerrSessionStore("sid")
        val repository = SeerrRepository(
            dataSource,
            SeerrSettingsRepository(InMemorySeerrSettingsStore(SeerrSettings(SEERR_URL))),
            sessionStore,
            signedIn(),
            TestScope(testScheduler),
        )
        advanceUntilIdle()

        val state = repository.authState.value
        assertIs<SeerrAuthState.SignedOut>(state)
        assertEquals(LoadError.ServerUnreachable, state.error)
        assertEquals("sid", sessionStore.stored)
    }

    @Test
    fun `sign in uses the jellyfin username and server address`() = runTest {
        val repository = repository(signedIn())
        advanceUntilIdle()

        val result = repository.signInWithJellyfin("secret")

        assertEquals(SeerrSignInResult.Success(SEERR_ACCOUNT), result)
        assertEquals(Triple("alice", "secret", SERVER), dataSource.signInArgs)
        assertEquals(SeerrAuthState.SignedIn(SEERR_ACCOUNT), repository.authState.value)
    }

    @Test
    fun `a failed sign-in reports the error without signing in`() = runTest {
        dataSource.signIn = { _, _, _ -> throw IOException("no route") }
        val repository = repository(signedIn(), cookie = null)
        advanceUntilIdle()

        val result = repository.signInWithJellyfin("bad")

        assertEquals(SeerrSignInResult.Failure(SignInError.ServerUnreachable), result)
        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun `a fresh jellyfin sign-in signs in to seerr automatically`() = runTest {
        val repository = repository(signedInWithPassword(), cookie = null)
        advanceUntilIdle()

        assertEquals(Triple("alice", "hunter2", SERVER), dataSource.signInArgs)
        assertEquals(SeerrAuthState.SignedIn(SEERR_ACCOUNT), repository.authState.value)
    }

    @Test
    fun `a restored session does not auto sign in to seerr`() = runTest {
        val repository = repository(signedIn(), cookie = null)
        advanceUntilIdle()

        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
        assertTrue(dataSource.calls.none { it.startsWith("signIn") })
    }

    @Test
    fun `a failed auto sign-in falls back to manual sign-in without retrying`() = runTest {
        dataSource.signIn = { _, _, _ -> throw IOException("no route") }
        val repository = repository(signedInWithPassword(), cookie = null)
        advanceUntilIdle()

        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
        assertEquals(1, dataSource.calls.count { it.startsWith("signIn") })

        dataSource.signIn = { _, _, _ -> SEERR_ACCOUNT }
        val result = repository.signInWithJellyfin("secret")

        assertIs<SeerrSignInResult.Success>(result)
        assertEquals(SeerrAuthState.SignedIn(SEERR_ACCOUNT), repository.authState.value)
    }

    @Test
    fun `configuring the seerr url after sign-in signs in automatically`() = runTest {
        val settings = SeerrSettingsRepository(InMemorySeerrSettingsStore(SeerrSettings()))
        val repository = SeerrRepository(
            dataSource,
            settings,
            InMemorySeerrSessionStore(),
            signedInWithPassword(),
            TestScope(testScheduler),
        )
        advanceUntilIdle()
        assertEquals(SeerrAuthState.NotConfigured, repository.authState.value)

        settings.update { it.copy(serverUrl = SEERR_URL) }
        advanceUntilIdle()

        assertEquals(SeerrAuthState.SignedIn(SEERR_ACCOUNT), repository.authState.value)
    }

    @Test
    fun `a 401 from the api clears the cookie and signs out`() = runTest {
        val sessionStore = InMemorySeerrSessionStore("sid")
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) }) { seerrDefaults(sessionStore) }
        val httpSource = KtorSeerrDataSource(client, StandardTestDispatcher(testScheduler))
        val repository = seerrRepository(
            httpSource,
            signedIn(),
            SeerrSettings(SEERR_URL),
            scope = TestScope(testScheduler),
            sessionStore = sessionStore,
        )
        advanceUntilIdle()

        val result = repository.myRequests()

        assertEquals(Outcome.Failure(LoadError.SessionExpired), result)
        assertNull(sessionStore.stored)
        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun `discover loads all sections`() = runTest {
        dataSource.trending = { pageOf(listOf(seerrMovie(1))) }
        dataSource.discoverMovies = { pageOf(listOf(seerrMovie(2))) }
        dataSource.discoverSeries = { pageOf(listOf(seerrSeries(3))) }
        val repository = repository(signedIn())
        advanceUntilIdle()

        val feed = (repository.discover() as Outcome.Success).value

        assertEquals(listOf(1), feed.trending.map { it.tmdbId })
        assertEquals(listOf(2), feed.movies.map { it.tmdbId })
        assertEquals(listOf(3), feed.series.map { it.tmdbId })
    }

    @Test
    fun `requesting a series passes the selected seasons`() = runTest {
        val repository = repository(signedIn())
        advanceUntilIdle()

        repository.request(SeerrMediaType.Series, 42, listOf(1, 3))

        assertEquals(Triple(SeerrMediaType.Series, 42, listOf(1, 3)), dataSource.requestArgs)
    }

    @Test
    fun `requesting a series without seasons fails early`() = runTest {
        val repository = repository(signedIn())
        advanceUntilIdle()

        val result = repository.request(SeerrMediaType.Series, 42, emptyList())

        assertIs<Outcome.Failure>(result)
        assertTrue(dataSource.requestArgs == null)
    }

    @Test
    fun `my requests are enriched with titles and posters`() = runTest {
        dataSource.requests = {
            pageOf(
                listOf(
                    seerrRequest(1, tmdbId = 10, type = SeerrMediaType.Movie),
                    seerrRequest(2, tmdbId = 20, type = SeerrMediaType.Series, seasons = listOf(1)),
                ),
            )
        }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, "The Movie").copy(posterUrl = "poster.jpg")) }
        dataSource.seriesDetails = { SeerrTitleDetails(seerrSeries(it, "The Series")) }
        val repository = repository(signedIn())
        advanceUntilIdle()

        val requests = (repository.myRequests() as Outcome.Success).value

        assertEquals("The Movie", requests[0].title)
        assertEquals("poster.jpg", requests[0].posterUrl)
        assertEquals("The Series", requests[1].title)
        assertTrue("movie(10)" in dataSource.calls && "series(20)" in dataSource.calls)
    }

    @Test
    fun `pending requests uses the pending filter, keeps pending only and enriches`() = runTest {
        dataSource.requests = { filter ->
            if (filter == SeerrRequestFilter.Pending) {
                pageOf(
                    listOf(
                        seerrRequest(1, tmdbId = 10, status = SeerrRequestStatus.Pending, requestedById = 8, requestedByName = "bob"),
                        seerrRequest(2, tmdbId = 20, status = SeerrRequestStatus.Approved),
                    ),
                )
            } else {
                pageOf(emptyList())
            }
        }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, "The Movie")) }
        val repository = repository(signedIn())
        advanceUntilIdle()

        val requests = (repository.pendingRequests() as Outcome.Success).value

        assertTrue("requests(Pending)" in dataSource.calls)
        assertEquals(listOf(1), requests.map { it.id })
        assertEquals("The Movie", requests.single().title)
        assertEquals("bob", requests.single().requestedByName)
        assertEquals(8, requests.single().requestedById)
    }

    @Test
    fun `request details are enriched with the title`() = runTest {
        dataSource.requestDetails = { seerrRequest(it, tmdbId = 10, status = SeerrRequestStatus.Pending) }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, "The Movie").copy(posterUrl = "p.jpg")) }
        val repository = repository(signedIn())
        advanceUntilIdle()

        val details = (repository.requestDetails(5) as Outcome.Success).value

        assertEquals(5, details.id)
        assertEquals("The Movie", details.title)
        assertEquals("p.jpg", details.posterUrl)
    }

    @Test
    fun `approve and decline hit the right endpoints`() = runTest {
        val repository = repository(signedIn())
        advanceUntilIdle()

        val approved = repository.approveRequest(9)
        val declined = repository.declineRequest(10)

        assertEquals(SeerrRequestStatus.Approved, (approved as Outcome.Success).value.status)
        assertEquals(SeerrRequestStatus.Declined, (declined as Outcome.Success).value.status)
        assertTrue("approve(9)" in dataSource.calls && "decline(10)" in dataSource.calls)
    }

    @Test
    fun `a 403 from the api reports forbidden without signing out`() = runTest {
        val sessionStore = InMemorySeerrSessionStore("sid")
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.Forbidden) }) { seerrDefaults(sessionStore) }
        val httpSource = KtorSeerrDataSource(client, StandardTestDispatcher(testScheduler))
        val repository = seerrRepository(
            httpSource,
            signedIn(),
            SeerrSettings(SEERR_URL),
            scope = TestScope(testScheduler),
            sessionStore = sessionStore,
        )
        advanceUntilIdle()

        val result = repository.pendingRequests()

        assertEquals(Outcome.Failure(LoadError.Forbidden), result)
        assertEquals("sid", sessionStore.stored)
    }

    @Test
    fun `signing out of jellyfin clears the seerr session`() = runTest {
        val sessionStore = InMemorySeerrSessionStore("sid")
        val authRepository = signedIn()
        val repository = SeerrRepository(
            dataSource,
            SeerrSettingsRepository(InMemorySeerrSettingsStore(SeerrSettings(SEERR_URL))),
            sessionStore,
            authRepository,
            TestScope(testScheduler),
        )
        advanceUntilIdle()
        assertIs<SeerrAuthState.SignedIn>(repository.authState.value)

        authRepository.signOut()
        advanceUntilIdle()

        assertNull(sessionStore.stored)
        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun `changing the server url clears the seerr session`() = runTest {
        val sessionStore = InMemorySeerrSessionStore("sid")
        val settings = SeerrSettingsRepository(InMemorySeerrSettingsStore(SeerrSettings(SEERR_URL)))
        val repository = SeerrRepository(dataSource, settings, sessionStore, signedIn(), TestScope(testScheduler))
        advanceUntilIdle()
        assertIs<SeerrAuthState.SignedIn>(repository.authState.value)

        settings.update { it.copy(serverUrl = "https://other.example.com") }
        advanceUntilIdle()

        assertNull(sessionStore.stored)
        assertIs<SeerrAuthState.SignedOut>(repository.authState.value)
    }
}
