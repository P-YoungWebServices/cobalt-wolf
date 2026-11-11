package com.pyoungws.bluewolfplayer.shared.qbittorrent

import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.presentation.DownloadsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.DownloadsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class QBittorrentViewModelsTest {

    private val settingsStore = InMemoryQBittorrentSettingsStore()
    private lateinit var settingsRepository: QBittorrentSettingsRepository
    private lateinit var dataSource: FakeQBittorrentDataSource
    private lateinit var repository: QBittorrentRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        settingsRepository = QBittorrentSettingsRepository(settingsStore)
        dataSource = FakeQBittorrentDataSource()
        repository = QBittorrentRepository(dataSource, settingsRepository)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()


    private fun configure() = settingsRepository.update("qbit.example.com", "admin", "hunter2")

    /**
     * Creates the ViewModel; the caller must run [body] inside it so the never-ending
     * poll loop is cancelled even when an assertion fails (it would otherwise hang runTest).
     */
    private suspend fun withDownloadsViewModel(body: suspend (DownloadsViewModel) -> Unit) {
        val viewModel = DownloadsViewModel(repository)
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    // DownloadsViewModel

    @Test
    fun `the monitor asks for configuration when nothing is set up`() = runTest {
        withDownloadsViewModel { viewModel ->
            runCurrent()

            assertEquals(DownloadsUiState.NotConfigured, viewModel.uiState.value)
        }
    }

    @Test
    fun `the monitor lists torrents when configured`() = runTest {
        configure()
        dataSource.torrentsResult = listOf(torrent("a"))

        withDownloadsViewModel { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertIs<DownloadsUiState.Ready>(state)
            assertEquals(listOf("a"), state.downloads.torrents.map { it.hash })
            assertEquals(false, state.stale)
        }
    }

    @Test
    fun `the first poll failing surfaces as an error`() = runTest {
        configure()
        dataSource.error = IOException("down")

        withDownloadsViewModel { viewModel ->
            runCurrent()

            assertIs<DownloadsUiState.Error>(viewModel.uiState.value)
        }
    }

    @Test
    fun `a later poll failing keeps the last data and marks it stale`() = runTest {
        configure()
        dataSource.torrentsResult = listOf(torrent("a"))

        withDownloadsViewModel { viewModel ->
            runCurrent()

            dataSource.error = IOException("down")
            viewModel.refresh()
            runCurrent()

            val state = viewModel.uiState.value
            assertIs<DownloadsUiState.Ready>(state)
            assertEquals(listOf("a"), state.downloads.torrents.map { it.hash })
            assertEquals(true, state.stale)
        }
    }

    @Test
    fun `clearing the settings returns to the not-configured state`() = runTest {
        configure()

        withDownloadsViewModel { viewModel ->
            runCurrent()

            settingsRepository.update("", "", "")
            runCurrent()

            assertEquals(DownloadsUiState.NotConfigured, viewModel.uiState.value)
        }
    }

    // QBittorrentSettingsViewModel

    @Test
    fun `the form starts with the saved values`() = runTest {
        settingsRepository.update("qbit.example.com", "admin", "hunter2")

        val viewModel = QBittorrentSettingsViewModel(repository)

        assertEquals("qbit.example.com", viewModel.uiState.value.serverUrl)
        assertEquals("admin", viewModel.uiState.value.username)
        assertEquals("hunter2", viewModel.uiState.value.password)
    }

    @Test
    fun `saving persists the form`() = runTest {
        val viewModel = QBittorrentSettingsViewModel(repository)
        viewModel.onServerUrlChange("qbit.lan")
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("hunter2")

        viewModel.save()

        assertEquals("qbit.lan", settingsRepository.settings.value.serverUrl)
        assertEquals("admin", settingsRepository.settings.value.username)
        assertEquals(true, viewModel.uiState.value.saved)
    }

    @Test
    fun `testing the form reports success and failure`() = runTest {
        val viewModel = QBittorrentSettingsViewModel(repository)
        viewModel.onServerUrlChange("qbit.lan")
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("good")

        viewModel.test()
        runCurrent()

        assertEquals(true, viewModel.uiState.value.testSucceeded)

        dataSource.loginResult = false
        viewModel.onPasswordChange("bad")
        viewModel.test()
        runCurrent()

        assertEquals(false, viewModel.uiState.value.testSucceeded)
        assertIs<LoadError.Unexpected>(viewModel.uiState.value.testError)
    }

    @Test
    fun `editing a field clears the previous test result`() = runTest {
        val viewModel = QBittorrentSettingsViewModel(repository)
        viewModel.onServerUrlChange("qbit.lan")
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("good")
        viewModel.test()
        runCurrent()

        viewModel.onUsernameChange("other")

        assertNull(viewModel.uiState.value.testSucceeded)
    }
}
