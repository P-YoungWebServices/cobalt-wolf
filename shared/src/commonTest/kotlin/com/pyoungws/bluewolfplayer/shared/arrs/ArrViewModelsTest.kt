package com.pyoungws.bluewolfplayer.shared.arrs

import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.presentation.ArrSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarUiState
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ArrViewModelsTest {

    private val settingsStore = InMemoryArrSettingsStore()
    private lateinit var settingsRepository: ArrSettingsRepository
    private lateinit var dataSource: FakeArrDataSource
    private lateinit var repository: ArrRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        settingsRepository = ArrSettingsRepository(settingsStore)
        dataSource = FakeArrDataSource()
        repository = ArrRepository(dataSource, settingsRepository, fixedClock(), TimeZone.UTC)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun configure(service: ArrService = ArrService.Radarr) =
        settingsRepository.update(service, "${service.name.lowercase()}.example.com", "key")

    // CalendarViewModel

    @Test
    fun `the calendar asks for configuration when no service is set up`() = runTest {
        val viewModel = CalendarViewModel(repository)
        runCurrent()

        assertEquals(CalendarUiState.NotConfigured, viewModel.uiState.value)
    }

    @Test
    fun `the calendar lists merged items when configured`() = runTest {
        configure()
        dataSource.items = mapOf(ArrService.Radarr to listOf(upcoming(ArrService.Radarr, "Movie")))

        val viewModel = CalendarViewModel(repository)
        runCurrent()

        val state = viewModel.uiState.value
        assertIs<CalendarUiState.Ready>(state)
        assertEquals(listOf("Movie"), state.items.map { it.title })
        assertEquals(emptyList(), state.failedServices)
    }

    @Test
    fun `a total failure surfaces as an error state`() = runTest {
        configure()
        dataSource.errors = mapOf(ArrService.Radarr to IOException("down"))

        val viewModel = CalendarViewModel(repository)
        runCurrent()

        assertIs<CalendarUiState.Error>(viewModel.uiState.value)
    }

    @Test
    fun `changing the settings reloads the calendar`() = runTest {
        configure()
        val viewModel = CalendarViewModel(repository)
        runCurrent()
        dataSource.items = mapOf(ArrService.Radarr to listOf(upcoming(ArrService.Radarr, "Movie")))

        settingsRepository.update(ArrService.Radarr, "radarr.example.com", "new-key")
        runCurrent()

        val state = viewModel.uiState.value
        assertIs<CalendarUiState.Ready>(state)
        assertEquals(listOf("Movie"), state.items.map { it.title })
    }

    @Test
    fun `removing the last service returns to the not-configured state`() = runTest {
        configure()
        val viewModel = CalendarViewModel(repository)
        runCurrent()

        settingsRepository.update(ArrService.Radarr, "", "")
        runCurrent()

        assertEquals(CalendarUiState.NotConfigured, viewModel.uiState.value)
    }

    // ArrSettingsViewModel

    @Test
    fun `the settings form starts with the saved values`() = runTest {
        settingsRepository.update(ArrService.Radarr, "radarr.example.com", "key")

        val viewModel = ArrSettingsViewModel(repository)

        assertEquals("radarr.example.com", viewModel.uiState.value.radarr.serverUrl)
        assertEquals("key", viewModel.uiState.value.radarr.apiKey)
        assertEquals("", viewModel.uiState.value.sonarr.serverUrl)
    }

    @Test
    fun `saving a form persists the values`() = runTest {
        val viewModel = ArrSettingsViewModel(repository)
        viewModel.onServerUrlChange(ArrService.Sonarr, "sonarr.example.com")
        viewModel.onApiKeyChange(ArrService.Sonarr, "abc")

        viewModel.save(ArrService.Sonarr)

        assertEquals("sonarr.example.com", settingsRepository.settings.value.sonarr.serverUrl)
        assertEquals("abc", settingsRepository.settings.value.sonarr.apiKey)
        assertEquals(true, viewModel.uiState.value.sonarr.saved)
    }

    @Test
    fun `testing a form reports success and failure`() = runTest {
        val viewModel = ArrSettingsViewModel(repository)
        viewModel.onServerUrlChange(ArrService.Radarr, "radarr.example.com")
        viewModel.onApiKeyChange(ArrService.Radarr, "good")

        viewModel.test(ArrService.Radarr)
        runCurrent()

        assertEquals(true, viewModel.uiState.value.radarr.testSucceeded)

        dataSource.errors = mapOf(ArrService.Radarr to IOException("down"))
        viewModel.onApiKeyChange(ArrService.Radarr, "bad")
        viewModel.test(ArrService.Radarr)
        runCurrent()

        val form = viewModel.uiState.value.radarr
        assertEquals(false, form.testSucceeded)
        assertEquals(LoadError.ServerUnreachable, form.testError)
    }

    @Test
    fun `editing a field clears the previous test result`() = runTest {
        val viewModel = ArrSettingsViewModel(repository)
        viewModel.onServerUrlChange(ArrService.Radarr, "radarr.example.com")
        viewModel.onApiKeyChange(ArrService.Radarr, "k")
        viewModel.test(ArrService.Radarr)
        runCurrent()

        viewModel.onApiKeyChange(ArrService.Radarr, "k2")

        assertNull(viewModel.uiState.value.radarr.testSucceeded)
    }
}
