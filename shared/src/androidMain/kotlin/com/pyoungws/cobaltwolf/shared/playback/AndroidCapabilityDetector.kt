package com.pyoungws.cobaltwolf.shared.playback

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary

internal val VIDEO_MIME_TYPES = mapOf(
    VideoCodec.H264 to "video/avc",
    VideoCodec.Hevc to "video/hevc",
    VideoCodec.Av1 to "video/av01",
    VideoCodec.Vp9 to "video/x-vnd.on2.vp9",
    VideoCodec.Vp8 to "video/x-vnd.on2.vp8",
    VideoCodec.Mpeg2 to "video/mpeg2",
    VideoCodec.Mpeg4 to "video/mp4v-es",
    VideoCodec.Vc1 to "video/wvc1",
)

internal val AUDIO_MIME_TYPES = mapOf(
    AudioCodec.Aac to "audio/mp4a-latm",
    AudioCodec.Mp3 to "audio/mpeg",
    AudioCodec.Mp2 to "audio/mpeg-L2",
    AudioCodec.Ac3 to "audio/ac3",
    AudioCodec.Eac3 to "audio/eac3",
    AudioCodec.TrueHd to "audio/true-hd",
    AudioCodec.Dts to "audio/vnd.dts",
    AudioCodec.Flac to "audio/flac",
    AudioCodec.Opus to "audio/opus",
    AudioCodec.Vorbis to "audio/vorbis",
    AudioCodec.Alac to "audio/alac",
    AudioCodec.Pcm to "audio/raw",
)

private const val DOLBY_VISION_MIME_TYPE = "video/dolby-vision"

private val PASSTHROUGH_ENCODINGS = mapOf(
    PassthroughFormat.DolbyDigital to C.ENCODING_AC3,
    PassthroughFormat.DolbyDigitalPlus to C.ENCODING_E_AC3,
    PassthroughFormat.DolbyDigitalPlusAtmos to C.ENCODING_E_AC3_JOC,
    PassthroughFormat.DolbyTrueHd to C.ENCODING_DOLBY_TRUEHD,
    PassthroughFormat.Dts to C.ENCODING_DTS,
    PassthroughFormat.DtsHd to C.ENCODING_DTS_HD,
)

/** Movie playback attributes; the audio output's passthrough support depends on them. */
val MOVIE_AUDIO_ATTRIBUTES: AudioAttributes =
    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

/**
 * Reads what this device can play: MediaCodec decoders (fixed for the device, so scanned once),
 * the display's HDR formats and modes, the audio output's passthrough formats (both follow
 * whatever is connected over HDMI, so read each time) and the bundled FFmpeg decoders.
 */
class AndroidCapabilityDetector(context: Context) : CapabilityDetector {
    private val context = context.applicationContext
    private val decoders by lazy { scanDecoders() }
    private val localCodecs by lazy { scanFfmpeg() }

    private class Decoders(val video: List<VideoDecoderCapability>, val audio: Set<AudioCodec>, val dolbyVisionProfiles: Set<Int>)

    @OptIn(UnstableApi::class)
    override fun detect(): DeviceCapabilities {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val audio = AudioCapabilities.getCapabilities(context, MOVIE_AUDIO_ATTRIBUTES, null)
        return DeviceCapabilities(
            videoDecoders = decoders.video,
            dolbyVisionProfiles = decoders.dolbyVisionProfiles,
            displayHdrTypes = display?.let(::hdrTypes).orEmpty(),
            displayModes = display?.supportedModes.orEmpty().map { DisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) },
            activeDisplayModeId = display?.mode?.modeId,
            audioDecoders = decoders.audio,
            passthrough = PASSTHROUGH_ENCODINGS.filterValues(audio::supportsEncoding).keys,
            maxPcmChannels = audio.maxChannelCount,
            localVideoCodecs = localCodecs.first,
            localAudioCodecs = localCodecs.second,
            isTv = isTv(),
        )
    }

    private fun scanDecoders(): Decoders {
        val video = mutableMapOf<VideoCodec, VideoDecoderCapability>()
        val audio = mutableSetOf(AudioCodec.Pcm)
        val dolbyVision = mutableSetOf<Int>()
        val videoCodecs = VIDEO_MIME_TYPES.entries.associate { it.value to it.key }
        val audioCodecs = AUDIO_MIME_TYPES.entries.associate { it.value.lowercase() to it.key }
        for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (info.isEncoder) continue
            for (type in info.supportedTypes) {
                val mime = type.lowercase()
                val capabilities = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: continue
                audioCodecs[mime]?.let(audio::add)
                if (mime == DOLBY_VISION_MIME_TYPE) {
                    capabilities.profileLevels.mapTo(dolbyVision) { Integer.numberOfTrailingZeros(it.profile) }
                }
                val codec = videoCodecs[mime] ?: continue
                val size = capabilities.videoCapabilities ?: continue
                val candidate = VideoDecoderCapability(
                    codec = codec,
                    decoderName = info.name,
                    isHardware = info.isHardware(),
                    maxWidth = size.supportedWidths.upper,
                    maxHeight = size.supportedHeights.upper,
                    supports10Bit = capabilities.profileLevels.any { it.profile in TEN_BIT_PROFILES.getValue(codec) },
                )
                val current = video[codec]
                if (current == null || DECODER_ORDER.compare(candidate, current) > 0) video[codec] = candidate
            }
        }
        return Decoders(VideoCodec.entries.mapNotNull(video::get), audio, dolbyVision)
    }

    @OptIn(UnstableApi::class)
    private fun scanFfmpeg(): Pair<Set<VideoCodec>, Set<AudioCodec>> = try {
        if (FfmpegLibrary.isAvailable()) {
            VIDEO_MIME_TYPES.filterValues(FfmpegLibrary::supportsFormat).keys to
                AUDIO_MIME_TYPES.filterValues(FfmpegLibrary::supportsFormat).keys
        } else {
            emptySet<VideoCodec>() to emptySet()
        }
    } catch (_: LinkageError) {
        // The native library is missing for this ABI: no local decoding, everything else still works.
        emptySet<VideoCodec>() to emptySet()
    }

    @Suppress("DEPRECATION")
    private fun hdrTypes(display: Display): Set<HdrType> {
        val types = display.hdrCapabilities?.supportedHdrTypes?.toMutableSet() ?: mutableSetOf()
        if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes.forEach(types::add)
        return types.mapNotNullTo(mutableSetOf()) {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> HdrType.DolbyVision
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> HdrType.Hdr10
                Display.HdrCapabilities.HDR_TYPE_HLG -> HdrType.Hlg
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> HdrType.Hdr10Plus
                else -> null
            }
        }
    }

    private fun isTv(): Boolean {
        val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType
        val packages = context.packageManager
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            packages.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packages.hasSystemFeature("amazon.hardware.fire_tv")
    }
}

private val DECODER_ORDER = compareBy<VideoDecoderCapability>({ it.isHardware }, { it.maxWidth.toLong() * it.maxHeight })

private fun MediaCodecInfo.isHardware(): Boolean {
    if (Build.VERSION.SDK_INT >= 29) return isHardwareAccelerated
    val name = name.lowercase()
    return !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.startsWith("c2.google.")
}

private val TEN_BIT_PROFILES: Map<VideoCodec, Set<Int>> = mapOf(
    VideoCodec.H264 to setOf(CodecProfileLevel.AVCProfileHigh10),
    VideoCodec.Hevc to setOf(CodecProfileLevel.HEVCProfileMain10, CodecProfileLevel.HEVCProfileMain10HDR10, CodecProfileLevel.HEVCProfileMain10HDR10Plus),
    VideoCodec.Vp9 to setOf(
        CodecProfileLevel.VP9Profile2, CodecProfileLevel.VP9Profile3, CodecProfileLevel.VP9Profile2HDR,
        CodecProfileLevel.VP9Profile3HDR, CodecProfileLevel.VP9Profile2HDR10Plus, CodecProfileLevel.VP9Profile3HDR10Plus,
    ),
    VideoCodec.Av1 to setOf(CodecProfileLevel.AV1ProfileMain10, CodecProfileLevel.AV1ProfileMain10HDR10, CodecProfileLevel.AV1ProfileMain10HDR10Plus),
).withDefault { emptySet() }
