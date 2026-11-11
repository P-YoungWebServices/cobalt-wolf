package com.pyoungws.bluewolfplayer.shared.qbittorrent

import com.pyoungws.bluewolfplayer.shared.net.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection details for a qBittorrent Web UI; all three fields are required. */
data class QBittorrentSettings(
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
)

/** Persists [QBittorrentSettings]. Platform implementations must protect the password. */
interface QBittorrentSettingsStore {
    fun load(): QBittorrentSettings
    fun save(settings: QBittorrentSettings)
}

/** Volatile store for tests and targets without platform storage. */
class InMemoryQBittorrentSettingsStore(initial: QBittorrentSettings = QBittorrentSettings()) : QBittorrentSettingsStore {
    private var current = initial
    override fun load() = current
    override fun save(settings: QBittorrentSettings) {
        current = settings
    }
}

class QBittorrentSettingsRepository(private val store: QBittorrentSettingsStore) {
    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<QBittorrentSettings> = _settings.asStateFlow()

    /** Normalized connection details, or null when any field is missing/invalid. */
    fun serviceSettings(): QBittorrentSettings? {
        val raw = _settings.value
        val url = raw.serverUrl?.let(::normalizeServerUrl) ?: return null
        val user = raw.username?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val password = raw.password?.takeIf { it.isNotEmpty() } ?: return null
        return QBittorrentSettings(url, user, password)
    }

    val isConfigured: Boolean get() = serviceSettings() != null

    fun update(serverUrl: String, username: String, password: String) {
        _settings.value = QBittorrentSettings(
            serverUrl = serverUrl.trim().trimEnd('/').ifEmpty { null },
            username = username.trim().ifEmpty { null },
            password = password.ifEmpty { null },
        )
        store.save(_settings.value)
    }
}

/** One torrent as reported by `torrents/info`. */
data class Torrent(
    val hash: String,
    val name: String,
    /** The raw qBittorrent state string (e.g. "downloading", "stalledUP"); mapped to labels in the UI. */
    val state: String,
    /** 0..1 download progress. */
    val progressFraction: Float,
    val sizeBytes: Long,
    val downloadSpeedBps: Long,
    val uploadSpeedBps: Long,
    /** Seconds to completion, or null when unknown/infinite. */
    val etaSeconds: Long?,
    val category: String?,
)

/** The torrent list plus the client's global transfer rates. */
data class DownloadState(
    val torrents: List<Torrent>,
    val totalDownloadSpeedBps: Long,
    val totalUploadSpeedBps: Long,
)
