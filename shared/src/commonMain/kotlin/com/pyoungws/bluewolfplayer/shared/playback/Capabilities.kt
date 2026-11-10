package com.pyoungws.bluewolfplayer.shared.playback

/** Video codecs the player knows about. [jellyfinName] is the codec name used by the Jellyfin server. */
enum class VideoCodec(val jellyfinName: String, val label: String) {
    H264("h264", "H.264"),
    Hevc("hevc", "HEVC"),
    Av1("av1", "AV1"),
    Vp9("vp9", "VP9"),
    Vp8("vp8", "VP8"),
    Mpeg2("mpeg2video", "MPEG-2"),
    Mpeg4("mpeg4", "MPEG-4"),
    Vc1("vc1", "VC-1");

    companion object {
        fun fromJellyfin(name: String?): VideoCodec? = when (val codec = name?.lowercase()) {
            null -> null
            "h265" -> Hevc
            "avc" -> H264
            else -> entries.firstOrNull { it.jellyfinName == codec }
        }
    }
}

/** Audio codecs the player knows about. [passthrough] is the encoded output format that carries the codec bit-perfectly. */
enum class AudioCodec(val jellyfinName: String, val label: String, val passthrough: PassthroughFormat? = null) {
    Aac("aac", "AAC"),
    Mp3("mp3", "MP3"),
    Mp2("mp2", "MP2"),
    Ac3("ac3", "Dolby Digital", PassthroughFormat.DolbyDigital),
    Eac3("eac3", "Dolby Digital Plus", PassthroughFormat.DolbyDigitalPlus),
    TrueHd("truehd", "Dolby TrueHD", PassthroughFormat.DolbyTrueHd),
    Dts("dts", "DTS", PassthroughFormat.Dts),
    Flac("flac", "FLAC"),
    Opus("opus", "Opus"),
    Vorbis("vorbis", "Vorbis"),
    Alac("alac", "ALAC"),
    Pcm("pcm", "PCM");

    companion object {
        fun fromJellyfin(name: String?): AudioCodec? {
            val codec = name?.lowercase() ?: return null
            return when {
                codec.startsWith("pcm") -> Pcm
                codec == "dca" -> Dts
                codec == "mlp" -> TrueHd
                else -> entries.firstOrNull { it.jellyfinName == codec }
            }
        }
    }
}

/**
 * Encoded audio formats that can be sent untouched to an HDMI receiver. Atmos rides inside Dolby
 * Digital Plus (JOC) or TrueHD, and DTS:X inside DTS-HD MA, so those need the carrier format.
 */
enum class PassthroughFormat(val label: String) {
    DolbyDigital("Dolby Digital"),
    DolbyDigitalPlus("Dolby Digital Plus"),
    DolbyDigitalPlusAtmos("Dolby Digital Plus with Atmos"),
    DolbyTrueHd("Dolby TrueHD / Atmos"),
    Dts("DTS"),
    DtsHd("DTS-HD MA / DTS:X"),
}

enum class HdrType(val label: String) {
    Hdr10("HDR10"),
    Hdr10Plus("HDR10+"),
    Hlg("HLG"),
    DolbyVision("Dolby Vision"),
}

/** The best platform (MediaCodec) decoder found for one video codec. */
data class VideoDecoderCapability(
    val codec: VideoCodec,
    val decoderName: String,
    val isHardware: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val supports10Bit: Boolean,
)

data class DisplayMode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * What the device, its display and its audio output can do right now. Detected at runtime; the
 * display and audio parts change when an HDMI display or receiver is connected.
 */
data class DeviceCapabilities(
    val videoDecoders: List<VideoDecoderCapability> = emptyList(),
    /** Dolby Vision profile numbers (5, 7, 8, ...) the device's Dolby Vision decoder accepts. */
    val dolbyVisionProfiles: Set<Int> = emptySet(),
    /** HDR formats the connected display reports. */
    val displayHdrTypes: Set<HdrType> = emptySet(),
    val displayModes: List<DisplayMode> = emptyList(),
    val activeDisplayModeId: Int? = null,
    /** Audio codecs with a platform decoder. */
    val audioDecoders: Set<AudioCodec> = emptySet(),
    /** Encoded formats the current audio output (HDMI receiver, TV) accepts. */
    val passthrough: Set<PassthroughFormat> = emptySet(),
    /** Most PCM channels the current audio output takes. */
    val maxPcmChannels: Int = 2,
    /** Codecs the bundled FFmpeg can decode in software. */
    val localVideoCodecs: Set<VideoCodec> = emptySet(),
    val localAudioCodecs: Set<AudioCodec> = emptySet(),
    val isTv: Boolean = false,
) {
    fun videoDecoder(codec: VideoCodec): VideoDecoderCapability? = videoDecoders.firstOrNull { it.codec == codec }
}

/** Supplies [DeviceCapabilities]. Detection is cheap enough to repeat before each playback. */
fun interface CapabilityDetector {
    fun detect(): DeviceCapabilities
}
