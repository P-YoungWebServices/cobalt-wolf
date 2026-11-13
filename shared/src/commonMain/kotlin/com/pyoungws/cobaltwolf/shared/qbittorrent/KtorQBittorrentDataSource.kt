package com.pyoungws.cobaltwolf.shared.qbittorrent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class KtorQBittorrentDataSource(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QBittorrentDataSource {

    override suspend fun login(settings: QBittorrentSettings): Boolean = call {
        val response = client.submitForm(
            url = "${settings.serverUrl}/api/v2/auth/login",
            formParameters = parameters {
                append("username", settings.username.orEmpty())
                append("password", settings.password.orEmpty())
            },
        )
        // Older versions answer "Ok."/"Fails."; newer ones answer 204/empty on success.
        response.status.isSuccess() && response.bodyAsText().trim() != "Fails."
    }

    override suspend fun torrents(settings: QBittorrentSettings): List<Torrent> = call {
        client.get("${settings.serverUrl}/api/v2/torrents/info")
            .body<List<TorrentDto>>().map { it.toTorrent() }
    }

    override suspend fun transferInfo(settings: QBittorrentSettings): TransferSpeeds = call {
        client.get("${settings.serverUrl}/api/v2/transfer/info")
            .body<TransferInfoDto>().let { TransferSpeeds(it.downloadBps, it.uploadBps) }
    }

    override suspend fun startTorrents(settings: QBittorrentSettings, hashes: List<String>) = call {
        torrentAction(settings, hashes, verb = "start", legacyVerb = "resume")
    }

    override suspend fun stopTorrents(settings: QBittorrentSettings, hashes: List<String>) = call {
        torrentAction(settings, hashes, verb = "stop", legacyVerb = "pause")
    }

    override suspend fun speedLimitsMode(settings: QBittorrentSettings): Boolean = call {
        client.get("${settings.serverUrl}/api/v2/transfer/speedLimitsMode").bodyAsText().trim() == "1"
    }

    override suspend fun toggleSpeedLimitsMode(settings: QBittorrentSettings) {
        call { client.post("${settings.serverUrl}/api/v2/transfer/toggleSpeedLimitsMode") }
    }

    /**
     * qBittorrent renamed `pause`/`resume` to `stop`/`start` in Web API 2.8.4 (qBittorrent 4.5);
     * older servers only know the legacy verbs, so a 404 retries the old name.
     */
    private suspend fun torrentAction(settings: QBittorrentSettings, hashes: List<String>, verb: String, legacyVerb: String) {
        try {
            postHashes(settings, verb, hashes)
        } catch (e: ClientRequestException) {
            if (e.response.status.value != HTTP_NOT_FOUND) throw e
            postHashes(settings, legacyVerb, hashes)
        }
    }

    private suspend fun postHashes(settings: QBittorrentSettings, verb: String, hashes: List<String>) {
        client.submitForm(
            url = "${settings.serverUrl}/api/v2/torrents/$verb",
            formParameters = parameters { append("hashes", hashes.joinToString("|")) },
        )
    }

    /** The Web UI answers 403 for a missing or expired SID cookie; surface it uniformly. */
    private suspend fun <T> call(block: suspend () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: ClientRequestException) {
            if (e.response.status.value == HTTP_FORBIDDEN) throw SessionExpiredException() else throw e
        }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}

@Serializable
internal data class TorrentDto(
    val hash: String = "",
    val name: String = "",
    val state: String = "",
    val progress: Double = 0.0,
    val size: Long = 0,
    val dlspeed: Long = 0,
    val upspeed: Long = 0,
    val eta: Long = INFINITE_ETA,
    val category: String? = null,
) {
    fun toTorrent() = Torrent(
        hash = hash,
        name = name,
        state = state,
        progressFraction = progress.toFloat().coerceIn(0f, 1f),
        sizeBytes = size,
        downloadSpeedBps = dlspeed,
        uploadSpeedBps = upspeed,
        etaSeconds = eta.takeIf { it in 0 until INFINITE_ETA },
        category = category?.takeIf { it.isNotBlank() },
    )

    companion object {
        /** qBittorrent reports 8640000 when there is no meaningful ETA. */
        const val INFINITE_ETA = 8_640_000L
    }
}

@Serializable
internal data class TransferInfoDto(
    val dl_info_speed: Long = 0,
    val up_info_speed: Long = 0,
) {
    val downloadBps: Long get() = dl_info_speed
    val uploadBps: Long get() = up_info_speed
}
