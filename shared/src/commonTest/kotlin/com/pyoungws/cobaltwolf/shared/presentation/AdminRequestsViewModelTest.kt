package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore
import com.pyoungws.cobaltwolf.shared.library.SESSION
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.seerr.FakeSeerrDataSource
import com.pyoungws.cobaltwolf.shared.seerr.InMemorySeerrSessionStore
import com.pyoungws.cobaltwolf.shared.seerr.InMemorySeerrSettingsStore
import com.pyoungws.cobaltwolf.shared.seerr.SEERR_URL
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRequestStatus
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettings
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrTitleDetails
import com.pyoungws.cobaltwolf.shared.seerr.pageOf
import com.pyoungws.cobaltwolf.shared.seerr.seerrMovie
import com.pyoungws.cobaltwolf.shared.seerr.seerrRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminRequestsViewModelTest {
    private val dataSource = FakeSeerrDataSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Cancels the view model scope afterwards so the poll loop cannot hang runTest. */
    private suspend fun <T : ViewModel> withViewModel(viewModel: T, body: suspend (T) -> Unit) {
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private suspend fun signedIn() = AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }

    private suspend fun TestScope.seerr() = SeerrRepository(
        dataSource,
        SeerrSettingsRepository(InMemorySeerrSettingsStore(SeerrSettings(SEERR_URL))),
        InMemorySeerrSessionStore("sid"),
        signedIn(),
        TestScope(testScheduler),
    )

    @Test
    fun `the queue lists pending requests with titles and requesters`() = runTest {
        dataSource.requests = {
            pageOf(listOf(seerrRequest(1, tmdbId = 10, requestedById = 8, requestedByName = "bob")))
        }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(listOf(1), state.requests.map { it.id })
            assertEquals("Movie 10", state.requests.single().title)
            assertEquals("bob", state.requests.single().requestedByName)
        }
    }

    @Test
    fun `approving removes the request from the queue`() = runTest {
        var approved = false
        dataSource.requests = { pageOf(if (approved) emptyList() else listOf(seerrRequest(1, tmdbId = 10))) }
        dataSource.approveRequest = {
            approved = true
            seerrRequest(it, status = SeerrRequestStatus.Approved)
        }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            viewModel.approve(1)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AdminActionStatus.Succeeded("${AdminRequestsViewModel.APPROVE_KEY}1"), state.action)
            assertTrue(state.requests.isEmpty())
        }
    }

    @Test
    fun `declining removes the request from the queue`() = runTest {
        var declined = false
        dataSource.requests = { pageOf(if (declined) emptyList() else listOf(seerrRequest(1, tmdbId = 10))) }
        dataSource.declineRequest = {
            declined = true
            seerrRequest(it, status = SeerrRequestStatus.Declined)
        }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            viewModel.decline(1)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AdminActionStatus.Succeeded("${AdminRequestsViewModel.DECLINE_KEY}1"), state.action)
            assertTrue(state.requests.isEmpty())
        }
    }

    @Test
    fun `a failed action is surfaced and the request stays`() = runTest {
        dataSource.requests = { pageOf(listOf(seerrRequest(1, tmdbId = 10))) }
        dataSource.approveRequest = { throw IOException("down") }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            viewModel.approve(1)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AdminActionStatus.Failed("${AdminRequestsViewModel.APPROVE_KEY}1", LoadError.ServerUnreachable), state.action)
            assertEquals(listOf(1), state.requests.map { it.id })
        }
    }

    @Test
    fun `details open for a request and close again`() = runTest {
        dataSource.requests = { pageOf(listOf(seerrRequest(5, tmdbId = 50))) }
        dataSource.requestDetails = { seerrRequest(it, tmdbId = 50, requestedByName = "bob") }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, "The Movie")) }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            viewModel.openDetails(5)
            runCurrent()

            val details = viewModel.uiState.value.details
            assertIs<AdminRequestDetails.Loaded>(details)
            assertEquals(5, details.requestId)
            assertEquals("The Movie", details.request.title)
            assertEquals("bob", details.request.requestedByName)

            viewModel.closeDetails()
            assertNull(viewModel.uiState.value.details)
        }
    }

    @Test
    fun `a failed details load is surfaced in the sheet`() = runTest {
        dataSource.requests = { pageOf(listOf(seerrRequest(5, tmdbId = 50))) }
        dataSource.requestDetails = { throw IOException("down") }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            viewModel.openDetails(5)
            runCurrent()

            val details = viewModel.uiState.value.details
            assertIs<AdminRequestDetails.Failed>(details)
            assertEquals(LoadError.ServerUnreachable, details.error)
        }
    }

    @Test
    fun `an unreachable server shows an error instead of the list`() = runTest {
        dataSource.requests = { throw IOException("down") }
        val repository = seerr()
        advanceUntilIdle()
        withViewModel(AdminRequestsViewModel(repository)) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(LoadError.ServerUnreachable, state.error)
            assertTrue(state.requests.isEmpty())
        }
    }
}
