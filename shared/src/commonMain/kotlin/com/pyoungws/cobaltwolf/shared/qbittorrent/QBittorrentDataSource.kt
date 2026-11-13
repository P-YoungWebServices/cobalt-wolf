package com.pyoungws.cobaltwolf.shared.qbittorrent

interface QBittorrentDataSource {
    /** Logs in to the Web UI; true when the server accepted the credentials (body "Ok."). */
    suspend fun login(settings: QBittorrentSettings): Boolean

    /** The torrent list (`torrents/info`). */
    suspend fun torrents(settings: QBittorrentSettings): List<Torrent>

    /** Global transfer rates (`transfer/info`). */
    suspend fun transferInfo(settings: QBittorrentSettings): TransferSpeeds

    /** Starts (resumes) torrents by hash; supports both old (`resume`) and new (`start`) API verbs. */
    suspend fun startTorrents(settings: QBittorrentSettings, hashes: List<String>)

    /** Stops (pauses) torrents by hash; supports both old (`pause`) and new (`stop`) API verbs. */
    suspend fun stopTorrents(settings: QBittorrentSettings, hashes: List<String>)

    /** True while alternative speed limits are active (`transfer/speedLimitsMode`). */
    suspend fun speedLimitsMode(settings: QBittorrentSettings): Boolean

    /** Flips alternative speed limits on/off (`transfer/toggleSpeedLimitsMode`). */
    suspend fun toggleSpeedLimitsMode(settings: QBittorrentSettings)
}

/** Thrown when the stored `SID` session cookie was rejected; the caller should log in again. */
class SessionExpiredException : Exception()

/** Global client transfer rates in bytes per second. */
data class TransferSpeeds(val downloadBps: Long, val uploadBps: Long)
