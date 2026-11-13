package com.pyoungws.cobaltwolf.shared.seerr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where to reach the Seerr (Jellyseerr/Overseerr) server. Null or blank disables the requests feature. */
data class SeerrSettings(val serverUrl: String? = null)

/** Persists [SeerrSettings]. */
interface SeerrSettingsStore {
    fun load(): SeerrSettings
    fun save(settings: SeerrSettings)
}

class SeerrSettingsRepository(private val store: SeerrSettingsStore) {
    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<SeerrSettings> = _settings.asStateFlow()

    val serverUrl: String? get() = _settings.value.serverUrl?.trim()?.takeIf { it.isNotEmpty() }

    fun update(transform: (SeerrSettings) -> SeerrSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        store.save(updated)
    }
}
