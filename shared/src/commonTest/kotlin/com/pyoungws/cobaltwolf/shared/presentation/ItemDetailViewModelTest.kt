package com.pyoungws.cobaltwolf.shared.presentation

import com.pyoungws.cobaltwolf.shared.library.FakeLibraryDataSource
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.library.SessionGuard
import com.pyoungws.cobaltwolf.shared.library.episode
import com.pyoungws.cobaltwolf.shared.library.movie
import com.pyoungws.cobaltwolf.shared.library.season
import com.pyoungws.cobaltwolf.shared.library.signedInAuthRepository
import com.pyoungws.cobaltwolf.shared.model.MediaItem
import com.pyoungws.cobaltwolf.shared.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {
    private val library = FakeLibraryDataSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.repository(): LibraryRepository =
        LibraryRepository(library, SessionGuard(signedInAuthRepository()))

    private suspend fun TestScope.loaded(item: MediaItem): ItemDetailUiState.Loaded {
        library.item = { item }
        val viewModel = ItemDetailViewModel(item.id, repository())
        advanceUntilIdle()
        return assertIs<ItemDetailUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun `an episode page loads the sibling episodes of its season`() = runTest {
        val e1 = episode("e1", 1)
        val e2 = episode("e2", 2)
        val e3 = episode("e3", 3)
        library.episodes = { seriesId, seasonId ->
            if (seriesId == "series-1" && seasonId == "season-1") listOf(e1, e2, e3) else emptyList()
        }

        val state = loaded(e2)

        assertEquals(listOf("e1", "e2", "e3"), state.episodes.map { it.id })
        assertTrue("episodes(season-1)" in library.calls)
    }

    @Test
    fun `an episode page loads its parent's seasons and selects its own`() = runTest {
        library.seasons = { listOf(season("season-1", 1), season("season-2", 2)) }
        val e2 = episode("e2", 2, seasonNumber = 2)
        library.episodes = { _, seasonId -> if (seasonId == "season-2") listOf(e2) else emptyList() }

        val state = loaded(e2)

        assertEquals(listOf("season-1", "season-2"), state.seasons.map { it.id })
        assertEquals("season-2", state.selectedSeasonId)
        assertEquals(listOf("e2"), state.episodes.map { it.id })
    }

    @Test
    fun `selecting another season on an episode page loads it through the series id`() = runTest {
        library.item = { episode("e1", 1) }
        library.seasons = { listOf(season("season-1", 1), season("season-2", 2)) }
        val s2e1 = episode("s2e1", 1, seasonNumber = 2)
        library.episodes = { seriesId, seasonId ->
            if (seriesId == "series-1" && seasonId == "season-2") listOf(s2e1) else emptyList()
        }
        val viewModel = ItemDetailViewModel("e1", repository())
        advanceUntilIdle()

        viewModel.selectSeason("season-2")
        advanceUntilIdle()

        val state = assertIs<ItemDetailUiState.Loaded>(viewModel.uiState.value)
        assertEquals("season-2", state.selectedSeasonId)
        assertEquals(listOf("s2e1"), state.episodes.map { it.id })
    }

    @Test
    fun `an episode without series or season id loads no siblings`() = runTest {
        val orphan = MediaItem("e0", "e0", MediaKind.Episode)

        val state = loaded(orphan)

        assertEquals(emptyList(), state.episodes)
        assertTrue(library.calls.none { it.startsWith("episodes(") })
    }

    @Test
    fun `a movie page does not fetch season episodes`() = runTest {
        val state = loaded(movie("m1"))

        assertEquals(emptyList(), state.episodes)
        assertTrue(library.calls.none { it.startsWith("episodes(") })
    }
}
