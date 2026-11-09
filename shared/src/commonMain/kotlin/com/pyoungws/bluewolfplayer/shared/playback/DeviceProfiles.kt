package com.pyoungws.bluewolfplayer.shared.playback

import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodeSeekInfo
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * Basic profile: containers and codecs ExoPlayer plays without extra decoders; anything else is
 * transcoded to HLS. Per-device capability detection is a separate task (high-end playback).
 */
internal val DEVICE_PROFILE = DeviceProfile(
    name = "Blue Wolf Player (Android)",
    directPlayProfiles = listOf(
        DirectPlayProfile(
            container = "mp4,m4v,mkv,webm",
            type = DlnaProfileType.VIDEO,
            videoCodec = "h264,hevc,vp9,av1",
            audioCodec = "aac,mp3,ac3,eac3,opus,vorbis,flac",
        ),
    ),
    transcodingProfiles = listOf(
        TranscodingProfile(
            container = "ts",
            type = DlnaProfileType.VIDEO,
            videoCodec = "h264",
            audioCodec = "aac",
            protocol = MediaStreamProtocol.HLS,
            context = EncodingContext.STREAMING,
            transcodeSeekInfo = TranscodeSeekInfo.AUTO,
            maxAudioChannels = "6",
            conditions = emptyList(),
        ),
    ),
    containerProfiles = emptyList(),
    codecProfiles = emptyList<CodecProfile>(),
    subtitleProfiles = listOf("srt", "subrip", "vtt", "ass", "ssa")
        .map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL) },
    maxStreamingBitrate = 120_000_000,
)
