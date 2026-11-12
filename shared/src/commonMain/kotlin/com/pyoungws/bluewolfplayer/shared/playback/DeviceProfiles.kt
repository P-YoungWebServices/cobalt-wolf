package com.pyoungws.bluewolfplayer.shared.playback

import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodeSeekInfo
import org.jellyfin.sdk.model.api.TranscodingProfile
import org.jellyfin.sdk.model.api.VideoRangeType

/** Containers ExoPlayer's extractors read. */
private const val DIRECT_PLAY_CONTAINERS = "mkv,webm,mp4,m4v,mov,ts,mpegts,avi"

private const val PCM_CODECS = "pcm_s16le,pcm_s24le,pcm_s32le,pcm_s16be,pcm_s24be,pcm_f32le"

/** Subtitle formats the player renders itself from the container (ASS/SSA with libass, PGS as bitmaps). */
private val EMBEDDED_SUBTITLES = listOf("ass", "ssa", "srt", "subrip", "vtt", "webvtt", "pgssub", "pgs", "dvdsub", "dvbsub", "mov_text", "ttml")

/** Text formats the player can load from a separate file. */
private val EXTERNAL_SUBTITLES = listOf("srt", "subrip", "ass", "ssa", "vtt", "ttml")

private const val MAX_TRANSCODE_AUDIO_CHANNELS = 6

/**
 * Describes to the server what this device can play without it, so the server only transcodes
 * what is left: codecs neither the device decoders nor FFmpeg handle, pictures larger than the
 * decoder takes, and HDR formats the decoder and display cannot show (which it tone-maps).
 * [serverTranscodesVideo] is false when the user's server policy forbids video transcoding.
 */
fun buildDeviceProfile(capabilities: DeviceCapabilities, settings: PlaybackSettings, serverTranscodesVideo: Boolean = true): DeviceProfile {
    val video = VideoCodec.entries.mapNotNull { videoSupport(it, capabilities, settings, serverTranscodesVideo) }
    val audio = AudioCodec.entries.filter { audioPath(it, capabilities, settings) != DecodePath.Transcode }
    val audioCodecs = audio.joinToString(",") { if (it == AudioCodec.Pcm) PCM_CODECS else it.jellyfinName }
    val ac3Passthrough = audioPath(AudioCodec.Ac3, capabilities, settings) == DecodePath.Passthrough
    return DeviceProfile(
        name = "Cobalt Wolf (Android)",
        directPlayProfiles = if (video.isEmpty()) {
            emptyList()
        } else {
            listOf(
                DirectPlayProfile(
                    container = DIRECT_PLAY_CONTAINERS,
                    type = DlnaProfileType.VIDEO,
                    videoCodec = video.joinToString(",") { it.codec.jellyfinName },
                    audioCodec = audioCodecs,
                ),
            )
        },
        transcodingProfiles = listOf(
            TranscodingProfile(
                container = "ts",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264",
                // A receiver gets 5.1 as Dolby Digital; otherwise AAC, which every device decodes.
                audioCodec = if (ac3Passthrough) "ac3,aac" else "aac",
                protocol = MediaStreamProtocol.HLS,
                context = EncodingContext.STREAMING,
                transcodeSeekInfo = TranscodeSeekInfo.AUTO,
                maxAudioChannels = capabilities.maxPcmChannels.coerceIn(2, MAX_TRANSCODE_AUDIO_CHANNELS).toString(),
                conditions = emptyList(),
            ),
        ),
        containerProfiles = emptyList(),
        // No limits to state when the server would only repackage the video it is asked to transcode.
        codecProfiles = if (serverTranscodesVideo) video.map { it.toCodecProfile() } else emptyList(),
        subtitleProfiles = EMBEDDED_SUBTITLES.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED) } +
            EXTERNAL_SUBTITLES.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL) },
        maxStreamingBitrate = settings.maxBitrate.bps ?: 200_000_000,
    )
}

private fun VideoSupport.toCodecProfile() = CodecProfile(
    type = CodecType.VIDEO,
    codec = codec.jellyfinName,
    conditions = listOf(
        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, maxWidth.toString()),
        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, maxHeight.toString()),
        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, maxBitDepth.toString()),
        condition(ProfileConditionType.EQUALS_ANY, ProfileConditionValue.VIDEO_RANGE_TYPE, ranges.joinToString("|") { it.toSdk().serialName }),
    ),
    applyConditions = emptyList(),
)

// Not required: a stream whose property the server could not probe is still tried on the device.
private fun condition(type: ProfileConditionType, property: ProfileConditionValue, value: String) =
    ProfileCondition(condition = type, property = property, value = value, isRequired = false)

internal fun VideoRange.toSdk(): VideoRangeType = when (this) {
    VideoRange.Sdr -> VideoRangeType.SDR
    VideoRange.Hdr10 -> VideoRangeType.HDR10
    VideoRange.Hdr10Plus -> VideoRangeType.HDR10_PLUS
    VideoRange.Hlg -> VideoRangeType.HLG
    VideoRange.DolbyVision -> VideoRangeType.DOVI
    VideoRange.DolbyVisionWithHdr10 -> VideoRangeType.DOVI_WITH_HDR10
    VideoRange.DolbyVisionWithHlg -> VideoRangeType.DOVI_WITH_HLG
    VideoRange.DolbyVisionWithSdr -> VideoRangeType.DOVI_WITH_SDR
    VideoRange.DolbyVisionWithEl -> VideoRangeType.DOVI_WITH_EL
    VideoRange.DolbyVisionWithHdr10Plus -> VideoRangeType.DOVI_WITH_HDR10_PLUS
    VideoRange.DolbyVisionWithElHdr10Plus -> VideoRangeType.DOVI_WITH_ELHDR10_PLUS
}

internal fun VideoRangeType.toVideoRange(): VideoRange? = VideoRange.entries.firstOrNull { it.toSdk() == this }
