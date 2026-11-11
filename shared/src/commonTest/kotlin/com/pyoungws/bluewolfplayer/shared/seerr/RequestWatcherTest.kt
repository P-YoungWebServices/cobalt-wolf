package com.pyoungws.bluewolfplayer.shared.seerr

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.library.SESSION
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class RequestWatcherTest {
    private val dataSource = FakeSeerrDataSource()
    private val events = mutableListOf<RequestEvent>()

    private suspend fun signedIn() = AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }

    private fun adminAccount() {
        dataSource.currentUser = { SEERR_ACCOUNT.copy(isAdmin = true) }
    }

    /**
     * A started watcher on the background scope, after the repository restored the session.
     * The poll loop is driven manually: [runCurrent] runs the first poll, each
     * [advanceTimeBy] + [runCurrent] pair runs the next one.
     */
    private suspend fun TestScope.watcher(): RequestWatcher {
        val repository = seerrRepository(dataSource, signedIn(), scope = TestScope(testScheduler))
        advanceUntilIdle()
        val watcher = RequestWatcher(repository, backgroundScope, POLL)
        backgroundScope.launch { watcher.events.collect { events += it } }
        watcher.start()
        return watcher
    }

    private suspend fun TestScope.nextPoll() {
        advanceTimeBy(POLL)
        runCurrent()
    }

    @Test
    fun `the first poll only seeds the baseline`() = runTest {
        adminAccount()
        dataSource.requests = { filter ->
            if (filter == SeerrRequestFilter.Pending) {
                pageOf(listOf(seerrRequest(9, tmdbId = 90, requestedById = 8, requestedByName = "bob")))
            } else {
                pageOf(listOf(seerrRequest(1, tmdbId = 10, status = SeerrRequestStatus.Approved, mediaStatus = SeerrMediaStatus.Processing)))
            }
        }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, mediaStatus = SeerrMediaStatus.Processing)) }
        watcher()
        runCurrent()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `an admin is told about a new pending request but not their own`() = runTest {
        adminAccount()
        var pending = listOf(seerrRequest(8, tmdbId = 80, requestedById = 8))
        dataSource.requests = { filter -> pageOf(if (filter == SeerrRequestFilter.Pending) pending else emptyList()) }
        watcher()
        runCurrent()

        pending = pending + listOf(
            seerrRequest(9, tmdbId = 90, requestedById = 8, requestedByName = "bob"),
            // The admin's own request: they know about it already.
            seerrRequest(10, tmdbId = 100, requestedById = SEERR_ACCOUNT.id),
        )
        nextPoll()

        val notified = events.filterIsInstance<RequestEvent.NewPending>()
        assertEquals(listOf(9), notified.map { it.request.id })
        assertEquals("bob", notified.single().request.requestedByName)
    }

    @Test
    fun `a viewer never queries the pending queue`() = runTest {
        // SEERR_ACCOUNT is not an admin.
        dataSource.requests = { pageOf(emptyList()) }
        watcher()
        runCurrent()
        nextPoll()

        assertFalse(dataSource.calls.any { it == "requests(Pending)" })
        assertTrue(dataSource.calls.any { it == "requests(All)" })
    }

    @Test
    fun `an own request moving to approved notifies`() = runTest {
        var own = listOf(seerrRequest(1, tmdbId = 10, status = SeerrRequestStatus.Pending))
        dataSource.requests = { pageOf(own) }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, mediaStatus = SeerrMediaStatus.Processing)) }
        watcher()
        runCurrent()

        own = listOf(seerrRequest(1, tmdbId = 10, status = SeerrRequestStatus.Approved))
        nextPoll()

        assertEquals(listOf(1), events.filterIsInstance<RequestEvent.Approved>().map { it.request.id })
    }

    @Test
    fun `an own request becoming available notifies`() = runTest {
        var available = false
        dataSource.requests = { pageOf(listOf(seerrRequest(1, tmdbId = 10, status = SeerrRequestStatus.Approved))) }
        dataSource.movieDetails = {
            SeerrTitleDetails(seerrMovie(it, mediaStatus = if (available) SeerrMediaStatus.Available else SeerrMediaStatus.Processing))
        }
        watcher()
        runCurrent()

        available = true
        nextPoll()

        assertEquals(listOf(1), events.filterIsInstance<RequestEvent.Available>().map { it.request.id })
    }

    @Test
    fun `a request seen for the first time is silent while pending and reports when approved`() = runTest {
        var own = emptyList<SeerrRequest>()
        dataSource.requests = { pageOf(own) }
        dataSource.movieDetails = { SeerrTitleDetails(seerrMovie(it, mediaStatus = SeerrMediaStatus.Processing)) }
        watcher()
        runCurrent()

        // The user created it on another device; still pending.
        own = listOf(seerrRequest(2, tmdbId = 20, status = SeerrRequestStatus.Pending))
        nextPoll()
        assertTrue(events.isEmpty())

        own = listOf(seerrRequest(2, tmdbId = 20, status = SeerrRequestStatus.Approved))
        nextPoll()
        assertEquals(listOf(2), events.filterIsInstance<RequestEvent.Approved>().map { it.request.id })
    }

    @Test
    fun `a failed poll keeps the baseline so recovered polls do not repeat events`() = runTest {
        adminAccount()
        var fail = false
        var pending = listOf(seerrRequest(9, tmdbId = 90, requestedById = 8))
        dataSource.requests = { filter ->
            if (fail) throw IOException("down")
            pageOf(if (filter == SeerrRequestFilter.Pending) pending else emptyList())
        }
        watcher()
        runCurrent()

        fail = true
        nextPoll()
        fail = false
        pending = pending + seerrRequest(10, tmdbId = 100, requestedById = 8)
        nextPoll()

        // Only the genuinely new request is reported; the known one is not repeated.
        assertEquals(listOf(10), events.filterIsInstance<RequestEvent.NewPending>().map { it.request.id })
    }

    private companion object {
        val POLL = 1.minutes
    }
}
