package com.pyoungws.cobaltwolf.shared.admin

import com.pyoungws.cobaltwolf.shared.model.MediaKind
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlayerStateInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TranscodeReason
import org.jellyfin.sdk.model.api.TranscodingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The dashboard shows what the server reports for each session; keep the mapping honest. */
class SessionMappingTest {

    private companion object {
        val ACTIVITY_DATE = java.time.LocalDateTime.parse("2026-11-10T12:00:00")

        fun stream(codec: String, type: MediaStreamType, index: Int) = MediaStream(
            codec = codec,
            type = type,
            index = index,
            isInterlaced = false,
            isDefault = index == 0,
            isForced = false,
            isHearingImpaired = false,
            isOriginal = false,
            isExternal = false,
            isTextSubtitleStream = false,
            supportsExternalStream = false,
        )
    }

    private fun sessionInfo(
        playState: PlayerStateInfo? = null,
        nowPlayingItem: BaseItemDto? = null,
        transcodingInfo: TranscodingInfo? = null,
    ) = SessionInfoDto(
        id = "session-1",
        userId = UUID.fromString("07b1927a-431c-4a13-bf3d-6bf581842d63"),
        playableMediaTypes = emptyList(),
        additionalUsers = emptyList(),
        nowPlayingQueue = emptyList(),
        supportedCommands = emptyList(),
        lastActivityDate = ACTIVITY_DATE,
        lastPlaybackCheckIn = ACTIVITY_DATE,
        lastPausedDate = null,
        userName = "alice",
        client = "Jellyfin Web",
        deviceName = "Shield TV",
        playState = playState,
        nowPlayingItem = nowPlayingItem,
        transcodingInfo = transcodingInfo,
        isActive = true,
        supportsMediaControl = true,
        supportsRemoteControl = true,
        hasCustomDeviceName = false,
    )

    @Test
    fun `a transcoding session reports codecs and transcode details`() {
        val dto = sessionInfo(
            playState = PlayerStateInfo(
                positionTicks = 300_000_000L,
                canSeek = true,
                isPaused = false,
                isMuted = false,
                playMethod = PlayMethod.TRANSCODE,
                audioStreamIndex = 1,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
            nowPlayingItem = BaseItemDto(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                name = "Movie",
                type = BaseItemKind.MOVIE,
                runTimeTicks = 600_000_000L,
                mediaStreams = listOf(
                    stream("hevc", MediaStreamType.VIDEO, 0),
                    stream("truehd", MediaStreamType.AUDIO, 1),
                    stream("ac3", MediaStreamType.AUDIO, 2),
                ),
            ),
            transcodingInfo = TranscodingInfo(
                container = "hls",
                videoCodec = "h264",
                audioCodec = "aac",
                isVideoDirect = false,
                isAudioDirect = false,
                bitrate = 8_000_000,
                width = 1920,
                height = 1080,
                audioChannels = 2,
                transcodeReasons = listOf(TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED),
            ),
        )

        val session = dto.toActiveSession("https://server")

        assertEquals("session-1", session.id)
        assertEquals("alice", session.userName)
        assertEquals("Shield TV", session.deviceName)
        assertEquals("Jellyfin Web", session.client)
        assertEquals("Movie", session.item?.name)
        assertEquals(MediaKind.Movie, session.item?.kind)
        assertEquals(0.5f, session.progressFraction)
        assertEquals(SessionPlayMethod.Transcode, session.playMethod)
        assertEquals("hevc", session.videoCodec)
        // The selected audio stream (index 1), not just the first audio stream.
        assertEquals("truehd", session.audioCodec)
        assertEquals(
            TranscodeDetails(
                container = "hls",
                videoCodec = "h264",
                audioCodec = "aac",
                bitrate = 8_000_000,
                width = 1920,
                height = 1080,
                audioChannels = 2,
                isVideoDirect = false,
                isAudioDirect = false,
                reasons = listOf("video codec not supported"),
            ),
            session.transcoding,
        )
    }

    @Test
    fun `an idle session has no playback details`() {
        val session = sessionInfo().toActiveSession("https://server")

        assertNull(session.item)
        assertNull(session.progressFraction)
        assertNull(session.playMethod)
        assertNull(session.videoCodec)
        assertNull(session.transcoding)
    }

    @Test
    fun `direct play sessions report the play method and paused flag`() {
        val session = sessionInfo(
            playState = PlayerStateInfo(
                positionTicks = 100_000_000L,
                canSeek = true,
                isPaused = true,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
            nowPlayingItem = BaseItemDto(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                name = "Episode",
                type = BaseItemKind.EPISODE,
                runTimeTicks = 400_000_000L,
            ),
        ).toActiveSession("https://server")

        assertEquals(SessionPlayMethod.DirectPlay, session.playMethod)
        assertEquals(true, session.isPaused)
        assertEquals(0.25f, session.progressFraction)
    }
}
