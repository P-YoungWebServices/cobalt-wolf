package com.pyoungws.bluewolfplayer.shared.qbittorrent

import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.net.normalizeServerUrl
import com.pyoungws.bluewolfplayer.shared.net.toLoadError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * qBittorrent monitoring: connection settings, login and the torrent list. The Web UI answers
 * 403 once the `SID` cookie expires, so a single automatic re-login-and-retry is built in.
 */
class QBittorrentRepository(
    private val dataSource: QBittorrentDataSource,
    private val settingsRepository: QBittorrentSettingsRepository,
) {
    val settings = settingsRepository.settings

    val isConfigured: Boolean get() = settingsRepository.isConfigured

    fun saveSettings(serverUrl: String, username: String, password: String) =
        settingsRepository.update(serverUrl, username, password)

    /** Logs in with the given (not necessarily saved) details; fails on unreachable or rejected credentials. */
    suspend fun testConnection(serverUrl: String, username: String, password: String): Outcome<Unit> {
        val url = normalizeServerUrl(serverUrl)
            ?: return Outcome.Failure(LoadError.Unexpected("Enter a valid server URL"))
        val user = username.trim().takeIf { it.isNotEmpty() }
            ?: return Outcome.Failure(LoadError.Unexpected("Enter the username"))
        if (password.isEmpty()) return Outcome.Failure(LoadError.Unexpected("Enter the password"))
        return try {
            if (dataSource.login(QBittorrentSettings(url, user, password))) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(LoadError.Unexpected("Invalid username or password"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Outcome.Failure(e.toLoadError())
        }
    }

    /**
     * The torrent list plus global transfer rates. An expired session (403) triggers one
     * automatic login and retry before the call fails.
     */
    suspend fun downloads(): Outcome<DownloadState> {
        val settings = settingsRepository.serviceSettings()
            ?: return Outcome.Failure(LoadError.Unexpected("qBittorrent is not configured"))
        return try {
            Outcome.Success(fetch(settings))
        } catch (e: SessionExpiredException) {
            try {
                if (!dataSource.login(settings)) return Outcome.Failure(LoadError.Forbidden)
                Outcome.Success(fetch(settings))
            } catch (retryError: Exception) {
                if (retryError is kotlinx.coroutines.CancellationException) throw retryError
                Outcome.Failure(retryError.toLoadError())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Outcome.Failure(e.toLoadError())
        }
    }

    private suspend fun fetch(settings: QBittorrentSettings): DownloadState = coroutineScope {
        val torrents = async { dataSource.torrents(settings) }
        val speeds = async { dataSource.transferInfo(settings) }
        val transfer = speeds.await()
        DownloadState(torrents.await(), transfer.downloadBps, transfer.uploadBps)
    }
}
