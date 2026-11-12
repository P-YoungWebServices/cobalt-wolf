package com.pyoungws.bluewolfplayer.shared.arrs

import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.net.normalizeServerUrl
import com.pyoungws.bluewolfplayer.shared.net.toLoadError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Radarr/Sonarr integration: per-service connection settings, connection tests and the merged
 * upcoming calendar. Unconfigured services are skipped; a failure in one service does not hide
 * the other's entries.
 */
class ArrRepository(
    private val dataSource: ArrDataSource,
    private val settingsRepository: ArrSettingsRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val settings: StateFlow<ArrSettings> = settingsRepository.settings

    fun isConfigured(service: ArrService): Boolean = settingsRepository.serviceSettings(service) != null

    val configuredServices: List<ArrService> get() = settingsRepository.configuredServices

    fun saveService(service: ArrService, serverUrl: String, apiKey: String) =
        settingsRepository.update(service, serverUrl, apiKey)

    /** Probes [service] with the given (not necessarily saved) URL and key. */
    suspend fun testConnection(service: ArrService, serverUrl: String, apiKey: String): Outcome<Unit> {
        val url = normalizeServerUrl(serverUrl)
            ?: return Outcome.Failure(LoadError.Unexpected("Enter a valid server URL"))
        val key = apiKey.trim().takeIf { it.isNotEmpty() }
            ?: return Outcome.Failure(LoadError.Unexpected("Enter the API key"))
        return try {
            dataSource.status(service, ArrServiceSettings(url, key))
            Outcome.Success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Outcome.Failure(e.toLoadError())
        }
    }

    /**
     * The upcoming calendar for every configured service between [start] and [end] inclusive —
     * yesterday through the next [LOOKAHEAD_DAYS] days when no range is given — merged and
     * sorted by date. [ArrCalendar.failedServices] lists the services that failed so the
     * caller can warn without hiding what did load. When every configured service fails the
     * whole call fails with the first error.
     */
    suspend fun calendar(start: LocalDate? = null, end: LocalDate? = null): Outcome<ArrCalendar> {
        val configured = ArrService.entries.filter { it.managesMedia }.mapNotNull { service ->
            settingsRepository.serviceSettings(service)?.let { service to it }
        }
        val today = clock.todayIn(timeZone)
        val rangeStart = start ?: today.minus(1, DateTimeUnit.DAY)
        val rangeEnd = end ?: today.plus(LOOKAHEAD_DAYS, DateTimeUnit.DAY)

        data class ServiceResult(val service: ArrService, val items: List<UpcomingItem>?, val error: LoadError?)

        val results = coroutineScope {
            configured.map { (service, serviceSettings) ->
                async {
                    try {
                        ServiceResult(service, dataSource.calendar(service, serviceSettings, rangeStart, rangeEnd), null)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        ServiceResult(service, null, e.toLoadError())
                    }
                }
            }.awaitAll()
        }
        val failures = results.filter { it.error != null }
        if (configured.isNotEmpty() && failures.size == configured.size) {
            return Outcome.Failure(failures.first().error!!)
        }
        return Outcome.Success(
            ArrCalendar(
                items = results.flatMap { it.items.orEmpty() }.sortedWith(compareBy({ it.date }, { it.title })),
                failedServices = failures.map { it.service },
            ),
        )
    }

    private companion object {
        const val LOOKAHEAD_DAYS = 30
    }
}
