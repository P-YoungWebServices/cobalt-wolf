package com.pyoungws.bluewolfplayer.shared.playback

enum class AudioOutputMode { Passthrough, Pcm }

enum class DecoderKind { Hardware, Software, Ffmpeg }

/** A live snapshot of what the player is doing, for the stats overlay and test logs. */
data class PlaybackStats(
    val level: PlaybackLevel? = null,
    val videoCodec: String? = null,
    val videoDecoder: String? = null,
    val videoDecoderKind: DecoderKind? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    /** Bits per second of the whole stream (or of the video track when the container says). */
    val bitrate: Int? = null,
    val hdr: String? = null,
    val audioCodec: String? = null,
    val audioDecoder: String? = null,
    val audioDecoderKind: DecoderKind? = null,
    val audioOutput: AudioOutputMode? = null,
    /** The format handed to the audio output, e.g. "Dolby TrueHD" or "PCM 16-bit". */
    val audioOutputFormat: String? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val droppedFrames: Int = 0,
    val displayRefreshRate: Float? = null,
)

/**
 * The fallback level actually in effect: a stream FFmpeg decodes is a local decode even when the
 * player chose FFmpeg up front (no device decoder) rather than after a failure.
 */
fun effectiveLevel(method: PlaybackMethod, videoDecoder: DecoderKind?, audioDecoder: DecoderKind?): PlaybackLevel = when {
    method == PlaybackMethod.Transcode -> PlaybackLevel.Transcode
    videoDecoder == DecoderKind.Ffmpeg || audioDecoder == DecoderKind.Ffmpeg -> PlaybackLevel.LocalDecode
    else -> PlaybackLevel.DirectPlay
}

/** One line with every field, for logs. */
fun PlaybackStats.summary(): String = listOf(
    "level" to level,
    "video" to videoCodec,
    "size" to if (width != null && height != null) "${width}x$height" else null,
    "fps" to frameRate,
    "bitrate" to bitrate,
    "hdr" to hdr,
    "videoDecoder" to videoDecoder,
    "videoDecoderKind" to videoDecoderKind,
    "audio" to audioCodec,
    "audioDecoder" to audioDecoder,
    "audioDecoderKind" to audioDecoderKind,
    "audioOutput" to audioOutput,
    "audioFormat" to audioOutputFormat,
    "sampleRate" to sampleRate,
    "channels" to channels,
    "dropped" to droppedFrames,
    "displayHz" to displayRefreshRate,
).filter { it.second != null }.joinToString(" ") { "${it.first}=${it.second.toString().replace(' ', '_')}" }

/**
 * Names the dynamic range reaching the display. [source] is the server's classification (the only
 * place HDR10+ and the Dolby Vision flavour are known); [transfer] is what the player read from
 * the stream. A Dolby Vision track decoded by a plain decoder shows its base layer, and FFmpeg
 * output is always SDR.
 */
fun describeHdr(
    source: VideoRange?,
    trackIsDolbyVision: Boolean,
    dolbyVisionProfile: Int?,
    transfer: HdrOutput,
    dolbyVisionDecoder: Boolean,
    decoderKind: DecoderKind?,
): String {
    val base = when {
        source != null && source.isDolbyVision -> source.base?.label
        source != null -> source.label
        else -> null
    } ?: transfer.label
    val label = when {
        (trackIsDolbyVision || source?.isDolbyVision == true) && dolbyVisionDecoder ->
            "Dolby Vision" + (dolbyVisionProfile?.let { " (profile $it)" } ?: "")
        trackIsDolbyVision || source?.isDolbyVision == true -> "$base (Dolby Vision base layer)"
        else -> base
    }
    return if (decoderKind == DecoderKind.Ffmpeg && label != HdrOutput.Sdr.label) "$label, shown as SDR (software decode)" else label
}
