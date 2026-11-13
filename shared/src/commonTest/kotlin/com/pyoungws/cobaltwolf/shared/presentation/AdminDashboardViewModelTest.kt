package com.pyoungws.cobaltwolf.shared.presentation

import com.pyoungws.cobaltwolf.shared.admin.AdminRepository
import com.pyoungws.cobaltwolf.shared.admin.FakeAdminDataSource
import com.pyoungws.cobaltwolf.shared.admin.playingSession
import com.pyoungws.cobaltwolf.shared.arrs.ArrService
import com.pyoungws.cobaltwolf.shared.arrs.ArrSettingsRepository
import com.pyoungws.cobaltwolf.shared.arrs.InMemoryArrSettingsStore
import com.pyoungws.cobaltwolf.shared.auth.ADMIN
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore
import com.pyoungws.cobaltwolf.shared.auth.VIEWER
import com.pyoungws.cobaltwolf.shared.bazarr.BazarrRepository
import com.pyoungws.cobaltwolf.shared.bazarr.FakeBazarrDataSource
import com.pyoungws.cobaltwolf.shared.library.SESSION
import com.pyoungws.cobaltwolf.shared.library.SessionGuard
import com.pyoungws.cobaltwolf.shared.library.movie
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.UserProfile
import com.pyoungws.cobaltwolf.shared.prowlarr.FakeProwlarrDataSource
import com.pyoungws.cobaltwolf.shared.prowlarr.ProwlarrIndexer
import com.pyoungws.cobaltwolf.shared.prowlarr.ProwlarrRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AdminDashboardViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Creates the ViewModel; the caller must run [body] inside it so the never-ending
     * poll loop is cancelled even when an assertion fails (it would otherwise hang runTest).
     */
    private suspend fun withViewModel(
        dataSource: FakeAdminDataSource,
        bazarr: FakeBazarrDataSource = FakeBazarrDataSource(),
        prowlarr: FakeProwlarrDataSource = FakeProwlarrDataSource(),
        user: UserProfile = ADMIN,
        configureServices: ArrSettingsRepository.() -> Unit = {},
        body: suspend (AdminDashboardViewModel) -> Unit,
    ) {
        val auth = AuthRepository(
            FakeAuthDataSource().apply { onGetCurrentUser = { user } },
            InMemorySessionStore(SESSION),
        ).apply { restoreSession() }
        val settings = ArrSettingsRepository(InMemoryArrSettingsStore()).apply(configureServices)
        val viewModel = AdminDashboardViewModel(
            auth,
            AdminRepository(dataSource, SessionGuard(auth)),
            BazarrRepository(bazarr, settings),
            ProwlarrRepository(prowlarr, settings),
        )
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `dashboard loads sessions and recently added`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            sessions = { listOf(playingSession("s1")) }
            recentlyAdded = { listOf(movie("m1")) }
        }
        withViewModel(dataSource) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(listOf("s1"), state.sessions.map { it.id })
            assertEquals(listOf("m1"), state.recentlyAdded.map { it.id })
            assertNull(state.error)
        }
    }

    @Test
    fun `the dashboard re-fetches on the refresh interval`() = runTest {
        val dataSource = FakeAdminDataSource()
        withViewModel(dataSource) {
            runCurrent()
            assertEquals(1, dataSource.dashboardCalls)

            advanceTimeBy(AdminDashboardViewModel.DEFAULT_REFRESH_SECONDS * 1_000L)
            runCurrent()
            assertEquals(2, dataSource.dashboardCalls)

            advanceTimeBy(AdminDashboardViewModel.DEFAULT_REFRESH_SECONDS * 1_000L)
            runCurrent()
            assertEquals(3, dataSource.dashboardCalls)
        }
    }

    @Test
    fun `changing the refresh interval applies from the next poll`() = runTest {
        val dataSource = FakeAdminDataSource()
        withViewModel(dataSource) { viewModel ->
            runCurrent()
            assertEquals(1, dataSource.dashboardCalls)

            viewModel.setRefreshSeconds(30)
            runCurrent()
            // The restart refreshes once, then waits for the new interval.
            assertEquals(2, dataSource.dashboardCalls)

            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(2, dataSource.dashboardCalls, "old 10s interval must not fire again")

            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(3, dataSource.dashboardCalls)
        }
    }

    @Test
    fun `viewers get recently added without polling sessions`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            sessions = { listOf(playingSession("s1")) }
            recentlyAdded = { listOf(movie("m1")) }
        }
        withViewModel(dataSource, user = VIEWER) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isAdmin)
            assertEquals(emptyList(), state.sessions)
            assertEquals(listOf("m1"), state.recentlyAdded.map { it.id })
            assertEquals(0, dataSource.dashboardCalls)

            // refresh() reloads recently added but still never touches the admin feed.
            viewModel.refresh()
            runCurrent()
            assertEquals(0, dataSource.dashboardCalls)
        }
    }

    @Test
    fun `a failed poll keeps the last good data`() = runTest {
        var fail = false
        val dataSource = FakeAdminDataSource().apply {
            sessions = {
                if (fail) throw InvalidStatusException(500)
                listOf(playingSession("s1"))
            }
        }
        withViewModel(dataSource) {
            runCurrent()

            fail = true
            advanceTimeBy(AdminDashboardViewModel.DEFAULT_REFRESH_SECONDS * 1_000L)
            runCurrent()

            val state = it.uiState.value
            assertEquals(listOf("s1"), state.sessions.map { s -> s.id })
            assertEquals(LoadError.Unexpected("Invalid HTTP status in response: 500"), state.error)
        }
    }

    @Test
    fun `unconfigured services report an unconfigured badge`() = runTest {
        withViewModel(FakeAdminDataSource()) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.bazarr.configured)
            assertNull(state.bazarr.count)
            assertEquals(false, state.prowlarr.configured)
            assertNull(state.prowlarr.count)
        }
    }

    @Test
    fun `configured services report their counts`() = runTest {
        val bazarr = FakeBazarrDataSource().apply {
            movies = listOf(com.pyoungws.cobaltwolf.shared.bazarr.WantedMovie("Film", emptyList()))
            episodes = listOf(com.pyoungws.cobaltwolf.shared.bazarr.WantedEpisode("Show", "S01E01", null, emptyList()))
        }
        val prowlarr = FakeProwlarrDataSource().apply {
            indexers = listOf(ProwlarrIndexer(1, "IndexOne", "torrent", true), ProwlarrIndexer(2, "IndexTwo", "usenet", true))
            healthIssues = listOf(com.pyoungws.cobaltwolf.shared.prowlarr.ProwlarrHealthIssue("IndexerFail", "warning", "down"))
        }
        withViewModel(
            FakeAdminDataSource(),
            bazarr = bazarr,
            prowlarr = prowlarr,
            configureServices = {
                update(ArrService.Bazarr, "https://bazarr.example.com", "key")
                update(ArrService.Prowlarr, "https://prowlarr.example.com", "key")
            },
        ) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(true, state.bazarr.configured)
            assertEquals(2, state.bazarr.count)
            assertEquals(true, state.prowlarr.configured)
            assertEquals(2, state.prowlarr.count)
            assertEquals(1, state.prowlarr.issues)
        }
    }

    @Test
    fun `a failing service keeps its configured badge without a count`() = runTest {
        val bazarr = FakeBazarrDataSource().apply { error = InvalidStatusException(500) }
        withViewModel(
            FakeAdminDataSource(),
            bazarr = bazarr,
            configureServices = { update(ArrService.Bazarr, "https://bazarr.example.com", "key") },
        ) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(true, state.bazarr.configured)
            assertNull(state.bazarr.count)
        }
    }

    @Test
    fun `sendMessage reports success for the session`() = runTest {
        val dataSource = FakeAdminDataSource()
        withViewModel(dataSource) { viewModel ->
            runCurrent()

            viewModel.sendMessage("s1", "server reboot in 5 minutes")
            runCurrent()

            assertEquals(MessageStatus.Sent("s1"), viewModel.uiState.value.message)
            assertEquals("message(s1, server reboot in 5 minutes)", dataSource.calls.last())
        }
    }

    @Test
    fun `sendMessage surfaces failures`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            onSendMessage = { _, _ -> throw InvalidStatusException(404) }
        }
        withViewModel(dataSource) { viewModel ->
            runCurrent()

            viewModel.sendMessage("gone", "hi")
            runCurrent()

            assertEquals(MessageStatus.Failed("gone", LoadError.NotFound), viewModel.uiState.value.message)
        }
    }

    @Test
    fun `a blank message is never sent`() = runTest {
        val dataSource = FakeAdminDataSource()
        withViewModel(dataSource) { viewModel ->
            runCurrent()

            viewModel.sendMessage("s1", "   ")
            runCurrent()

            assertNull(viewModel.uiState.value.message)
            assertEquals(emptyList(), dataSource.calls.filter { it.startsWith("message") })
        }
    }
}
