package com.pyoungws.bluewolfplayer.shared.qbittorrent

class InMemoryQBittorrentSessionStore : QBittorrentSessionStore {
    private var cookie: String? = null
    override suspend fun load() = cookie
    override suspend fun save(cookie: String) {
        this.cookie = cookie
    }
    override suspend fun clear() {
        cookie = null
    }
}

class FakeQBittorrentDataSource : QBittorrentDataSource {
    val calls = mutableListOf<String>()
    var torrentsResult: List<Torrent> = emptyList()
    var speeds: TransferSpeeds = TransferSpeeds(0, 0)
    var loginResult = true
    /** Thrown by the next [torrents]/[transferInfo] call; [SessionExpiredException] triggers re-login. */
    var failOnce: Throwable? = null
    var error: Throwable? = null

    override suspend fun login(settings: QBittorrentSettings): Boolean {
        calls += "login(${settings.serverUrl},${settings.username})"
        return loginResult
    }

    override suspend fun torrents(settings: QBittorrentSettings): List<Torrent> {
        calls += "torrents"
        failOnce?.let { failOnce = null; throw it }
        error?.let { throw it }
        return torrentsResult
    }

    override suspend fun transferInfo(settings: QBittorrentSettings): TransferSpeeds {
        calls += "transferInfo"
        failOnce?.let { failOnce = null; throw it }
        error?.let { throw it }
        return speeds
    }
}

fun torrent(
    hash: String,
    name: String = "Torrent $hash",
    state: String = "downloading",
    progress: Float = 0.5f,
) = Torrent(
    hash = hash,
    name = name,
    state = state,
    progressFraction = progress,
    sizeBytes = 4_000_000_000,
    downloadSpeedBps = 1_000_000,
    uploadSpeedBps = 0,
    etaSeconds = 2000,
    category = null,
)
