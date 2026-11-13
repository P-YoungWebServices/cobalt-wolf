package com.pyoungws.cobaltwolf.shared.qbittorrent

import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.net.normalizeServerUrl
import com.pyoungws.cobaltwolf.shared.net.toLoadError
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
    suspend fun downloads(): Outcome<DownloadState> = withSessionRetry { fetch(it) }

    /** Starts (resumes) one torrent. */
    suspend fun startTorrent(hash: String): Outcome<Unit> =
        withSessionRetry { dataSource.startTorrents(it, listOf(hash)) }

    /** Stops (pauses) one torrent. */
    suspend fun stopTorrent(hash: String): Outcome<Unit> =
        withSessionRetry { dataSource.stopTorrents(it, listOf(hash)) }

    /** Flips the alternative speed limits; the next poll reports the new mode. */
    suspend fun toggleAlternativeSpeeds(): Outcome<Unit> =
        withSessionRetry { dataSource.toggleSpeedLimitsMode(it) }

    /**
     * Runs [block] against the saved connection, re-logging once when the SID cookie has expired.
     */
    private suspend fun <T> withSessionRetry(block: suspend (QBittorrentSettings) -> T): Outcome<T> {
        val settings = settingsRepository.serviceSettings()
            ?: return Outcome.Failure(LoadError.Unexpected("qBittorrent is not configured"))
        return try {
            Outcome.Success(block(settings))
        } catch (e: SessionExpiredException) {
            try {
                if (!dataSource.login(settings)) return Outcome.Failure(LoadError.Forbidden)
                Outcome.Success(block(settings))
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
        val altSpeeds = async { dataSource.speedLimitsMode(settings) }
        val transfer = speeds.await()
        DownloadState(torrents.await(), transfer.downloadBps, transfer.uploadBps, altSpeeds.await())
    }
}
