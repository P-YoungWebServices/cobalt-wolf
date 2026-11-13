@file:OptIn(UnstableApi::class)

package com.pyoungws.cobaltwolf.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.audio.AudioSink
import com.pyoungws.cobaltwolf.shared.playback.AudioOutputMode
import com.pyoungws.cobaltwolf.shared.playback.DecoderKind
import com.pyoungws.cobaltwolf.shared.playback.HdrOutput
import com.pyoungws.cobaltwolf.shared.playback.PlaybackMethod
import com.pyoungws.cobaltwolf.shared.playback.PlaybackRequest
import com.pyoungws.cobaltwolf.shared.playback.PlaybackStats
import com.pyoungws.cobaltwolf.shared.playback.describeHdr
import com.pyoungws.cobaltwolf.shared.playback.effectiveLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Turns player analytics events into [PlaybackStats] for the overlay. */
internal class PlaybackStatsCollector(context: Context, private val request: PlaybackRequest) : AnalyticsListener {
    private val context = context.applicationContext
    private val info = request.source.info

    /** The server's figures describe the original file, so they only apply while that is what plays. */
    private val sourceVideo = info?.video.takeIf { request.source.method != PlaybackMethod.Transcode }
    private val sourceBitrate = info?.bitrate.takeIf { request.source.method != PlaybackMethod.Transcode }
    private val _stats = MutableStateFlow(PlaybackStats(level = request.level, bitrate = sourceBitrate))
    val stats: StateFlow<PlaybackStats> = _stats.asStateFlow()

    private var videoFormat: Format? = null
    private var audioFormat: Format? = null

    /** True once the video stream is known to be HDR that is shown as HDR (not software decoded). */
    val isHdrOutput: Boolean
        get() = videoFormat?.let { it.transfer() != HdrOutput.Sdr || it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION } == true &&
            _stats.value.videoDecoderKind != DecoderKind.Ffmpeg

    override fun onVideoInputFormatChanged(eventTime: EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        videoFormat = format
        _stats.update {
            it.copy(
                videoCodec = listOfNotNull(format.sampleMimeType, format.codecs).joinToString(" "),
                width = format.width.takeIf { w -> w > 0 },
                height = format.height.takeIf { h -> h > 0 },
                frameRate = format.frameRate.takeIf { f -> f > 0 } ?: info?.video?.frameRate,
                bitrate = sourceBitrate ?: format.bitrate.takeIf { b -> b > 0 } ?: it.bitrate,
            ).withHdr()
        }
    }

    override fun onVideoDecoderInitialized(eventTime: EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        _stats.update { it.copy(videoDecoder = decoderName, videoDecoderKind = decoderKind(decoderName)).withHdr().withLevel() }
    }

    override fun onVideoDisabled(eventTime: EventTime, decoderCounters: DecoderCounters) {
        videoFormat = null
        _stats.update { it.copy(videoCodec = null, videoDecoder = null, videoDecoderKind = null, hdr = null) }
    }

    override fun onDroppedVideoFrames(eventTime: EventTime, droppedFrames: Int, elapsedMs: Long) {
        _stats.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
    }

    override fun onAudioInputFormatChanged(eventTime: EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        audioFormat = format
        _stats.update { it.copy(audioCodec = listOfNotNull(format.sampleMimeType, format.codecs).joinToString(" ")) }
    }

    override fun onAudioDecoderInitialized(eventTime: EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        _stats.update { it.copy(audioDecoder = decoderName, audioDecoderKind = decoderKind(decoderName)).withLevel() }
    }

    override fun onAudioDisabled(eventTime: EventTime, decoderCounters: DecoderCounters) {
        audioFormat = null
        _stats.update {
            it.copy(audioCodec = null, audioDecoder = null, audioDecoderKind = null, audioOutput = null, audioOutputFormat = null, sampleRate = null, channels = null)
        }
    }

    override fun onAudioTrackInitialized(eventTime: EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        val pcm = Util.isEncodingLinearPcm(audioTrackConfig.encoding)
        val inputChannels = audioFormat?.channelCount?.takeIf { it > 0 }
        _stats.update {
            it.copy(
                audioOutput = if (pcm) AudioOutputMode.Pcm else AudioOutputMode.Passthrough,
                audioOutputFormat = encodingLabel(audioTrackConfig.encoding),
                sampleRate = audioTrackConfig.sampleRate,
                // A passthrough track's channel mask describes the carrier, not the programme.
                channels = if (pcm) Integer.bitCount(audioTrackConfig.channelConfig) else inputChannels ?: info?.audio?.channels,
                // Passthrough starts no decoder; clear one left over from an earlier track.
                audioDecoder = if (pcm) it.audioDecoder else null,
                audioDecoderKind = if (pcm) it.audioDecoderKind else null,
            ).withLevel()
        }
    }

    /** Refreshes values that are polled rather than pushed. */
    fun refreshDisplay() {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        _stats.update { it.copy(displayRefreshRate = display?.mode?.refreshRate) }
    }

    private fun PlaybackStats.withLevel() = copy(level = effectiveLevel(request.source.method, videoDecoderKind, audioDecoderKind))

    private fun PlaybackStats.withHdr(): PlaybackStats {
        val format = videoFormat ?: return this
        val dolbyVisionTrack = format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        return copy(
            hdr = describeHdr(
                source = sourceVideo?.range,
                trackIsDolbyVision = dolbyVisionTrack,
                dolbyVisionProfile = sourceVideo?.dolbyVisionProfile ?: format.codecs?.let(::dolbyVisionProfile),
                transfer = format.transfer(),
                dolbyVisionDecoder = videoDecoder?.let(::isDolbyVisionDecoder) == true,
                decoderKind = videoDecoderKind,
            ),
        )
    }

    private fun decoderKind(name: String): DecoderKind {
        val lower = name.lowercase()
        if ("ffmpeg" in lower) return DecoderKind.Ffmpeg
        val hardware = if (Build.VERSION.SDK_INT >= 29) {
            codecInfos.firstOrNull { it.name == name }?.isHardwareAccelerated
        } else {
            null
        } ?: !(lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.startsWith("c2.google."))
        return if (hardware) DecoderKind.Hardware else DecoderKind.Software
    }

    private fun isDolbyVisionDecoder(name: String): Boolean =
        codecInfos.firstOrNull { it.name == name }?.supportedTypes?.any { it.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true) } == true

    private val codecInfos by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }
}

private fun Format.transfer(): HdrOutput = when (colorInfo?.colorTransfer) {
    C.COLOR_TRANSFER_ST2084 -> HdrOutput.Hdr10
    C.COLOR_TRANSFER_HLG -> HdrOutput.Hlg
    else -> HdrOutput.Sdr
}

/** "dvhe.08.06" -> 8. */
private fun dolbyVisionProfile(codecs: String): Int? =
    codecs.split('.').takeIf { it.size >= 2 && it[0].startsWith("dv") || it[0] == "dav1" }?.getOrNull(1)?.toIntOrNull()

private fun encodingLabel(encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_16BIT -> "PCM 16-bit"
    C.ENCODING_PCM_24BIT -> "PCM 24-bit"
    C.ENCODING_PCM_32BIT -> "PCM 32-bit"
    C.ENCODING_PCM_FLOAT -> "PCM float"
    C.ENCODING_PCM_8BIT -> "PCM 8-bit"
    C.ENCODING_AC3 -> "Dolby Digital"
    C.ENCODING_E_AC3 -> "Dolby Digital Plus"
    C.ENCODING_E_AC3_JOC -> "Dolby Digital Plus Atmos"
    C.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD"
    C.ENCODING_DTS -> "DTS"
    C.ENCODING_DTS_HD -> "DTS-HD"
    C.ENCODING_DTS_UHD_P2 -> "DTS:X"
    C.ENCODING_AC4 -> "Dolby AC-4"
    else -> if (Util.isEncodingLinearPcm(encoding)) "PCM" else "Encoded ($encoding)"
}
