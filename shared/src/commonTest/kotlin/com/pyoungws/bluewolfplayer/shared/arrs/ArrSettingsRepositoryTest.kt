package com.pyoungws.bluewolfplayer.shared.arrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArrSettingsRepositoryTest {

    private fun repository(settings: ArrSettings = ArrSettings(), store: ArrSettingsStore = InMemoryArrSettingsStore(settings)) =
        ArrSettingsRepository(store)

    @Test
    fun `a service is only configured when both url and api key are usable`() {
        val repository = repository(
            ArrSettings(radarr = ArrServiceSettings("radarr.example.com:7878", "key")),
        )

        assertEquals(
            ArrServiceSettings("https://radarr.example.com:7878", "key"),
            repository.serviceSettings(ArrService.Radarr),
        )
        assertNull(repository.serviceSettings(ArrService.Sonarr))
        assertEquals(listOf(ArrService.Radarr), repository.configuredServices)
    }

    @Test
    fun `a missing or invalid url leaves the service unconfigured`() {
        val repository = repository(
            ArrSettings(
                radarr = ArrServiceSettings("not a host", "key"),
                sonarr = ArrServiceSettings("sonarr.example.com", "   "),
            ),
        )

        assertNull(repository.serviceSettings(ArrService.Radarr))
        assertNull(repository.serviceSettings(ArrService.Sonarr))
        assertEquals(emptyList(), repository.configuredServices)
    }

    @Test
    fun `update normalizes blanks to null and persists`() {
        val store = InMemoryArrSettingsStore()
        val repository = repository(store = store)

        repository.update(ArrService.Sonarr, "  http://sonarr.lan:8989/base/  ", "  abc  ")

        val stored = store.load().sonarr
        assertEquals("http://sonarr.lan:8989/base", stored.serverUrl)
        assertEquals("abc", stored.apiKey)
        assertEquals(ArrServiceSettings("http://sonarr.lan:8989/base", "abc"), repository.serviceSettings(ArrService.Sonarr))

        repository.update(ArrService.Sonarr, "", "")
        assertEquals(ArrServiceSettings(), store.load().sonarr)
    }
}
