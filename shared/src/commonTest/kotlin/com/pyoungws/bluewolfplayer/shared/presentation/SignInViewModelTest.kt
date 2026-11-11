package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.auth.ADMIN
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.AuthenticatedUser
import com.pyoungws.bluewolfplayer.shared.auth.CODE
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.auth.JellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectState
import com.pyoungws.bluewolfplayer.shared.auth.QuickConnectTicket
import com.pyoungws.bluewolfplayer.shared.auth.SECRET
import com.pyoungws.bluewolfplayer.shared.auth.SERVER
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.auth.SignInError
import com.pyoungws.bluewolfplayer.shared.auth.VIEWER
import com.pyoungws.bluewolfplayer.shared.model.PublicUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val dataSource = FakeAuthDataSource()
    private val store = InMemorySessionStore()
    private val repository = AuthRepository(dataSource, store, quickConnectPollInterval = 5.seconds)
    // Created on first use, so each test can configure the fake (e.g. its user list) first.
    private val viewModel by lazy { SignInViewModel(SERVER, repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful sign in clears busy state and signs the session in`() = runTest {
        viewModel.onUsernameChange("alice")
        viewModel.onPasswordChange("hunter2")

        viewModel.signIn()
        assertTrue(viewModel.uiState.value.isSigningIn)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSigningIn)
        assertNull(state.error)
        assertIs<SessionState.SignedIn>(repository.sessionState.value)
        assertEquals(
            listOf("getPublicUsers", "authenticateByName($SERVER, alice)", "getServerName($SERVER)"),
            dataSource.calls,
        )
    }

    @Test
    fun `successful sign in clears the credentials from the form`() = runTest {
        viewModel.onUsernameChange("alice")
        viewModel.onPasswordChange("hunter2")

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(SignInUiState(isLoadingUsers = false), viewModel.uiState.value)
    }

    @Test
    fun `quick connect sign in resets the form for the next user`() = runTest {
        viewModel.onUsernameChange("alice")
        viewModel.startQuickConnect()
        advanceUntilIdle()

        assertEquals(SignInUiState(isLoadingUsers = false), viewModel.uiState.value)
        assertIs<SessionState.SignedIn>(repository.sessionState.value)
    }

    // region Login-screen user list

    /** Creates the ViewModel (which starts loading the user list) and lets the load finish. */
    private fun TestScope.startViewModel() {
        viewModel.uiState
        advanceUntilIdle()
    }

    private val withPassword = PublicUser(id = "u1", name = "alice", hasPassword = true)
    private val noPassword = PublicUser(id = "u2", name = "kids", hasPassword = false)

    @Test
    fun `server users are loaded and shown as a picker`() = runTest {
        dataSource.onGetPublicUsers = { listOf(withPassword, noPassword) }
        assertTrue(viewModel.uiState.value.isLoadingUsers)
        assertFalse(viewModel.uiState.value.showUserList)

        startViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingUsers)
        assertEquals(listOf(withPassword, noPassword), state.publicUsers)
        assertTrue(state.showUserList)
    }

    @Test
    fun `with no listed users the username form is shown`() = runTest {
        dataSource.onGetPublicUsers = { emptyList() }
        startViewModel()
        assertFalse(viewModel.uiState.value.showUserList)
    }

    @Test
    fun `if the user list fails to load the username form is shown`() = runTest {
        dataSource.onGetPublicUsers = { throw TimeoutException("offline") }
        startViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingUsers)
        assertFalse(state.showUserList)
        assertNull(state.error)
    }

    @Test
    fun `picking a user with a password asks only for the password`() = runTest {
        dataSource.onGetPublicUsers = { listOf(withPassword) }
        var received: Pair<String, String>? = null
        dataSource.onAuthenticateByName = { user, password ->
            received = user to password
            AuthenticatedUser("t", ADMIN)
        }
        startViewModel()

        viewModel.selectUser(withPassword)
        advanceUntilIdle()
        assertEquals(withPassword, viewModel.uiState.value.selectedUser)
        assertFalse(viewModel.uiState.value.showUserList)
        assertNull(received, "must not sign in before the password is entered")

        viewModel.onPasswordChange("hunter2")
        viewModel.signIn()
        advanceUntilIdle()

        assertEquals("alice" to "hunter2", received)
        assertIs<SessionState.SignedIn>(repository.sessionState.value)
    }

    @Test
    fun `picking a user without a password signs in straight away`() = runTest {
        dataSource.onGetPublicUsers = { listOf(noPassword) }
        var received: Pair<String, String>? = null
        dataSource.onAuthenticateByName = { user, password ->
            received = user to password
            AuthenticatedUser("t", VIEWER)
        }
        startViewModel()

        viewModel.selectUser(noPassword)
        advanceUntilIdle()

        assertEquals("kids" to "", received)
        assertIs<SessionState.SignedIn>(repository.sessionState.value)
    }

    @Test
    fun `typing a username manually and going back to the list`() = runTest {
        dataSource.onGetPublicUsers = { listOf(withPassword) }
        startViewModel()

        viewModel.enterUsernameManually()
        assertTrue(viewModel.uiState.value.manualEntry)
        assertFalse(viewModel.uiState.value.showUserList)
        viewModel.onUsernameChange("hidden-admin")
        viewModel.onPasswordChange("pw")

        viewModel.backToUserList()

        val state = viewModel.uiState.value
        assertTrue(state.showUserList)
        assertEquals("", state.username)
        assertEquals("", state.password)
    }

    @Test
    fun `the user list is kept after signing in so the next user can pick from it`() = runTest {
        dataSource.onGetPublicUsers = { listOf(withPassword, noPassword) }
        startViewModel()

        viewModel.selectUser(noPassword)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedUser)
        assertEquals(listOf(withPassword, noPassword), state.publicUsers)
        assertTrue(state.showUserList)
    }

    // endregion

    @Test
    fun `wrong password shows an error and clears the password`() = runTest {
        dataSource.onAuthenticateByName = { _, _ -> throw InvalidStatusException(401) }
        viewModel.onUsernameChange("alice")
        viewModel.onPasswordChange("wrong")

        viewModel.signIn()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SignInError.InvalidCredentials, state.error)
        assertEquals("", state.password)
        assertEquals("alice", state.username)
        assertFalse(state.isSigningIn)
    }

    @Test
    fun `network errors keep the password so the user can retry`() = runTest {
        dataSource.onAuthenticateByName = { _, _ -> throw TimeoutException("offline") }
        viewModel.onUsernameChange("alice")
        viewModel.onPasswordChange("hunter2")

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(SignInError.ServerUnreachable, viewModel.uiState.value.error)
        assertEquals("hunter2", viewModel.uiState.value.password)
    }

    @Test
    fun `editing a field clears the previous error`() = runTest {
        viewModel.signIn()
        advanceUntilIdle()
        assertEquals(SignInError.MissingUsername, viewModel.uiState.value.error)

        viewModel.onUsernameChange("a")
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a second sign in tap while one is running is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val slowRepository = AuthRepository(
            object : JellyfinAuthDataSource by dataSource {
                override suspend fun authenticateByName(serverAddress: String, username: String, password: String): AuthenticatedUser {
                    attempts++
                    gate.await()
                    return AuthenticatedUser("t", VIEWER)
                }
            },
            store,
        )
        val vm = SignInViewModel(SERVER, slowRepository)
        vm.onUsernameChange("bob")

        vm.signIn()
        runCurrent()
        vm.signIn()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, attempts)
    }

    @Test
    fun `quick connect shows the code and signs in once approved`() = runTest {
        viewModel.startQuickConnect()
        runCurrent()

        assertEquals(QuickConnectState.WaitingForApproval(CODE), viewModel.uiState.value.quickConnect)

        advanceUntilIdle()
        assertNull(viewModel.uiState.value.quickConnect)
        assertEquals(VIEWER.id, (repository.sessionState.value as SessionState.SignedIn).session.userId)
    }

    @Test
    fun `cancelling quick connect stops polling and returns to the form`() = runTest {
        dataSource.onPoll = { QuickConnectTicket(CODE, SECRET, authenticated = false) }
        viewModel.startQuickConnect()
        advanceTimeBy(11.seconds)
        runCurrent()
        val polls = dataSource.pollCount

        viewModel.cancelQuickConnect()
        advanceTimeBy(60.seconds)

        assertEquals(2, polls)
        assertEquals(polls, dataSource.pollCount)
        assertNull(viewModel.uiState.value.quickConnect)
        assertNull(store.stored)
    }

    @Test
    fun `quick connect failure is shown and can be retried`() = runTest {
        dataSource.onQuickConnectEnabled = { false }
        viewModel.startQuickConnect()
        advanceUntilIdle()
        assertEquals(QuickConnectState.Failed(SignInError.QuickConnectDisabled), viewModel.uiState.value.quickConnect)

        dataSource.onQuickConnectEnabled = { true }
        viewModel.startQuickConnect()
        advanceUntilIdle()
        assertIs<SessionState.SignedIn>(repository.sessionState.value)
    }
}
