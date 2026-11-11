package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.FakeAdminDataSource
import com.pyoungws.bluewolfplayer.shared.admin.playingSession
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.library.SESSION
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.model.LoadError
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
        body: suspend (AdminDashboardViewModel) -> Unit,
    ) {
        val auth = AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }
        val viewModel = AdminDashboardViewModel(AdminRepository(dataSource, SessionGuard(auth)))
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
