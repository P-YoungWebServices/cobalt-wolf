package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.library.SESSION
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminRepositoryTest {

    private fun repository(dataSource: FakeAdminDataSource, auth: AuthRepository): AdminRepository =
        AdminRepository(dataSource, SessionGuard(auth))

    @Test
    fun `dashboard combines active sessions and recently added`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            sessions = { listOf(playingSession("s1"), playingSession("s2", item = null)) }
            recentlyAdded = { listOf(movie("m1"), movie("m2")) }
        }
        val result = repository(dataSource, signedIn()).dashboard()

        val dashboard = (result as Outcome.Success).value
        assertEquals(listOf("s1", "s2"), dashboard.sessions.map { it.id })
        assertEquals(listOf("m1", "m2"), dashboard.recentlyAdded.map { it.id })
        assertEquals(listOf("sessions", "latest(16)"), dataSource.calls)
    }

    @Test
    fun `a revoked token signs the user out instead of showing data`() = runTest {
        val dataSource = FakeAdminDataSource().apply { sessions = { throw InvalidStatusException(401) } }
        val auth = signedIn()

        val result = repository(dataSource, auth).dashboard()

        assertEquals(Outcome.Failure(LoadError.SessionExpired), result)
        assertEquals(SessionState.SignedOut(lastServerAddress = SESSION.serverAddress), auth.sessionState.value)
    }

    @Test
    fun `sendMessage forwards the trimmed text to the session`() = runTest {
        val dataSource = FakeAdminDataSource()

        val result = repository(dataSource, signedIn()).sendMessage("s1", "  hello there  ")

        assertEquals(Outcome.Success(Unit), result)
        assertEquals(listOf("message(s1, hello there)"), dataSource.calls)
    }

    @Test
    fun `a blank message never reaches the server`() = runTest {
        val dataSource = FakeAdminDataSource()

        val result = repository(dataSource, signedIn()).sendMessage("s1", "   ")

        assertEquals(Outcome.Success(Unit), result)
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun `sendMessage reports server errors`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            onSendMessage = { _, _ -> throw InvalidStatusException(404) }
        }

        val result = repository(dataSource, signedIn()).sendMessage("gone", "hi")

        assertEquals(Outcome.Failure(LoadError.NotFound), result)
    }

    private suspend fun signedIn(): AuthRepository =
        AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }
}
