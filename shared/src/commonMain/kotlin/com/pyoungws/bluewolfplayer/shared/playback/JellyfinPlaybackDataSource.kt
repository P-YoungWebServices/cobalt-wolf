package com.pyoungws.bluewolfplayer.shared.playback

import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

/** Jellyfin playback calls. Implementations throw the SDK's `ApiClientException` subtypes on failure. */
interface JellyfinPlaybackDataSource {
    /** [profile] tells the server what the device plays itself; [forceTranscode] skips direct play regardless. */
    suspend fun getPlaybackSource(session: Session, itemId: String, startPositionTicks: Long, profile: DeviceProfile, forceTranscode: Boolean): PlaybackSource
    /** Whether the server re-encodes video for this user; false when their policy disables video transcoding. */
    suspend fun canTranscodeVideo(session: Session): Boolean
    suspend fun reportStart(session: Session, report: PlaybackReport)
    suspend fun reportProgress(session: Session, report: PlaybackReport)
    suspend fun reportStopped(session: Session, report: PlaybackReport)
}

class SdkJellyfinPlaybackDataSource(
    private val jellyfin: Jellyfin,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JellyfinPlaybackDataSource {

    private suspend fun <T> call(session: Session, block: suspend ApiClient.() -> T): T =
        withContext(ioDispatcher) {
            jellyfin.createApi(baseUrl = session.serverAddress, accessToken = session.accessToken).block()
        }

    override suspend fun getPlaybackSource(
        session: Session,
        itemId: String,
        startPositionTicks: Long,
        profile: DeviceProfile,
        forceTranscode: Boolean,
    ) = call(session) {
        val id = UUID.fromString(itemId)
        val response = mediaInfoApi.getPostedPlaybackInfo(
            id,
            PlaybackInfoDto(
                userId = UUID.fromString(session.userId),
                startTimeTicks = startPositionTicks.takeIf { it > 0 },
                deviceProfile = profile,
                enableDirectPlay = !forceTranscode,
                enableDirectStream = !forceTranscode,
                enableTranscoding = true,
                autoOpenLiveStream = true,
            ),
        ).content
        val source = response.mediaSources.firstOrNull() ?: throw noPlayableSource()
        source.toPlaybackSource(session, itemId, response.playSessionId)
    }

    override suspend fun canTranscodeVideo(session: Session): Boolean = call(session) {
        userApi.getCurrentUser().content.policy?.enableVideoPlaybackTranscoding ?: true
    }

    override suspend fun reportStart(session: Session, report: PlaybackReport) = call(session) {
        sessionApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = UUID.fromString(report.itemId),
                mediaSourceId = report.mediaSourceId,
                playSessionId = report.playSessionId,
                positionTicks = report.positionTicks,
                isPaused = report.isPaused,
                isMuted = false,
                playMethod = report.method.toSdk(),
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
        Unit
    }

    override suspend fun reportProgress(session: Session, report: PlaybackReport) = call(session) {
        sessionApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = UUID.fromString(report.itemId),
                mediaSourceId = report.mediaSourceId,
                playSessionId = report.playSessionId,
                positionTicks = report.positionTicks,
                isPaused = report.isPaused,
                isMuted = false,
                playMethod = report.method.toSdk(),
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
        Unit
    }

    override suspend fun reportStopped(session: Session, report: PlaybackReport) = call(session) {
        sessionApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = UUID.fromString(report.itemId),
                mediaSourceId = report.mediaSourceId,
                playSessionId = report.playSessionId,
                positionTicks = report.positionTicks,
                failed = false,
            ),
        )
        Unit
    }
}

private fun noPlayableSource() = InvalidContentException("The server returned no playable media source")

private fun PlaybackMethod.toSdk() = when (this) {
    PlaybackMethod.DirectPlay -> PlayMethod.DIRECT_PLAY
    PlaybackMethod.DirectStream -> PlayMethod.DIRECT_STREAM
    PlaybackMethod.Transcode -> PlayMethod.TRANSCODE
}

internal fun authorizationHeaders(accessToken: String) = mapOf("Authorization" to "MediaBrowser Token=\"$accessToken\"")

internal fun MediaSourceInfo.toPlaybackSource(session: Session, itemId: String, playSessionId: String?): PlaybackSource {
    val server = session.serverAddress.trimEnd('/')
    val mediaSourceId = id ?: itemId
    val transcodingUrl = transcodingUrl
    val (url, method) = when {
        supportsDirectPlay -> "$server/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId" to PlaybackMethod.DirectPlay
        supportsDirectStream -> "$server/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId" to PlaybackMethod.DirectStream
        transcodingUrl != null -> "$server/${transcodingUrl.trimStart('/')}" to PlaybackMethod.Transcode
        else -> throw noPlayableSource()
    }
    // When the original file is played its embedded tracks come from the container (with their
    // fonts, for ASS), so only sidecar files are side-loaded. A transcode carries no subtitle
    // tracks, so there every externally deliverable one is side-loaded.
    val subtitles = mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL && it.deliveryUrl != null }
            .filter { method == PlaybackMethod.Transcode || it.isExternal }
            .map {
                ExternalSubtitle(
                    url = if (it.isExternalUrl == true) it.deliveryUrl!! else "$server/${it.deliveryUrl!!.trimStart('/')}",
                    mimeType = subtitleMimeType(it.codec),
                    language = it.language,
                    label = it.displayTitle ?: it.title,
                    isDefault = it.isDefault,
                    isForced = it.isForced,
                )
            }
    return PlaybackSource(mediaSourceId, playSessionId, url, method, subtitles, authorizationHeaders(session.accessToken), toSourceMediaInfo())
}

internal fun MediaSourceInfo.toSourceMediaInfo(): SourceMediaInfo {
    val streams = mediaStreams.orEmpty()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.filter { it.type == MediaStreamType.AUDIO }.let { tracks ->
        tracks.firstOrNull { it.index == defaultAudioStreamIndex } ?: tracks.firstOrNull { it.isDefault } ?: tracks.firstOrNull()
    }
    return SourceMediaInfo(
        container = container,
        bitrate = bitrate,
        video = video?.let {
            VideoStreamInfo(
                codec = it.codec,
                profile = it.profile,
                width = it.width,
                height = it.height,
                frameRate = it.realFrameRate ?: it.averageFrameRate,
                bitDepth = it.bitDepth,
                bitrate = it.bitRate,
                range = it.videoRangeType?.toVideoRange(),
                dolbyVisionProfile = it.dvProfile,
            )
        },
        audio = audio?.let { AudioStreamInfo(codec = it.codec, profile = it.profile, channels = it.channels, sampleRate = it.sampleRate, bitrate = it.bitRate) },
    )
}

internal fun subtitleMimeType(codec: String?) = when (codec?.lowercase()) {
    "vtt", "webvtt" -> "text/vtt"
    "ass", "ssa" -> "text/x-ssa"
    "ttml" -> "application/ttml+xml"
    else -> "application/x-subrip"
}
