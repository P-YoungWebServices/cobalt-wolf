package com.pyoungws.bluewolfplayer.shared.qbittorrent

interface QBittorrentDataSource {
    /** Logs in to the Web UI; true when the server accepted the credentials (body "Ok."). */
    suspend fun login(settings: QBittorrentSettings): Boolean

    /** The torrent list (`torrents/info`). */
    suspend fun torrents(settings: QBittorrentSettings): List<Torrent>

    /** Global transfer rates (`transfer/info`). */
    suspend fun transferInfo(settings: QBittorrentSettings): TransferSpeeds
}

/** Thrown when the stored `SID` session cookie was rejected; the caller should log in again. */
class SessionExpiredException : Exception()

/** Global client transfer rates in bytes per second. */
data class TransferSpeeds(val downloadBps: Long, val uploadBps: Long)
