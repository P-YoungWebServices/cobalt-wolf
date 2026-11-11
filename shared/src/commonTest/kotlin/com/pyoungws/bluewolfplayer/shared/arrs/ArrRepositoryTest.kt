package com.pyoungws.bluewolfplayer.shared.arrs

import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArrRepositoryTest {

    private fun repository(
        dataSource: FakeArrDataSource,
        settings: ArrSettings = FULLY_CONFIGURED,
    ) = ArrRepository(
        dataSource,
        ArrSettingsRepository(InMemoryArrSettingsStore(settings)),
        clock = fixedClock("2026-11-10T12:00:00Z"),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun `the calendar merges both services sorted by date`() = runTest {
        val dataSource = FakeArrDataSource().apply {
            items = mapOf(
                ArrService.Radarr to listOf(
                    upcoming(ArrService.Radarr, "Movie C", LocalDate(2026, 11, 20)),
                    upcoming(ArrService.Radarr, "Movie A", LocalDate(2026, 11, 11)),
                ),
                ArrService.Sonarr to listOf(upcoming(ArrService.Sonarr, "Show", LocalDate(2026, 11, 15))),
            )
        }

        val result = repository(dataSource).calendar()

        val calendar = (result as Outcome.Success).value
        assertEquals(listOf("Movie A", "Show", "Movie C"), calendar.items.map { it.title })
        assertEquals(emptyList(), calendar.failedServices)
        assertEquals(listOf("calendar(Radarr)", "calendar(Sonarr)"), dataSource.calls.sorted())
    }

    @Test
    fun `the window covers yesterday through the next 30 days`() = runTest {
        val dataSource = FakeArrDataSource()

        repository(dataSource).calendar()

        assertEquals(LocalDate(2026, 11, 9) to LocalDate(2026, 12, 10), dataSource.windows[ArrService.Radarr])
        assertEquals(LocalDate(2026, 11, 9) to LocalDate(2026, 12, 10), dataSource.windows[ArrService.Sonarr])
    }

    @Test
    fun `a failing service does not hide the other's entries`() = runTest {
        val dataSource = FakeArrDataSource().apply {
            items = mapOf(ArrService.Sonarr to listOf(upcoming(ArrService.Sonarr, "Show")))
            errors = mapOf(ArrService.Radarr to IOException("down"))
        }

        val calendar = (repository(dataSource).calendar() as Outcome.Success).value

        assertEquals(listOf("Show"), calendar.items.map { it.title })
        assertEquals(listOf(ArrService.Radarr), calendar.failedServices)
    }

    @Test
    fun `the call fails when every configured service fails`() = runTest {
        val dataSource = FakeArrDataSource().apply {
            errors = mapOf(
                ArrService.Radarr to IOException("radarr down"),
                ArrService.Sonarr to IOException("sonarr down"),
            )
        }

        assertIs<Outcome.Failure>(repository(dataSource).calendar())
    }

    @Test
    fun `unconfigured services are skipped entirely`() = runTest {
        val dataSource = FakeArrDataSource().apply {
            items = mapOf(ArrService.Sonarr to listOf(upcoming(ArrService.Sonarr, "Show")))
        }
        val settings = ArrSettings(sonarr = ArrServiceSettings("sonarr.example.com", "key"))

        val calendar = (repository(dataSource, settings).calendar() as Outcome.Success).value

        assertEquals(listOf("calendar(Sonarr)"), dataSource.calls)
        assertEquals(listOf("Show"), calendar.items.map { it.title })
    }

    @Test
    fun `nothing configured returns an empty calendar without calls`() = runTest {
        val dataSource = FakeArrDataSource()

        val calendar = (repository(dataSource, ArrSettings()).calendar() as Outcome.Success).value

        assertEquals(emptyList(), calendar.items)
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun `testConnection validates input before calling the service`() = runTest {
        val dataSource = FakeArrDataSource()
        val repository = repository(dataSource)

        assertIs<Outcome.Failure>(repository.testConnection(ArrService.Radarr, "not a host", "key"))
        assertIs<Outcome.Failure>(repository.testConnection(ArrService.Radarr, "radarr.example.com", "  "))
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun `testConnection normalizes the url and reports success`() = runTest {
        val dataSource = FakeArrDataSource()
        val repository = repository(dataSource)

        assertIs<Outcome.Success<Unit>>(repository.testConnection(ArrService.Sonarr, "sonarr.example.com:8989", " k "))
        assertEquals(listOf("status(Sonarr,https://sonarr.example.com:8989)"), dataSource.calls)
    }

    private companion object {
        val FULLY_CONFIGURED = ArrSettings(
            radarr = ArrServiceSettings("radarr.example.com", "key-r"),
            sonarr = ArrServiceSettings("sonarr.example.com", "key-s"),
        )
    }
}
