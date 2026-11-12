package com.pyoungws.bluewolfplayer.shared.prowlarr

import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrServiceSettings
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettings
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.net.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Prowlarr indexer monitoring: the configured indexers enriched with their stats, disable
 * state and the server's current health issues. Shares the *arr settings store.
 */
class ProwlarrRepository(
    private val dataSource: ProwlarrDataSource,
    private val settingsRepository: ArrSettingsRepository,
) {
    val settings: StateFlow<ArrSettings> = settingsRepository.settings

    val isConfigured: Boolean get() = settingsRepository.serviceSettings(ArrService.Prowlarr) != null

    /** Indexers with stats and disable state, plus the current health issues. */
    suspend fun overview(): Outcome<ProwlarrOverview> = call { settings ->
        coroutineScope {
            val indexers = async { dataSource.indexers(settings) }
            val stats = async { dataSource.indexerStats(settings) }
            val disabled = async { dataSource.disabledIndexers(settings) }
            val health = async { dataSource.health(settings) }
            val statsById = stats.await()
            val disabledById = disabled.await()
            ProwlarrOverview(
                indexers = indexers.await().map { it.copy(disabledUntil = disabledById[it.id], stats = statsById[it.id]) },
                health = health.await(),
            )
        }
    }

    /**
     * Re-runs Prowlarr's connectivity test on one indexer. Fails with the validation messages
     * the server reports when the indexer does not pass.
     */
    suspend fun testIndexer(id: Int): Outcome<Unit> = call { settings ->
        val resource = dataSource.indexerResource(settings, id)
        val problems = dataSource.testIndexer(settings, resource)
        if (problems.isNotEmpty()) throw IndexerTestException(problems.joinToString("; "))
    }

    /** Re-runs the test suite on every indexer; results surface via health/indexerstatus on refresh. */
    suspend fun testAllIndexers(): Outcome<Unit> = call { dataSource.testAllIndexers(it) }

    private suspend fun <T> call(block: suspend (ArrServiceSettings) -> T): Outcome<T> {
        val settings = settingsRepository.serviceSettings(ArrService.Prowlarr)
            ?: return Outcome.Failure(LoadError.Unexpected("Prowlarr is not configured"))
        return try {
            Outcome.Success(block(settings))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IndexerTestException) {
            Outcome.Failure(LoadError.Unexpected(e.message ?: "Indexer test failed"))
        } catch (e: Exception) {
            Outcome.Failure(e.toLoadError())
        }
    }
}

/** A completed indexer test whose result was "invalid" — carries the server's validation messages. */
private class IndexerTestException(message: String) : Exception(message)
