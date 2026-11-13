package com.pyoungws.cobaltwolf.shared.arrs

import com.pyoungws.cobaltwolf.shared.net.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * The supported *arr-family services. [managesMedia] marks the two whose libraries the app
 * reads calendars from and edits; Bazarr and Prowlarr are connection-only companions.
 */
enum class ArrService {
    Radarr, Sonarr, Bazarr, Prowlarr;

    val managesMedia: Boolean get() = this == Radarr || this == Sonarr
}

/** Connection details for one service; both fields are required before it counts as configured. */
data class ArrServiceSettings(
    val serverUrl: String? = null,
    val apiKey: String? = null,
)

data class ArrSettings(
    val radarr: ArrServiceSettings = ArrServiceSettings(),
    val sonarr: ArrServiceSettings = ArrServiceSettings(),
    val bazarr: ArrServiceSettings = ArrServiceSettings(),
    val prowlarr: ArrServiceSettings = ArrServiceSettings(),
)

/** Persists [ArrSettings]. Platform implementations must protect the API keys. */
interface ArrSettingsStore {
    fun load(): ArrSettings
    fun save(settings: ArrSettings)
}

/** Volatile store for tests and targets without platform storage. */
class InMemoryArrSettingsStore(initial: ArrSettings = ArrSettings()) : ArrSettingsStore {
    private var current = initial
    override fun load() = current
    override fun save(settings: ArrSettings) {
        current = settings
    }
}

class ArrSettingsRepository(private val store: ArrSettingsStore) {
    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<ArrSettings> = _settings.asStateFlow()

    /** Normalized connection details for [service], or null when URL or key is missing/invalid. */
    fun serviceSettings(service: ArrService): ArrServiceSettings? {
        val raw = _settings.value.forService(service)
        val url = raw.serverUrl?.let(::normalizeServerUrl) ?: return null
        val key = raw.apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ArrServiceSettings(url, key)
    }

    /** Services with a complete, usable configuration. */
    val configuredServices: List<ArrService>
        get() = ArrService.entries.filter { serviceSettings(it) != null }

    fun update(service: ArrService, serverUrl: String, apiKey: String) {
        val updated = ArrServiceSettings(serverUrl.trim().trimEnd('/').ifEmpty { null }, apiKey.trim().ifEmpty { null })
        _settings.value = when (service) {
            ArrService.Radarr -> _settings.value.copy(radarr = updated)
            ArrService.Sonarr -> _settings.value.copy(sonarr = updated)
            ArrService.Bazarr -> _settings.value.copy(bazarr = updated)
            ArrService.Prowlarr -> _settings.value.copy(prowlarr = updated)
        }
        store.save(_settings.value)
    }
}

fun ArrSettings.forService(service: ArrService): ArrServiceSettings = when (service) {
    ArrService.Radarr -> radarr
    ArrService.Sonarr -> sonarr
    ArrService.Bazarr -> bazarr
    ArrService.Prowlarr -> prowlarr
}

/** One entry on an *arr calendar: an upcoming episode (Sonarr) or movie release (Radarr). */
data class UpcomingItem(
    val service: ArrService,
    /** The series or movie name. */
    val title: String,
    /** Secondary label: "S02E05 · Episode title" for episodes, the year for movies. */
    val subtitle: String?,
    val date: LocalDate,
    val hasFile: Boolean,
    val monitored: Boolean,
    val posterUrl: String?,
    val overview: String?,
)

/** The merged calendar: everything that loaded, plus the services that failed (if any). */
data class ArrCalendar(
    val items: List<UpcomingItem>,
    val failedServices: List<ArrService> = emptyList(),
)
