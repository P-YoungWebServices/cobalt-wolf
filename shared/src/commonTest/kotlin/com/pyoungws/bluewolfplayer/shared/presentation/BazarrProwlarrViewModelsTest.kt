package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.arrs.InMemoryArrSettingsStore
import com.pyoungws.bluewolfplayer.shared.bazarr.BazarrRepository
import com.pyoungws.bluewolfplayer.shared.bazarr.FakeBazarrDataSource
import com.pyoungws.bluewolfplayer.shared.bazarr.MissingSubtitle
import com.pyoungws.bluewolfplayer.shared.bazarr.WantedMovie
import com.pyoungws.bluewolfplayer.shared.prowlarr.FakeProwlarrDataSource
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrIndexer
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BazarrProwlarrViewModelsTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun settingsRepository() = ArrSettingsRepository(InMemoryArrSettingsStore())

    @Test
    fun `bazarr reports not configured until the service has credentials`() = runTest {
        val settings = settingsRepository()
        val viewModel = BazarrViewModel(BazarrRepository(FakeBazarrDataSource(), settings))
        try {
            runCurrent()
            assertEquals(BazarrUiState.NotConfigured, viewModel.uiState.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `bazarr loads the wanted list once configured`() = runTest {
        val dataSource = FakeBazarrDataSource().apply {
            movies = listOf(WantedMovie("Film", listOf(MissingSubtitle("English"))))
        }
        val settings = settingsRepository().apply {
            update(ArrService.Bazarr, "https://bazarr.example.com", "key")
        }
        val viewModel = BazarrViewModel(BazarrRepository(dataSource, settings))
        try {
            runCurrent()

            val state = assertIs<BazarrUiState.Ready>(viewModel.uiState.value)
            assertEquals(1, state.wanted.total)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `a failed bazarr reload keeps the list marked stale`() = runTest {
        var fail = false
        val dataSource = FakeBazarrDataSource().apply {
            movies = listOf(WantedMovie("Film", emptyList()))
        }
        val settings = settingsRepository().apply {
            update(ArrService.Bazarr, "https://bazarr.example.com", "key")
        }
        val viewModel = BazarrViewModel(BazarrRepository(dataSource, settings))
        try {
            runCurrent()

            dataSource.error = InvalidStatusException(500).also { fail = true }
            viewModel.refresh()
            runCurrent()

            val state = assertIs<BazarrUiState.Ready>(viewModel.uiState.value)
            assertTrue(state.stale)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `prowlarr reports not configured until the service has credentials`() = runTest {
        val viewModel = ProwlarrViewModel(ProwlarrRepository(FakeProwlarrDataSource(), settingsRepository()))
        try {
            runCurrent()
            assertEquals(ProwlarrUiState.NotConfigured, viewModel.uiState.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `prowlarr loads the indexer overview once configured`() = runTest {
        val dataSource = FakeProwlarrDataSource().apply {
            indexers = listOf(ProwlarrIndexer(1, "IndexOne", "torrent", true))
        }
        val settings = settingsRepository().apply {
            update(ArrService.Prowlarr, "https://prowlarr.example.com", "key")
        }
        val viewModel = ProwlarrViewModel(ProwlarrRepository(dataSource, settings))
        try {
            runCurrent()

            val state = assertIs<ProwlarrUiState.Ready>(viewModel.uiState.value)
            assertEquals("IndexOne", state.overview.indexers.single().name)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `a bazarr manual search downloads and reports the count`() = runTest {
        val dataSource = FakeBazarrDataSource().apply {
            movies = listOf(WantedMovie("Film", listOf(MissingSubtitle("English")), radarrId = 42))
            movieCandidates = listOf(
                com.pyoungws.bluewolfplayer.shared.bazarr.SubtitleCandidate("opensubtitles", "sub-1", 90, "English"),
            )
        }
        val settings = settingsRepository().apply {
            update(ArrService.Bazarr, "https://bazarr.example.com", "key")
        }
        val viewModel = BazarrViewModel(BazarrRepository(dataSource, settings))
        try {
            runCurrent()

            val movie = (viewModel.uiState.value as BazarrUiState.Ready).wanted.movies.single()
            viewModel.search(movie)
            runCurrent()

            val status = viewModel.searches.value[viewModel.searchKey(movie)]
            assertEquals(BazarrSearchStatus.Downloaded(1), status)
            assertEquals(listOf("m:42:sub-1"), dataSource.downloads)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `a prowlarr indexer test reports the result`() = runTest {
        val dataSource = FakeProwlarrDataSource().apply {
            indexers = listOf(ProwlarrIndexer(1, "IndexOne", "torrent", true))
        }
        val settings = settingsRepository().apply {
            update(ArrService.Prowlarr, "https://prowlarr.example.com", "key")
        }
        val viewModel = ProwlarrViewModel(ProwlarrRepository(dataSource, settings))
        try {
            runCurrent()

            val indexer = (viewModel.uiState.value as ProwlarrUiState.Ready).overview.indexers.single()
            viewModel.test(indexer)
            runCurrent()

            assertEquals(ProwlarrTestStatus.Passed, viewModel.tests.value[1])
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `a failed prowlarr load surfaces an error`() = runTest {
        val dataSource = FakeProwlarrDataSource().apply { error = InvalidStatusException(500) }
        val settings = settingsRepository().apply {
            update(ArrService.Prowlarr, "https://prowlarr.example.com", "key")
        }
        val viewModel = ProwlarrViewModel(ProwlarrRepository(dataSource, settings))
        try {
            runCurrent()

            assertIs<ProwlarrUiState.Error>(viewModel.uiState.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
