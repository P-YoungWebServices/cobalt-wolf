package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.library.FakeLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.library.MOVIES
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.episode
import com.pyoungws.bluewolfplayer.shared.library.movie
import com.pyoungws.bluewolfplayer.shared.library.season
import com.pyoungws.bluewolfplayer.shared.library.series
import com.pyoungws.bluewolfplayer.shared.library.signedInAuthRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Page
import com.pyoungws.bluewolfplayer.shared.seerr.FakeSeerrDataSource
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrSettings
import com.pyoungws.bluewolfplayer.shared.seerr.pageOf
import com.pyoungws.bluewolfplayer.shared.seerr.seerrMovie
import com.pyoungws.bluewolfplayer.shared.seerr.seerrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelsTest {
    private val dataSource = FakeLibraryDataSource()
    private val seerrDataSource = FakeSeerrDataSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun repository() = LibraryRepository(dataSource, SessionGuard(signedInAuthRepository()))

    private suspend fun TestScope.seerrRepo(settings: SeerrSettings = SeerrSettings(com.pyoungws.bluewolfplayer.shared.seerr.SEERR_URL)) =
        seerrRepository(seerrDataSource, signedInAuthRepository(), settings = settings, scope = TestScope(testScheduler))

    @Test
    fun `home loads the feed`() = runTest {
        dataSource.resume = { listOf(movie("m1", positionTicks = 10)) }
        dataSource.latest = { listOf(movie("m2")) }
        val viewModel = LibraryHomeViewModel(repository())
        assertEquals(LibraryHomeUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        val feed = (viewModel.uiState.value as LibraryHomeUiState.Loaded).feed
        assertEquals(listOf("m1"), feed.continueWatching.map { it.id })
        assertEquals(2, feed.recentlyAdded.size)
    }

    @Test
    fun `home keeps next up as its own row and drops items already resuming`() = runTest {
        dataSource.resume = { listOf(episode("e1", 1, positionTicks = 10)) }
        dataSource.nextUp = { listOf(episode("e1", 1, positionTicks = 10), episode("e2", 2)) }
        val viewModel = LibraryHomeViewModel(repository())
        advanceUntilIdle()

        val feed = (viewModel.uiState.value as LibraryHomeUiState.Loaded).feed
        assertEquals(listOf("e1"), feed.continueWatching.map { it.id })
        assertEquals(listOf("e2"), feed.nextUp.map { it.id })
    }

    @Test
    fun `home shows an error and can retry`() = runTest {
        var fail = true
        dataSource.libraries = { if (fail) throw TimeoutException() else listOf(MOVIES) }
        val viewModel = LibraryHomeViewModel(repository())
        advanceUntilIdle()
        assertEquals(LibraryHomeUiState.Error(LoadError.ServerUnreachable), viewModel.uiState.value)

        fail = false
        viewModel.load()
        advanceUntilIdle()

        assertIs<LibraryHomeUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun `a failed background refresh keeps the existing content`() = runTest {
        var fail = false
        dataSource.libraries = { if (fail) throw TimeoutException() else listOf(MOVIES) }
        val viewModel = LibraryHomeViewModel(repository())
        advanceUntilIdle()
        val loaded = viewModel.uiState.value

        fail = true
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(loaded, viewModel.uiState.value)
    }

    @Test
    fun `library loads pages until everything is shown`() = runTest {
        dataSource.libraryItems = { _, start, limit -> Page((start until minOf(start + limit, 5)).map { movie("m$it") }, 5) }
        val viewModel = LibraryViewModel(MOVIES, repository(), pageSize = 2)
        advanceUntilIdle()
        assertEquals(listOf("m0", "m1"), viewModel.uiState.value.items.map { it.id })
        assertTrue(viewModel.uiState.value.canLoadMore)

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.items.size)
        assertEquals(false, viewModel.uiState.value.canLoadMore)
        assertEquals(3, dataSource.calls.count { it.startsWith("items(") })
    }

    @Test
    fun `library error can be retried`() = runTest {
        var fail = true
        dataSource.libraryItems = { _, _, _ -> if (fail) throw TimeoutException() else Page(listOf(movie("m1")), 1) }
        val viewModel = LibraryViewModel(MOVIES, repository())
        advanceUntilIdle()
        assertEquals(LoadError.ServerUnreachable, viewModel.uiState.value.error)

        fail = false
        viewModel.retry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("m1"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun `movie detail loads the movie and plays it`() = runTest {
        val viewModel = ItemDetailViewModel("m1", repository())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals("m1", state.item.id)
        assertEquals("m1", state.playTarget?.id)
        assertTrue("seasons(m1)" !in dataSource.calls)
    }

    @Test
    fun `series detail loads seasons and the first real season's episodes`() = runTest {
        dataSource.item = { series(it) }
        dataSource.seasons = { listOf(season("sp", 0), season("s1", 1), season("s2", 2)) }
        dataSource.episodes = { _, seasonId -> listOf(episode("$seasonId-e1", 1, played = true), episode("$seasonId-e2", 2)) }
        val viewModel = ItemDetailViewModel("series-1", repository())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals("s1", state.selectedSeasonId)
        assertEquals(listOf("s1-e1", "s1-e2"), state.episodes.map { it.id })
        assertEquals("s1-e2", state.playTarget?.id)
    }

    @Test
    fun `selecting a season loads its episodes`() = runTest {
        dataSource.item = { series(it) }
        dataSource.seasons = { listOf(season("s1", 1), season("s2", 2)) }
        dataSource.episodes = { _, seasonId -> listOf(episode("$seasonId-e1", 1)) }
        val viewModel = ItemDetailViewModel("series-1", repository())
        advanceUntilIdle()

        viewModel.selectSeason("s2")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals("s2", state.selectedSeasonId)
        assertEquals(listOf("s2-e1"), state.episodes.map { it.id })
    }

    @Test
    fun `series play target prefers an episode in progress`() = runTest {
        dataSource.item = { series(it) }
        dataSource.seasons = { listOf(season("s1", 1)) }
        dataSource.episodes = { _, _ -> listOf(episode("e1", 1), episode("e2", 2, positionTicks = 5), episode("e3", 3)) }
        val viewModel = ItemDetailViewModel("series-1", repository())
        advanceUntilIdle()

        assertEquals("e2", (viewModel.uiState.value as ItemDetailUiState.Loaded).playTarget?.id)
    }

    @Test
    fun `episode failures do not discard the series`() = runTest {
        dataSource.item = { series(it) }
        dataSource.seasons = { listOf(season("s1", 1)) }
        dataSource.episodes = { _, _ -> throw TimeoutException() }
        val viewModel = ItemDetailViewModel("series-1", repository())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals(LoadError.ServerUnreachable, state.episodesError)
        assertEquals("series-1", state.item.id)
    }

    @Test
    fun `detail loads similar items for a movie`() = runTest {
        dataSource.similar = { listOf(movie("s1"), movie("s2")) }
        val viewModel = ItemDetailViewModel("m1", repository())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals(listOf("s1", "s2"), state.similar.map { it.id })
        assertTrue("similar(m1)" in dataSource.calls)
    }

    @Test
    fun `series detail loads next up and similar`() = runTest {
        dataSource.item = { series(it) }
        dataSource.seasons = { listOf(season("s1", 1)) }
        dataSource.episodes = { _, _ -> emptyList() }
        dataSource.nextUpForSeries = { episode("e3", 3, positionTicks = 5) }
        dataSource.similar = { listOf(series("other")) }
        val viewModel = ItemDetailViewModel("series-1", repository())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals("e3", state.nextUp?.id)
        assertEquals(listOf("other"), state.similar.map { it.id })
        assertTrue("nextUpSeries(series-1)" in dataSource.calls)
    }

    @Test
    fun `similar failure leaves the section empty without failing the page`() = runTest {
        dataSource.similar = { throw TimeoutException() }
        val viewModel = ItemDetailViewModel("m1", repository())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ItemDetailUiState.Loaded
        assertEquals("m1", state.item.id)
        assertTrue(state.similar.isEmpty())
    }

    @Test
    fun `detail shows an error when the item cannot be loaded`() = runTest {
        dataSource.item = { throw TimeoutException() }
        val viewModel = ItemDetailViewModel("m1", repository())
        advanceUntilIdle()

        assertEquals(ItemDetailUiState.Error(LoadError.ServerUnreachable), viewModel.uiState.value)
    }

    @Test
    fun `search debounces typing and only queries the final term`() = runTest {
        dataSource.search = { listOf(movie(it)) }
        val viewModel = SearchViewModel(repository(), seerrRepo())
        advanceUntilIdle()

        viewModel.onQueryChange("w")
        advanceTimeBy(100)
        viewModel.onQueryChange("wo")
        advanceTimeBy(100)
        viewModel.onQueryChange("wolf")
        advanceUntilIdle()

        assertEquals(listOf("search(wolf)"), dataSource.calls)
        assertEquals(listOf("wolf"), viewModel.uiState.value.results.map { it.id })
        assertEquals(false, viewModel.uiState.value.isSearching)
    }

    @Test
    fun `search reports no results and errors, and clears on an empty query`() = runTest {
        val viewModel = SearchViewModel(repository(), seerrRepo())
        advanceUntilIdle()

        viewModel.onQueryChange("zzz")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showNoResults)

        dataSource.search = { throw TimeoutException() }
        viewModel.onQueryChange("boom")
        advanceUntilIdle()
        assertEquals(LoadError.ServerUnreachable, viewModel.uiState.value.error)

        dataSource.search = { listOf(movie(it)) }
        viewModel.onQueryChange("ok")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.results.size)

        viewModel.onQueryChange("")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals(false, viewModel.uiState.value.showNoResults)
    }

    @Test
    fun `search shows local and seerr results together`() = runTest {
        dataSource.search = { listOf(movie(it)) }
        seerrDataSource.search = { pageOf(listOf(seerrMovie(7))) }
        val viewModel = SearchViewModel(repository(), seerrRepo())
        advanceUntilIdle()

        viewModel.onQueryChange("wolf")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertEquals(listOf(7), state.seerrResults.map { it.tmdbId })
        assertNull(state.seerrError)
        assertEquals(listOf("me", "search(wolf)"), seerrDataSource.calls)
    }

    @Test
    fun `search skips seerr when no server is configured`() = runTest {
        dataSource.search = { listOf(movie(it)) }
        val viewModel = SearchViewModel(repository(), seerrRepo(SeerrSettings()))
        advanceUntilIdle()

        viewModel.onQueryChange("wolf")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertTrue(state.seerrResults.isEmpty())
        assertNull(state.seerrError)
        assertTrue(seerrDataSource.calls.isEmpty())
    }
}
