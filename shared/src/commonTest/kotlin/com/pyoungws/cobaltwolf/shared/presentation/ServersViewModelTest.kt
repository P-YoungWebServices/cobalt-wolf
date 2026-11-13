package com.pyoungws.cobaltwolf.shared.presentation

import com.pyoungws.cobaltwolf.shared.auth.AuthenticatedUser
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.InMemoryServerProfilesStore
import com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore
import com.pyoungws.cobaltwolf.shared.auth.SERVER
import com.pyoungws.cobaltwolf.shared.auth.SessionState
import com.pyoungws.cobaltwolf.shared.auth.VIEWER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {
    private val dataSource = FakeAuthDataSource()
    private lateinit var repository: AuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = AuthRepository(dataSource, InMemorySessionStore(), InMemoryServerProfilesStore())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun signInBoth() {
        repository.signIn(SERVER, "alice", "hunter2")
        dataSource.onAuthenticateByName = { _, _ -> AuthenticatedUser("token-b", VIEWER) }
        repository.signIn("https://other.example.com", "bob", "hunter3")
    }

    @Test
    fun `the active profile follows the session`() = runTest {
        signInBoth()
        val viewModel = ServersViewModel(repository)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(2, state.profiles.size)
        val active = state.profiles.single { it.id == state.activeProfileId }
        assertEquals("bob", active.userName)
    }

    @Test
    fun `switching updates the active profile`() = runTest {
        signInBoth()
        val viewModel = ServersViewModel(repository)
        runCurrent()
        val alice = viewModel.uiState.value.profiles.first { it.userName == "alice" }

        viewModel.switchTo(alice.id)
        runCurrent()

        assertEquals(alice.id, viewModel.uiState.value.activeProfileId)
        assertEquals(false, viewModel.uiState.value.busy)
        val session = (repository.sessionState.value as SessionState.SignedIn).session
        assertEquals(SERVER, session.serverAddress)
    }

    @Test
    fun `removing the active profile signs out`() = runTest {
        signInBoth()
        val viewModel = ServersViewModel(repository)
        runCurrent()
        val activeId = viewModel.uiState.value.activeProfileId!!

        viewModel.remove(activeId)
        runCurrent()

        assertIs<SessionState.SignedOut>(repository.sessionState.value)
        assertNull(viewModel.uiState.value.activeProfileId)
        assertEquals(1, viewModel.uiState.value.profiles.size)
    }
}
