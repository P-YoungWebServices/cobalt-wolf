package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.auth.ADMIN
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.AuthenticatedUser
import com.pyoungws.bluewolfplayer.shared.auth.VIEWER
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.auth.SERVER
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.ServerInfo
import com.pyoungws.bluewolfplayer.shared.model.Session
import com.pyoungws.bluewolfplayer.shared.server.ServerError
import com.pyoungws.bluewolfplayer.shared.server.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.api.PublicSystemInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectAndHomeViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `connect exposes the validated server once, then it can be consumed`() = runTest {
        val viewModel = ServerConnectViewModel(
            ServerRepository {
                listOf(
                    RecommendedServerInfo(
                        "https://den.example/", 10, RecommendedServerInfoScore.GREAT, emptyList(),
                        Result.success(PublicSystemInfo(serverName = "Den", version = "12.4.0", id = "id-1")),
                    ),
                )
            },
        )
        viewModel.onAddressChange("den.example")

        viewModel.connect()
        advanceUntilIdle()
        assertEquals(ServerInfo("https://den.example", "Den", "12.4.0", "id-1"), viewModel.uiState.value.validatedServer)

        viewModel.onServerConsumed()
        assertNull(viewModel.uiState.value.validatedServer)
    }

    @Test
    fun `connect shows the validation error`() = runTest {
        val viewModel = ServerConnectViewModel(ServerRepository { emptyList() })
        viewModel.onAddressChange("nope")

        viewModel.connect()
        advanceUntilIdle()

        assertEquals(ServerError.InvalidAddress, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.validatedServer)
    }

    @Test
    fun `home shows the signed-in user and admin flag`() = runTest {
        val store = InMemorySessionStore(Session(SERVER, ADMIN.id, "token"))
        val repository = AuthRepository(FakeAuthDataSource(), store).apply { restoreSession() }

        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        assertEquals(HomeUiState.Loaded(ADMIN), viewModel.uiState.value)
    }

    @Test
    fun `home signs out when the stored token was revoked`() = runTest {
        val store = InMemorySessionStore(Session(SERVER, ADMIN.id, "revoked"))
        val dataSource = FakeAuthDataSource().apply { onGetCurrentUser = { throw InvalidStatusException(401) } }
        val repository = AuthRepository(dataSource, store).apply { restoreSession() }

        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        // The app returns to sign-in for the same server; home drops the stale user.
        assertEquals(SessionState.SignedOut(lastServerAddress = SERVER), repository.sessionState.value)
        assertNull(store.stored)
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `sign out from home ends the session and forgets the user`() = runTest {
        val store = InMemorySessionStore(Session(SERVER, ADMIN.id, "token"))
        val dataSource = FakeAuthDataSource()
        val repository = AuthRepository(dataSource, store).apply { restoreSession() }
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeUiState.Loaded(ADMIN), viewModel.uiState.value)

        viewModel.signOut()
        assertEquals(true, viewModel.isSigningOut.value)
        advanceUntilIdle()

        assertEquals(false, viewModel.isSigningOut.value)
        assertEquals("logout($SERVER, token)", dataSource.calls.last())
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(lastServerAddress = SERVER), repository.sessionState.value)
        assertEquals(HomeUiState.Loading, viewModel.uiState.value, "previous user's data is dropped")
    }

    @Test
    fun `home reloads for the next user after switching`() = runTest {
        val store = InMemorySessionStore(Session(SERVER, ADMIN.id, "token-admin"))
        val dataSource = FakeAuthDataSource().apply {
            onGetCurrentUser = { token -> if (token == "token-admin") ADMIN else VIEWER }
            onAuthenticateByName = { _, _ -> AuthenticatedUser("token-viewer", VIEWER) }
        }
        val repository = AuthRepository(dataSource, store).apply { restoreSession() }
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()
        repository.signIn(SERVER, "bob", "pw")
        advanceUntilIdle()

        assertEquals(HomeUiState.Loaded(VIEWER), viewModel.uiState.value)
    }
}
