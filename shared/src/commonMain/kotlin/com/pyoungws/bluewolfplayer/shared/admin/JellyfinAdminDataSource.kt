package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.library.toMediaItem
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TranscodingInfo
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest

/** Jellyfin admin queries. Implementations throw the SDK's `ApiClientException` subtypes on failure. */
interface JellyfinAdminDataSource {
    suspend fun getSessions(session: Session): List<ActiveSession>
    suspend fun getRecentlyAdded(session: Session, limit: Int): List<MediaItem>
    suspend fun sendMessage(session: Session, sessionId: String, text: String)
}

class SdkJellyfinAdminDataSource(
    private val jellyfin: Jellyfin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JellyfinAdminDataSource {

    private suspend fun <T> call(session: Session, block: suspend ApiClient.() -> T): T =
        withContext(ioDispatcher) {
            jellyfin.createApi(baseUrl = session.serverAddress, accessToken = session.accessToken).block()
        }

    override suspend fun getSessions(session: Session) = call(session) {
        sessionApi.getSessions().content.map { it.toActiveSession(session.serverAddress) }
    }

    override suspend fun getRecentlyAdded(session: Session, limit: Int) = call(session) {
        libraryApi.getLatestMedia(
            GetLatestMediaRequest(
                userId = session.userId.toUuid(),
                fields = ITEM_FIELDS,
                limit = limit,
                enableUserData = true,
            ),
        ).content.map { it.toMediaItem(session.serverAddress) }
    }

    override suspend fun sendMessage(session: Session, sessionId: String, text: String) = call(session) {
        sessionApi.sendMessageCommand(
            sessionId,
            MessageCommand(header = MESSAGE_HEADER, text = text, timeoutMs = MESSAGE_TIMEOUT_MS),
        )
        Unit
    }

    private companion object {
        const val MESSAGE_HEADER = "Blue Wolf Player"
        const val MESSAGE_TIMEOUT_MS = 15_000L
        val ITEM_FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.GENRES)
    }
}

private fun String.toUuid(): UUID = UUID.fromString(this)

internal fun SessionInfoDto.toActiveSession(serverAddress: String): ActiveSession {
    val item = nowPlayingItem?.toMediaItem(serverAddress)
    val streams = nowPlayingItem?.mediaStreams.orEmpty()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == playState?.audioStreamIndex }
        ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
    val positionTicks = playState?.positionTicks
    val runtimeTicks = nowPlayingItem?.runTimeTicks

    return ActiveSession(
        id = id.orEmpty(),
        userName = userName.orEmpty(),
        deviceName = deviceName.orEmpty(),
        client = client,
        item = item,
        progressFraction = if (positionTicks != null && runtimeTicks != null && runtimeTicks > 0) {
            (positionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
        } else {
            null
        },
        isPaused = playState?.isPaused == true,
        playMethod = playState?.playMethod?.toSessionPlayMethod(),
        videoCodec = video?.codec,
        audioCodec = audio?.codec,
        transcoding = transcodingInfo?.toTranscodeDetails(),
    )
}

private fun PlayMethod.toSessionPlayMethod() = when (this) {
    PlayMethod.DIRECT_PLAY -> SessionPlayMethod.DirectPlay
    PlayMethod.DIRECT_STREAM -> SessionPlayMethod.DirectStream
    PlayMethod.TRANSCODE -> SessionPlayMethod.Transcode
}

private fun TranscodingInfo.toTranscodeDetails() = TranscodeDetails(
    container = container,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    bitrate = bitrate,
    width = width,
    height = height,
    audioChannels = audioChannels,
    isVideoDirect = isVideoDirect,
    isAudioDirect = isAudioDirect,
    reasons = transcodeReasons.orEmpty().map { reason ->
        reason.name.lowercase().replace('_', ' ')
    },
)
