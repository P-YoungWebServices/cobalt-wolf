package com.pyoungws.cobaltwolf.shared.library

import com.pyoungws.cobaltwolf.shared.auth.SessionState
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.model.Page
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LibraryRepositoryTest {
    private val dataSource = FakeLibraryDataSource()

    private suspend fun repository(): Pair<LibraryRepository, com.pyoungws.cobaltwolf.shared.auth.AuthRepository> {
        val auth = signedInAuthRepository()
        return LibraryRepository(dataSource, SessionGuard(auth)) to auth
    }

    @Test
    fun `home combines libraries, continue watching, next up and a recently added row per browsable library`() = runTest {
        dataSource.resume = { listOf(movie("m1", positionTicks = 100)) }
        dataSource.nextUp = { listOf(episode("e1", 2)) }
        dataSource.latest = { if (it == MOVIES.id) listOf(movie("m2")) else listOf(series("s1")) }
        val (repository) = repository()

        val feed = (repository.loadHome() as Outcome.Success).value

        assertEquals(listOf(MOVIES, SHOWS, MUSIC), feed.libraries)
        assertEquals(listOf("m1"), feed.continueWatching.map { it.id })
        assertEquals(listOf("e1"), feed.nextUp.map { it.id })
        assertEquals(listOf(MOVIES, SHOWS), feed.recentlyAdded.map { it.library })
        assertEquals(listOf("m2"), feed.recentlyAdded[0].items.map { it.id })
        assertTrue("latest(${MUSIC.id})" !in dataSource.calls)
    }

    @Test
    fun `home removes next-up items already in resume and drops empty rows`() = runTest {
        dataSource.resume = { listOf(episode("e1", 1, positionTicks = 5)) }
        dataSource.nextUp = { listOf(episode("e1", 1), episode("e2", 2)) }
        dataSource.latest = { if (it == SHOWS.id) listOf(series("s1")) else emptyList() }
        val (repository) = repository()

        val feed = (repository.loadHome() as Outcome.Success).value

        assertEquals(listOf("e1"), feed.continueWatching.map { it.id })
        assertEquals(listOf("e2"), feed.nextUp.map { it.id })
        assertEquals(listOf(SHOWS), feed.recentlyAdded.map { it.library })
    }

    @Test
    fun `an unauthorized response invalidates the session`() = runTest {
        dataSource.libraries = { throw InvalidStatusException(401) }
        val (repository, auth) = repository()

        val result = repository.loadHome()

        assertEquals(Outcome.Failure(LoadError.SessionExpired), result)
        assertEquals(SessionState.SignedOut(lastServerAddress = com.pyoungws.cobaltwolf.shared.auth.SERVER), auth.sessionState.value)
    }

    @Test
    fun `without a session every call fails as expired`() = runTest {
        val auth = com.pyoungws.cobaltwolf.shared.auth.AuthRepository(
            com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource(),
            com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore(),
        ).apply { restoreSession() }

        assertEquals(Outcome.Failure(LoadError.SessionExpired), LibraryRepository(dataSource, SessionGuard(auth)).item("x"))
    }

    @Test
    fun `failures are mapped to load errors`() = runTest {
        val (repository) = repository()

        dataSource.item = { throw InvalidStatusException(404) }
        assertEquals(Outcome.Failure(LoadError.NotFound), repository.item("x"))

        dataSource.item = { throw TimeoutException() }
        assertEquals(Outcome.Failure(LoadError.ServerUnreachable), repository.item("x"))

        dataSource.item = { throw SecureConnectionException() }
        assertEquals(Outcome.Failure(LoadError.SecureConnectionFailed), repository.item("x"))

        dataSource.item = { throw InvalidStatusException(500) }
        assertIs<LoadError.Unexpected>((repository.item("x") as Outcome.Failure).error)
    }

    @Test
    fun `library items are paged`() = runTest {
        dataSource.libraryItems = { _, start, _ -> Page(listOf(movie("m$start")), 5) }
        val (repository) = repository()

        val page = (repository.libraryItems(MOVIES, 2, 60) as Outcome.Success).value

        assertEquals(listOf("m2"), page.items.map { it.id })
        assertEquals(5, page.totalCount)
    }

    @Test
    fun `blank searches do not call the server`() = runTest {
        val (repository) = repository()

        assertEquals(Outcome.Success(emptyList()), repository.search("   "))
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun `search trims the term`() = runTest {
        dataSource.search = { listOf(movie(it)) }
        val (repository) = repository()

        val result = (repository.search("  wolf ") as Outcome.Success).value

        assertEquals(listOf("wolf"), result.map { it.id })
    }
}
