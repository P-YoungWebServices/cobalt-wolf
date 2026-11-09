package com.pyoungws.bluewolfplayer.shared.auth

import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val dataSource = FakeAuthDataSource()
    private val store = InMemorySessionStore()
    private val repository = AuthRepository(dataSource, store, quickConnectPollInterval = 5.seconds)

    // region Session restore

    @Test
    fun `restoreSession reports signed out when nothing is stored`() = runTest {
        assertEquals(SessionState.Unknown, repository.sessionState.value)
        repository.restoreSession()
        assertEquals(SessionState.SignedOut(lastServerAddress = null), repository.sessionState.value)
    }

    @Test
    fun `restoreSession restores a stored session`() = runTest {
        val session = Session(SERVER, ADMIN.id, "stored-token")
        store.stored = session
        repository.restoreSession()
        assertEquals(SessionState.SignedIn(session), repository.sessionState.value)
    }

    // endregion

    // region Username and password

    @Test
    fun `signIn stores the access token and returns the user with the admin flag`() = runTest {
        val result = repository.signIn(SERVER, "alice", "hunter2")

        assertEquals(SignInResult.Success(ADMIN), result)
        val expected = Session(SERVER, ADMIN.id, "token-password")
        assertEquals(expected, store.stored)
        assertEquals(SessionState.SignedIn(expected), repository.sessionState.value)
    }

    @Test
    fun `signIn trims the username and passes the password unchanged`() = runTest {
        var received: Pair<String, String>? = null
        dataSource.onAuthenticateByName = { user, password ->
            received = user to password
            AuthenticatedUser("t", VIEWER)
        }

        repository.signIn(SERVER, "  bob  ", " pass with spaces ")

        assertEquals("bob" to " pass with spaces ", received)
    }

    @Test
    fun `signIn allows an empty password`() = runTest {
        assertIs<SignInResult.Success>(repository.signIn(SERVER, "alice", ""))
    }

    @Test
    fun `signIn rejects a blank username without calling the server`() = runTest {
        val result = repository.signIn(SERVER, "   ", "pw")

        assertEquals(SignInResult.Failure(SignInError.MissingUsername), result)
        assertEquals(emptyList(), dataSource.calls)
        assertNull(store.stored)
    }

    @Test
    fun `signIn maps 401 to invalid credentials and stores nothing`() = runTest {
        dataSource.onAuthenticateByName = { _, _ -> throw InvalidStatusException(401) }

        val result = repository.signIn(SERVER, "alice", "wrong")

        assertEquals(SignInResult.Failure(SignInError.InvalidCredentials), result)
        assertNull(store.stored)
        assertEquals(SessionState.Unknown, repository.sessionState.value)
    }

    @Test
    fun `signIn maps 403 to invalid credentials`() = runTest {
        dataSource.onAuthenticateByName = { _, _ -> throw InvalidStatusException(403) }
        assertEquals(SignInResult.Failure(SignInError.InvalidCredentials), repository.signIn(SERVER, "a", "b"))
    }

    @Test
    fun `signIn maps connection failures`() = runTest {
        dataSource.onAuthenticateByName = { _, _ -> throw TimeoutException("down") }
        assertEquals(SignInResult.Failure(SignInError.ServerUnreachable), repository.signIn(SERVER, "a", "b"))

        dataSource.onAuthenticateByName = { _, _ -> throw SecureConnectionException("bad cert") }
        assertEquals(SignInResult.Failure(SignInError.SecureConnectionFailed), repository.signIn(SERVER, "a", "b"))
    }

    @Test
    fun `signIn maps other server errors to unexpected`() = runTest {
        dataSource.onAuthenticateByName = { _, _ -> throw InvalidStatusException(500) }
        val result = repository.signIn(SERVER, "a", "b")
        assertIs<SignInError.Unexpected>((result as SignInResult.Failure).error)
    }

    // endregion

    // region Quick Connect

    @Test
    fun `quickConnect shows the code, polls until approved, then signs in`() = runTest {
        val states = repository.quickConnect(SERVER).toList()

        assertEquals(
            listOf(
                QuickConnectState.Starting,
                QuickConnectState.WaitingForApproval(CODE),
                QuickConnectState.SignedIn(VIEWER),
            ),
            states,
        )
        assertEquals(2, dataSource.pollCount)
        assertEquals(10_000, currentTime, "polls are spaced by the poll interval")
        assertEquals("authenticateWithQuickConnect($SECRET)", dataSource.calls.last())
        val expected = Session(SERVER, VIEWER.id, "token-quick-connect")
        assertEquals(expected, store.stored)
        assertEquals(SessionState.SignedIn(expected), repository.sessionState.value)
    }

    @Test
    fun `quickConnect signs in without polling when already authenticated`() = runTest {
        dataSource.onInitiate = { QuickConnectTicket(CODE, SECRET, authenticated = true) }

        val states = repository.quickConnect(SERVER).toList()

        assertEquals(QuickConnectState.SignedIn(VIEWER), states.last())
        assertEquals(0, dataSource.pollCount)
    }

    @Test
    fun `quickConnect fails when disabled on the server without initiating`() = runTest {
        dataSource.onQuickConnectEnabled = { false }

        val states = repository.quickConnect(SERVER).toList()

        assertEquals(QuickConnectState.Failed(SignInError.QuickConnectDisabled), states.last())
        assertEquals(listOf("isQuickConnectEnabled"), dataSource.calls)
        assertNull(store.stored)
    }

    @Test
    fun `quickConnect treats 401 on initiate as disabled`() = runTest {
        dataSource.onInitiate = { throw InvalidStatusException(401) }

        val states = repository.quickConnect(SERVER).toList()

        assertEquals(QuickConnectState.Failed(SignInError.QuickConnectDisabled), states.last())
    }

    @Test
    fun `quickConnect reports expiry when the server forgets the secret`() = runTest {
        dataSource.onPoll = { poll -> if (poll < 3) QuickConnectTicket(CODE, SECRET, false) else throw InvalidStatusException(404) }

        val states = repository.quickConnect(SERVER).toList()

        assertEquals(QuickConnectState.Failed(SignInError.QuickConnectExpired), states.last())
        assertEquals(3, dataSource.pollCount)
        assertNull(store.stored)
    }

    @Test
    fun `quickConnect maps network errors while polling`() = runTest {
        dataSource.onPoll = { throw TimeoutException("offline") }

        val states = repository.quickConnect(SERVER).toList()

        assertEquals(QuickConnectState.Failed(SignInError.ServerUnreachable), states.last())
    }

    @Test
    fun `quickConnect stops polling when collection is cancelled`() = runTest {
        dataSource.onPoll = { QuickConnectTicket(CODE, SECRET, authenticated = false) }
        val states = mutableListOf<QuickConnectState>()

        val job = launch { repository.quickConnect(SERVER).collect { states += it } }
        advanceTimeBy(12.seconds)
        runCurrent()
        job.cancel()
        val pollsAtCancel = dataSource.pollCount
        advanceTimeBy(60.seconds)

        assertEquals(2, pollsAtCancel)
        assertEquals(pollsAtCancel, dataSource.pollCount)
        assertEquals(QuickConnectState.WaitingForApproval(CODE), states.last())
        assertNull(store.stored)
    }

    // endregion

    // region Sign out

    private suspend fun signedIn(): Session {
        val session = Session(SERVER, ADMIN.id, "stored-token")
        store.stored = session
        repository.restoreSession()
        return session
    }

    @Test
    fun `signOut ends the server session, deletes the token and remembers the server`() = runTest {
        signedIn()

        repository.signOut()

        assertEquals(listOf("logout($SERVER, stored-token)"), dataSource.calls)
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(lastServerAddress = SERVER), repository.sessionState.value)
    }

    @Test
    fun `signOut still deletes the token when the server is unreachable`() = runTest {
        signedIn()
        dataSource.onLogout = { throw TimeoutException("offline") }

        repository.signOut()

        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(lastServerAddress = SERVER), repository.sessionState.value)
    }

    @Test
    fun `signOut does not wait forever for a hanging server`() = runTest {
        signedIn()
        dataSource.onLogout = { kotlinx.coroutines.awaitCancellation() }

        repository.signOut()

        assertEquals(5_000, currentTime)
        assertNull(store.stored)
        assertIs<SessionState.SignedOut>(repository.sessionState.value)
    }

    @Test
    fun `signOut does nothing when nobody is signed in`() = runTest {
        repository.restoreSession()
        repository.signOut()
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun `a user can sign in again after signing out`() = runTest {
        signedIn()
        repository.signOut()

        assertEquals(SignInResult.Success(ADMIN), repository.signIn(SERVER, "alice", "pw"))
        assertEquals(Session(SERVER, ADMIN.id, "token-password"), store.stored)
    }

    // endregion

    // region Current user

    @Test
    fun `currentUser fetches the profile with the stored token`() = runTest {
        store.stored = Session(SERVER, ADMIN.id, "stored-token")
        repository.restoreSession()

        assertEquals(SignInResult.Success(ADMIN), repository.currentUser())
        assertEquals(listOf("getCurrentUser(stored-token)"), dataSource.calls)
    }

    @Test
    fun `currentUser signs out when the token is rejected`() = runTest {
        store.stored = Session(SERVER, ADMIN.id, "revoked")
        repository.restoreSession()
        dataSource.onGetCurrentUser = { throw InvalidStatusException(401) }

        assertEquals(SignInResult.Failure(SignInError.SessionExpired), repository.currentUser())
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(lastServerAddress = SERVER), repository.sessionState.value)
    }

    @Test
    fun `currentUser keeps the session on network errors`() = runTest {
        val session = Session(SERVER, ADMIN.id, "stored-token")
        store.stored = session
        repository.restoreSession()
        dataSource.onGetCurrentUser = { throw TimeoutException("offline") }

        assertEquals(SignInResult.Failure(SignInError.ServerUnreachable), repository.currentUser())
        assertEquals(session, store.stored)
    }

    // endregion
}
