package com.pyoungws.cobaltwolf.shared.presentation

import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore
import com.pyoungws.cobaltwolf.shared.auth.SignInError
import com.pyoungws.cobaltwolf.shared.library.SESSION
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.seerr.FakeSeerrDataSource
import com.pyoungws.cobaltwolf.shared.seerr.InMemorySeerrSessionStore
import com.pyoungws.cobaltwolf.shared.seerr.InMemorySeerrSettingsStore
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaStatus
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaType
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRequestStatus
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSeason
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettings
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrTitleDetails
import com.pyoungws.cobaltwolf.shared.seerr.SEERR_URL
import com.pyoungws.cobaltwolf.shared.seerr.pageOf
import com.pyoungws.cobaltwolf.shared.seerr.seerrMovie
import com.pyoungws.cobaltwolf.shared.seerr.seerrRequest
import com.pyoungws.cobaltwolf.shared.seerr.seerrSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrViewModelsTest {
    private val dataSource = FakeSeerrDataSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun signedIn() = AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }

    private suspend fun TestScope.seerr(
        settingsStore: InMemorySeerrSettingsStore = InMemorySeerrSettingsStore(SeerrSettings(SEERR_URL)),
        cookie: String? = "sid",
    ): Pair<SeerrRepository, SeerrSettingsRepository> {
        val settings = SeerrSettingsRepository(settingsStore)
        return SeerrRepository(dataSource, settings, InMemorySeerrSessionStore(cookie), signedIn(), TestScope(testScheduler)) to settings
    }

    @Test
    fun `discover asks for the server url when none is configured`() = runTest {
        val settingsStore = InMemorySeerrSettingsStore(SeerrSettings())
        val (repository, settings) = seerr(settingsStore, cookie = null)
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        assertEquals(DiscoverPhase.Setup, viewModel.uiState.value.phase)
    }

    @Test
    fun `discover walks setup, sign-in and the feed`() = runTest {
        dataSource.trending = { pageOf(listOf(seerrMovie(1))) }
        dataSource.discoverMovies = { pageOf(listOf(seerrMovie(2))) }
        dataSource.discoverSeries = { pageOf(listOf(seerrSeries(3))) }
        val settingsStore = InMemorySeerrSettingsStore(SeerrSettings())
        val (repository, settings) = seerr(settingsStore, cookie = null)
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        viewModel.onServerUrlChange("seerr.example.com")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals("https://seerr.example.com", settingsStore.saved.serverUrl)
        assertEquals(DiscoverPhase.SignIn, viewModel.uiState.value.phase)
        assertEquals("alice", viewModel.uiState.value.username)

        viewModel.onPasswordChange("secret")
        viewModel.signIn()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DiscoverPhase.Ready, state.phase)
        assertEquals(listOf(1), state.feed?.trending?.map { it.tmdbId })
        assertEquals(listOf(2), state.feed?.movies?.map { it.tmdbId })
        assertEquals(listOf(3), state.feed?.series?.map { it.tmdbId })
    }

    @Test
    fun `an invalid server url is rejected`() = runTest {
        val settingsStore = InMemorySeerrSettingsStore(SeerrSettings())
        val (repository, settings) = seerr(settingsStore, cookie = null)
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        viewModel.onServerUrlChange("not a url")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals(SeerrUrlError.Invalid, viewModel.uiState.value.urlError)
        assertEquals(DiscoverPhase.Setup, viewModel.uiState.value.phase)
    }

    @Test
    fun `connect falls back to http when https does not respond`() = runTest {
        dataSource.onStatus = { url -> if (url.startsWith("https")) throw IOException("no tls") }
        val settingsStore = InMemorySeerrSettingsStore(SeerrSettings())
        val (repository, settings) = seerr(settingsStore, cookie = null)
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        viewModel.onServerUrlChange("seerr.example.com:5055")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals("http://seerr.example.com:5055", settingsStore.saved.serverUrl)
        assertEquals(DiscoverPhase.SignIn, viewModel.uiState.value.phase)
    }

    @Test
    fun `connect reports unreachable when neither scheme responds`() = runTest {
        dataSource.onStatus = { throw IOException("down") }
        val settingsStore = InMemorySeerrSettingsStore(SeerrSettings())
        val (repository, settings) = seerr(settingsStore, cookie = null)
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        viewModel.onServerUrlChange("seerr.example.com:5055")
        viewModel.connect()
        advanceUntilIdle()

        assertEquals(SeerrUrlError.Unreachable, viewModel.uiState.value.urlError)
        assertEquals(DiscoverPhase.Setup, viewModel.uiState.value.phase)
    }

    @Test
    fun `a failed seerr sign-in shows the error`() = runTest {
        dataSource.signIn = { _, _, _ -> throw IOException("no route") }
        val (repository, settings) = seerr(cookie = null)
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        viewModel.onPasswordChange("bad")
        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(DiscoverPhase.SignIn, viewModel.uiState.value.phase)
        assertEquals(SignInError.ServerUnreachable, viewModel.uiState.value.signInError)
    }

    @Test
    fun `a stored session goes straight to the feed`() = runTest {
        dataSource.trending = { pageOf(listOf(seerrMovie(1))) }
        val (repository, settings) = seerr()
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        assertEquals(DiscoverPhase.Ready, viewModel.uiState.value.phase)
        assertEquals(listOf(1), viewModel.uiState.value.feed?.trending?.map { it.tmdbId })
    }

    @Test
    fun `search queries seerr after the debounce`() = runTest {
        dataSource.search = { pageOf(listOf(seerrMovie(99, "wolf"))) }
        val (repository, settings) = seerr()
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        viewModel.onQueryChange("wolf")
        advanceUntilIdle()

        assertEquals(listOf("search(wolf)"), dataSource.calls.filter { it.startsWith("search") })
        assertEquals(listOf("wolf"), viewModel.uiState.value.searchResults.map { it.title })
    }

    @Test
    fun `series detail skips available seasons and requests the selection`() = runTest {
        dataSource.seriesDetails = {
            SeerrTitleDetails(
                seerrSeries(it),
                seasons = listOf(
                    SeerrSeason(0, "Specials", 1),
                    SeerrSeason(1, "Season 1", 8, SeerrMediaStatus.Available),
                    SeerrSeason(2, "Season 2", 8),
                ),
            )
        }
        val (repository, _) = seerr()
        val viewModel = SeerrDetailViewModel(SeerrMediaType.Series, 42, repository)
        advanceUntilIdle()

        var state = viewModel.uiState.value as SeerrDetailUiState.Loaded
        assertEquals(setOf(0, 2), state.selectedSeasons)

        viewModel.toggleSeason(0)
        viewModel.request()
        advanceUntilIdle()

        state = viewModel.uiState.value as SeerrDetailUiState.Loaded
        assertIs<RequestActionState.Done>(state.requestState)
        assertEquals(listOf(2), dataSource.requestArgs?.third)
        assertEquals(SeerrRequestStatus.Pending, state.details.title.requestStatus)
    }

    @Test
    fun `movie detail requests the movie directly`() = runTest {
        val (repository, _) = seerr()
        val viewModel = SeerrDetailViewModel(SeerrMediaType.Movie, 55, repository)
        advanceUntilIdle()

        viewModel.request()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SeerrDetailUiState.Loaded
        assertIs<RequestActionState.Done>(state.requestState)
        assertEquals(Triple(SeerrMediaType.Movie, 55, null), dataSource.requestArgs)
    }

    @Test
    fun `requesting an already-available title is not offered`() = runTest {
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, mediaStatus = SeerrMediaStatus.Available)) }
        val (repository, _) = seerr()
        val viewModel = SeerrDetailViewModel(SeerrMediaType.Movie, 55, repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value as SeerrDetailUiState.Loaded
        assertTrue(state.details.title.isInLibrary)
    }

    @Test
    fun `my requests lists each request with its status`() = runTest {
        dataSource.requests = {
            pageOf(
                listOf(
                    seerrRequest(1, tmdbId = 10, status = SeerrRequestStatus.Pending),
                    seerrRequest(2, tmdbId = 20, status = SeerrRequestStatus.Approved, mediaStatus = SeerrMediaStatus.Processing),
                    seerrRequest(3, tmdbId = 30, status = SeerrRequestStatus.Approved, mediaStatus = SeerrMediaStatus.Available),
                    seerrRequest(4, tmdbId = 40, status = SeerrRequestStatus.Declined),
                ),
            )
        }
        // Title details are the fresher source; keep them consistent with the request statuses.
        dataSource.movieDetails = {
            SeerrTitleDetails(seerrMovie(it, mediaStatus = if (it == 30) SeerrMediaStatus.Available else SeerrMediaStatus.NotRequested))
        }
        val (repository, _) = seerr()
        val viewModel = MyRequestsViewModel(repository)
        advanceUntilIdle()

        val requests = (viewModel.uiState.value as MyRequestsUiState.Loaded).requests
        assertEquals(listOf(1, 2, 3, 4), requests.map { it.id })
        assertEquals(SeerrRequestStatus.Declined, requests[3].status)
        assertTrue(requests[2].isAvailable)
        assertEquals("Movie 10", requests[0].title)
    }

    @Test
    fun `a load failure is surfaced with retry`() = runTest {
        var fail = true
        dataSource.trending = { if (fail) throw IOException("down") else pageOf(listOf(seerrMovie(1))) }
        dataSource.discoverMovies = { if (fail) throw IOException("down") else pageOf(listOf(seerrMovie(2))) }
        dataSource.discoverSeries = { if (fail) throw IOException("down") else pageOf(listOf(seerrSeries(3))) }
        val (repository, settings) = seerr()
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        assertEquals(LoadError.ServerUnreachable, viewModel.uiState.value.feedError)

        fail = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.uiState.value.feed?.trending?.map { it.tmdbId })
    }

    @Test
    fun `a section the server forbids degrades to empty rather than failing the feed`() = runTest {
        dataSource.trending = { pageOf(listOf(seerrMovie(1))) }
        dataSource.discoverSeries = { throw IllegalStateException("Forbidden") }
        val (repository, settings) = seerr()
        val viewModel = DiscoverViewModel(repository, settings)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.feedError)
        assertEquals(emptyList(), state.feed?.series)
        assertTrue(state.feed!!.trending.isNotEmpty())
    }
}
