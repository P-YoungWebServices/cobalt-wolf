package com.pyoungws.cobaltwolf.shared.arrs

import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The repository resolves the saved connection per service and maps errors to [Outcome]. */
class ArrMediaRepositoryTest {
    private val settingsRepository = ArrSettingsRepository(InMemoryArrSettingsStore())
    private val dataSource = FakeArrMediaDataSource()
    private val repository = ArrMediaRepository(dataSource, settingsRepository)

    private fun configure(service: ArrService = ArrService.Radarr) =
        settingsRepository.update(service, "${service.name.lowercase()}.example.com", "key")

    @Test
    fun `an unconfigured service fails instead of calling the api`() = runTest {
        val outcome = repository.movies()

        assertIs<Outcome.Failure>(outcome)
        assertIs<LoadError.Unexpected>(outcome.error)
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun `movies returns the tracked movies once Radarr is configured`() = runTest {
        configure()
        dataSource.movieItems = listOf(arrMovie(id = 5))

        val outcome = repository.movies()

        assertIs<Outcome.Success<List<ArrMovie>>>(outcome)
        assertEquals(5, outcome.value.single().id)
        assertEquals(listOf("movies()"), dataSource.calls)
    }

    @Test
    fun `an api failure maps to a load error`() = runTest {
        configure()
        dataSource.error = IOException("down")

        val outcome = repository.movies()

        assertIs<Outcome.Failure>(outcome)
        assertEquals(LoadError.ServerUnreachable, outcome.error)
    }

    @Test
    fun `deleteItem forwards the deleteFiles choice`() = runTest {
        configure()

        assertIs<Outcome.Success<*>>(repository.deleteItem(ArrService.Radarr, 5, deleteFiles = true))

        assertTrue("deleteItem(Radarr,5,true)" in dataSource.calls)
    }

    @Test
    fun `movie operations never touch Sonarr and vice versa`() = runTest {
        configure(ArrService.Radarr)

        // Sonarr is not configured, so a series call fails without hitting the api.
        assertIs<Outcome.Failure>(repository.seriesList())
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun `automaticSearch resolves the service from the target type`() = runTest {
        configure(ArrService.Radarr)
        configure(ArrService.Sonarr)

        assertIs<Outcome.Success<*>>(repository.automaticSearch(SearchTarget.Movie(5)))
        assertIs<Outcome.Success<*>>(repository.automaticSearch(SearchTarget.Episodes(listOf(1))))

        assertTrue("automaticSearch(Movie(movieId=5))" in dataSource.calls)
        assertTrue("automaticSearch(Episodes(episodeIds=[1]))" in dataSource.calls)
    }
}
