package com.pyoungws.cobaltwolf.shared.playback

/** Largest picture the FFmpeg software decoder is trusted to decode in real time. */
const val LOCAL_DECODE_MAX_WIDTH = 1920
const val LOCAL_DECODE_MAX_HEIGHT = 1088

/**
 * The dynamic range of a video stream, as Jellyfin classifies it. Dolby Vision variants name the
 * layer a non-Dolby-Vision device can play instead ([base]); profile 5 has none.
 */
enum class VideoRange(val label: String, val isDolbyVision: Boolean = false, val base: VideoRange? = null) {
    Sdr("SDR"),
    Hdr10("HDR10"),
    Hdr10Plus("HDR10+"),
    Hlg("HLG"),
    DolbyVision("Dolby Vision (profile 5)", isDolbyVision = true),
    DolbyVisionWithHdr10("Dolby Vision (HDR10 compatible)", isDolbyVision = true, base = Hdr10),
    DolbyVisionWithHlg("Dolby Vision (HLG compatible)", isDolbyVision = true, base = Hlg),
    DolbyVisionWithSdr("Dolby Vision (SDR compatible)", isDolbyVision = true, base = Sdr),
    DolbyVisionWithEl("Dolby Vision (profile 7, HDR10 compatible)", isDolbyVision = true, base = Hdr10),
    DolbyVisionWithHdr10Plus("Dolby Vision (HDR10+ compatible)", isDolbyVision = true, base = Hdr10Plus),
    DolbyVisionWithElHdr10Plus("Dolby Vision (profile 7, HDR10+ compatible)", isDolbyVision = true, base = Hdr10Plus);

    /** The Dolby Vision profile a decoder must support to play this range as Dolby Vision. */
    fun dolbyVisionProfile(codec: VideoCodec): Int? = when {
        !isDolbyVision -> null
        codec == VideoCodec.Av1 -> 10
        codec == VideoCodec.H264 -> 9
        this == DolbyVision -> 5
        this == DolbyVisionWithEl || this == DolbyVisionWithElHdr10Plus -> 7
        else -> 8
    }
}

/** What actually reaches the display for a stream. */
enum class HdrOutput(val label: String) {
    Sdr("SDR"),
    Hdr10("HDR10"),
    Hdr10Plus("HDR10+"),
    Hlg("HLG"),
    DolbyVision("Dolby Vision"),
}

/** How one stream is handled, best first. */
enum class DecodePath { Passthrough, Device, Local, Transcode }

/** What can be played without the server for one video codec, after applying the user's overrides. */
data class VideoSupport(
    val codec: VideoCodec,
    val path: DecodePath,
    val decoderName: String?,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitDepth: Int,
    val ranges: Set<VideoRange>,
)

/**
 * How [range] would be shown on this device, or null when it cannot be shown correctly and the
 * server has to tone-map it. HDR needs the device's own decoder and an HDR display; Dolby Vision
 * falls back to its base layer when the decoder or display has no (matching) Dolby Vision support.
 */
fun hdrOutput(range: VideoRange, codec: VideoCodec, capabilities: DeviceCapabilities, settings: PlaybackSettings): HdrOutput? {
    if (range == VideoRange.Sdr) return HdrOutput.Sdr
    // Per-format overrides: a disabled HDR format falls back to its SDR/lower base layer.
    val base = if (range.isDolbyVision) range.base else range
    when (base) {
        VideoRange.Hdr10, VideoRange.Hdr10Plus -> if (!settings.allowHdr10) {
            return if (range.base == VideoRange.Sdr) HdrOutput.Sdr else null
        }
        VideoRange.Hlg -> if (!settings.allowHlg) {
            return if (range.base == VideoRange.Sdr) HdrOutput.Sdr else null
        }
        else -> Unit
    }
    val tenBitDecoder = capabilities.videoDecoder(codec)?.supports10Bit == true
    if (settings.hdr == HdrPreference.SdrOnly) {
        return if (range.base == VideoRange.Sdr && tenBitDecoder) HdrOutput.Sdr else null
    }
    if (!tenBitDecoder) return null
    val display = capabilities.displayHdrTypes
    val profile = range.dolbyVisionProfile(codec)
    if (profile != null && canOutputDolbyVision(profile, capabilities, settings)) return HdrOutput.DolbyVision
    return when (if (range.isDolbyVision) range.base else range) {
        VideoRange.Sdr -> HdrOutput.Sdr
        VideoRange.Hdr10 -> HdrOutput.Hdr10.takeIf { HdrType.Hdr10 in display }
        // HDR10+ carries a plain HDR10 layer, so an HDR10-only display still shows it.
        VideoRange.Hdr10Plus -> when {
            HdrType.Hdr10Plus in display -> HdrOutput.Hdr10Plus
            HdrType.Hdr10 in display -> HdrOutput.Hdr10
            else -> null
        }
        VideoRange.Hlg -> HdrOutput.Hlg.takeIf { HdrType.Hlg in display }
        else -> null
    }
}

/** Whether a Dolby Vision stream of [profile] is decoded and shown as Dolby Vision on this device. */
fun canOutputDolbyVision(profile: Int, capabilities: DeviceCapabilities, settings: PlaybackSettings): Boolean =
    settings.dolbyVision && HdrType.DolbyVision in capabilities.displayHdrTypes && profile in capabilities.dolbyVisionProfiles

/** A Dolby Vision track as the container declares it: its profile and the codec of its backwards-compatible layer, if it has one. */
data class DolbyVisionTrack(val profile: Int, val baseLayer: VideoCodec?)

/**
 * Reads a Dolby Vision codecs string such as "dvhe.08.06". Profiles 4, 7 and 8 sit on an HEVC
 * layer any HEVC decoder can play, profile 9 on H.264 and profile 10 on AV1; profile 5 (and the
 * other single-layer IPT profiles) cannot be shown without Dolby Vision processing.
 */
fun parseDolbyVisionCodecs(codecs: String?): DolbyVisionTrack? {
    val parts = codecs?.trim()?.split('.') ?: return null
    if (parts.size < 2 || parts[0] !in setOf("dvhe", "dvh1", "dvav", "dva1", "dav1")) return null
    val profile = parts[1].toIntOrNull() ?: return null
    val base = when (profile) {
        4, 7, 8 -> VideoCodec.Hevc
        9 -> VideoCodec.H264
        10 -> VideoCodec.Av1
        else -> null
    }
    return DolbyVisionTrack(profile, base)
}

/**
 * The codec to decode a Dolby Vision track as when it will not be played as Dolby Vision here
 * (no decoder for its profile, no Dolby Vision display, or turned off): its base layer. Null
 * means leave the track alone, either because it plays as Dolby Vision or has no base layer.
 */
fun dolbyVisionBaseLayerFallback(codecs: String?, capabilities: DeviceCapabilities, settings: PlaybackSettings): VideoCodec? {
    val track = parseDolbyVisionCodecs(codecs) ?: return null
    return if (canOutputDolbyVision(track.profile, capabilities, settings)) null else track.baseLayer
}

/**
 * The best way to play [codec] without the server, or null when it needs a server transcode.
 *
 * When the server will not re-encode video for this user ([serverTranscodesVideo] false) it would
 * only repackage the same stream, so nothing is held back: any size, bit depth and range is
 * claimed and the player does what it can with it.
 */
fun videoSupport(
    codec: VideoCodec,
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
    serverTranscodesVideo: Boolean = true,
): VideoSupport? {
    if (settings.decoding == DecodingPreference.AlwaysTranscode) return null
    val device = capabilities.videoDecoder(codec)
    val local = codec in capabilities.localVideoCodecs
    if (device == null && !local) return null
    val useLocal = device == null || (local && settings.decoding == DecodingPreference.PreferLocal)
    if (!serverTranscodesVideo) {
        return VideoSupport(
            codec = codec,
            path = if (useLocal) DecodePath.Local else DecodePath.Device,
            decoderName = device?.decoderName?.takeIf { !useLocal },
            maxWidth = Int.MAX_VALUE,
            maxHeight = Int.MAX_VALUE,
            maxBitDepth = Int.MAX_VALUE,
            ranges = VideoRange.entries.toSet(),
        )
    }
    return VideoSupport(
        codec = codec,
        path = if (useLocal) DecodePath.Local else DecodePath.Device,
        decoderName = device?.decoderName?.takeIf { !useLocal },
        maxWidth = maxOf(device?.maxWidth ?: 0, if (local) LOCAL_DECODE_MAX_WIDTH else 0),
        maxHeight = maxOf(device?.maxHeight ?: 0, if (local) LOCAL_DECODE_MAX_HEIGHT else 0),
        maxBitDepth = if (device?.supports10Bit == true || local) 10 else 8,
        ranges = VideoRange.entries.filterTo(mutableSetOf()) { hdrOutput(it, codec, capabilities, settings) != null },
    )
}

/** The best way to play [codec]: passthrough, a platform decoder, FFmpeg, then the server. */
fun audioPath(codec: AudioCodec, capabilities: DeviceCapabilities, settings: PlaybackSettings): DecodePath {
    if (settings.decoding == DecodingPreference.AlwaysTranscode) return DecodePath.Transcode
    val local = codec in capabilities.localAudioCodecs
    val preferLocal = local && settings.decoding == DecodingPreference.PreferLocal
    val passthroughAllowed = when (codec) {
        AudioCodec.Ac3, AudioCodec.Eac3 -> settings.passthroughAc3Eac3
        AudioCodec.Dts -> settings.passthroughDts
        AudioCodec.TrueHd -> settings.passthroughTrueHd
        else -> true
    }
    return when {
        !preferLocal && settings.audioOutput == AudioOutputPreference.Auto && passthroughAllowed &&
            codec.passthrough != null && codec.passthrough in capabilities.passthrough -> DecodePath.Passthrough
        preferLocal -> DecodePath.Local
        codec == AudioCodec.Pcm || codec in capabilities.audioDecoders -> DecodePath.Device
        local -> DecodePath.Local
        else -> DecodePath.Transcode
    }
}

/** Whether frame-rate matching may switch to a display mode that blanks the screen. */
fun PlaybackSettings.allowsDisplayModeSwitch(isTv: Boolean): Boolean =
    frameRateMatching == FrameRateMatching.Auto && isTv

// --- Fallback -------------------------------------------------------------------------------

enum class DecoderChoice { Device, Local }

/**
 * Which decoders the player uses for the original streams. [DecoderChoice.Local] is FFmpeg; for
 * audio it also means PCM output, because passthrough is the thing that failed.
 */
data class DecodePlan(val video: DecoderChoice = DecoderChoice.Device, val audio: DecoderChoice = DecoderChoice.Device) {
    val isLocal: Boolean get() = video == DecoderChoice.Local || audio == DecoderChoice.Local
}

/** What went wrong in the player, as far as the fallback order cares. */
enum class PlayerErrorKind { VideoDecoder, AudioDecoder, AudioOutput, Source, Other }

sealed interface FallbackStep {
    /** Replay the same stream with FFmpeg doing the decoding that failed. */
    data class LocalDecode(val plan: DecodePlan) : FallbackStep
    data object Transcode : FallbackStep
    data object Fail : FallbackStep
}

/**
 * The fallback order after a player error: direct play, then local FFmpeg decode, then a server
 * transcode. Local decode is skipped when FFmpeg cannot do the stream justice (no decoder, larger
 * than it can decode in real time, or HDR it cannot tone-map) and the server can do better; when
 * there is no server to fall back to, or the server does not re-encode video for this user
 * ([serverTranscodesVideo] false), it is tried anyway.
 */
fun nextFallback(
    method: PlaybackMethod,
    plan: DecodePlan,
    error: PlayerErrorKind,
    info: SourceMediaInfo?,
    capabilities: DeviceCapabilities,
    canTranscode: Boolean,
    serverTranscodesVideo: Boolean = true,
): FallbackStep {
    val transcode = if (canTranscode && method != PlaybackMethod.Transcode) FallbackStep.Transcode else FallbackStep.Fail
    val strict = transcode == FallbackStep.Transcode && serverTranscodesVideo
    return when {
        error == PlayerErrorKind.VideoDecoder && plan.video == DecoderChoice.Device &&
            canDecodeVideoLocally(info?.video, capabilities, strict) -> FallbackStep.LocalDecode(plan.copy(video = DecoderChoice.Local))
        (error == PlayerErrorKind.AudioDecoder || error == PlayerErrorKind.AudioOutput) && plan.audio == DecoderChoice.Device &&
            canDecodeAudioLocally(info?.audio, capabilities) -> FallbackStep.LocalDecode(plan.copy(audio = DecoderChoice.Local))
        else -> transcode
    }
}

private fun canDecodeVideoLocally(video: VideoStreamInfo?, capabilities: DeviceCapabilities, strict: Boolean): Boolean {
    if (capabilities.localVideoCodecs.isEmpty()) return false
    if (video == null) return true
    val codec = VideoCodec.fromJellyfin(video.codec)
    if (video.codec != null && codec !in capabilities.localVideoCodecs) return false
    if (!strict) return true
    val fits = (video.width ?: 0) <= LOCAL_DECODE_MAX_WIDTH && (video.height ?: 0) <= LOCAL_DECODE_MAX_HEIGHT
    return fits && (video.range == null || video.range == VideoRange.Sdr)
}

private fun canDecodeAudioLocally(audio: AudioStreamInfo?, capabilities: DeviceCapabilities): Boolean {
    if (capabilities.localAudioCodecs.isEmpty()) return false
    return audio?.codec == null || AudioCodec.fromJellyfin(audio.codec) in capabilities.localAudioCodecs
}

/** The plan playback starts with under the user's settings. */
fun initialDecodePlan(settings: PlaybackSettings): DecodePlan =
    if (settings.decoding == DecodingPreference.PreferLocal) DecodePlan(DecoderChoice.Local, DecoderChoice.Local) else DecodePlan()
