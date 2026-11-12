package com.pyoungws.bluewolfplayer.shared.prowlarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.arrs.InMemoryArrSettingsStore
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProwlarrRepositoryTest {

    private val dataSource = FakeProwlarrDataSource()
    private val settingsRepository = ArrSettingsRepository(InMemoryArrSettingsStore())

    private fun repository() = ProwlarrRepository(dataSource, settingsRepository)

    private fun configure() = settingsRepository.update(ArrService.Prowlarr, "https://prowlarr.example.com", "key")

    @Test
    fun `unconfigured until url and api key are set`() {
        assertFalse(repository().isConfigured)
        configure()
        assertTrue(repository().isConfigured)
    }

    @Test
    fun `overview fails cleanly when the service is not configured`() = runTest {
        val result = repository().overview()

        val failure = assertIs<Outcome.Failure>(result)
        assertIs<LoadError.Unexpected>(failure.error)
    }

    @Test
    fun `overview merges indexers with their stats and disable state`() = runTest {
        configure()
        dataSource.indexers = listOf(
            ProwlarrIndexer(1, "IndexOne", "torrent", true),
            ProwlarrIndexer(2, "IndexTwo", "usenet", true),
        )
        dataSource.stats = mapOf(1 to IndexerStats(queries = 40, grabs = 5, averageResponseMs = 123))
        dataSource.disabled = mapOf(2 to "2026-11-12T00:00:00Z")
        dataSource.healthIssues = listOf(ProwlarrHealthIssue("IndexerLongTermStatusCheck", "warning", "down"))

        val overview = assertIs<Outcome.Success<ProwlarrOverview>>(repository().overview()).value

        val first = overview.indexers[0]
        assertEquals("IndexOne", first.name)
        assertEquals(40, first.stats?.queries)
        assertEquals(123, first.stats?.averageResponseMs)
        assertNull(first.disabledUntil)

        val second = overview.indexers[1]
        assertNull(second.stats)
        assertEquals("2026-11-12T00:00:00Z", second.disabledUntil)

        assertEquals(1, overview.health.size)
        assertEquals("down", overview.health.single().message)
    }

    @Test
    fun `a rejected api key surfaces as a failure`() = runTest {
        configure()
        dataSource.error = InvalidStatusException(401)

        assertIs<Outcome.Failure>(repository().overview())
    }

    @Test
    fun `testIndexer succeeds when the server reports no problems`() = runTest {
        configure()
        dataSource.testProblems = emptyList()

        assertIs<Outcome.Success<Unit>>(repository().testIndexer(1))
    }

    @Test
    fun `testIndexer fails with the validation messages`() = runTest {
        configure()
        dataSource.testProblems = listOf("Unable to connect to indexer", "Certificate expired")

        val failure = assertIs<Outcome.Failure>(repository().testIndexer(1))

        val error = assertIs<LoadError.Unexpected>(failure.error)
        assertEquals("Unable to connect to indexer; Certificate expired", error.message)
    }

    @Test
    fun `testAllIndexers calls the sweep endpoint`() = runTest {
        configure()

        assertIs<Outcome.Success<Unit>>(repository().testAllIndexers())
        assertEquals(1, dataSource.testAllCalls)
    }
}
