package com.pyoungws.bluewolfplayer.shared.qbittorrent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QBittorrentSettingsRepositoryTest {

    private fun repository(
        settings: QBittorrentSettings = QBittorrentSettings(),
        store: QBittorrentSettingsStore = InMemoryQBittorrentSettingsStore(settings),
    ) = QBittorrentSettingsRepository(store)

    @Test
    fun `serviceSettings returns normalized details only when everything is present`() {
        val repository = repository(
            QBittorrentSettings("qbit.example.com:8080", "admin", "hunter2"),
        )

        assertEquals(
            QBittorrentSettings("https://qbit.example.com:8080", "admin", "hunter2"),
            repository.serviceSettings(),
        )
        assertTrue(repository.isConfigured)
    }

    @Test
    fun `any missing or invalid field leaves it unconfigured`() {
        assertNull(repository(QBittorrentSettings("qbit.lan", "", "p")).serviceSettings())
        assertNull(repository(QBittorrentSettings("qbit.lan", "u", null)).serviceSettings())
        assertNull(repository(QBittorrentSettings("not a host", "u", "p")).serviceSettings())
        assertNull(repository(QBittorrentSettings(null, "u", "p")).serviceSettings())
    }

    @Test
    fun `update trims, blanks to null and persists`() {
        val store = InMemoryQBittorrentSettingsStore()
        val repository = repository(store = store)

        repository.update("  http://qbit.lan:8080/ui/ ", " admin ", " hunter2 ")

        val stored = store.load()
        assertEquals("http://qbit.lan:8080/ui", stored.serverUrl)
        assertEquals("admin", stored.username)
        assertEquals(" hunter2 ", stored.password)

        repository.update("", "", "")
        assertEquals(QBittorrentSettings(), store.load())
    }
}
