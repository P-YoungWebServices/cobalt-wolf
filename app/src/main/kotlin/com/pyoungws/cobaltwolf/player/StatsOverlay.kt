package com.pyoungws.cobaltwolf.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.playback.AudioOutputMode
import com.pyoungws.cobaltwolf.shared.playback.DecoderKind
import com.pyoungws.cobaltwolf.shared.playback.PlaybackLevel
import com.pyoungws.cobaltwolf.shared.playback.PlaybackStats
import java.util.Locale

/** Live playback details: codec, bitrate, HDR type, audio output, sample rate, channels, dropped frames. */
@Composable
internal fun StatsOverlay(stats: PlaybackStats, modifier: Modifier = Modifier) {
    val unknown = stringResource(R.string.stats_unknown)
    val lines = listOf(
        R.string.stats_playback to stats.level?.let { stringResource(it.label()) },
        R.string.stats_video to stats.videoCodec?.let { codec ->
            listOfNotNull(
                codec,
                if (stats.width != null && stats.height != null) "${stats.width}×${stats.height}" else null,
                stats.frameRate?.let { String.format(Locale.US, "%.3f fps", it) },
            ).joinToString(" · ")
        },
        R.string.stats_video_decoder to stats.videoDecoder?.let { decoder(it, stats.videoDecoderKind) },
        R.string.stats_bitrate to stats.bitrate?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) },
        R.string.stats_hdr to stats.hdr,
        R.string.stats_display to stats.displayRefreshRate?.let { String.format(Locale.US, "%.3f Hz", it) },
        R.string.stats_audio to stats.audioCodec,
        R.string.stats_audio_output to stats.audioOutput?.let { mode ->
            val label = stringResource(if (mode == AudioOutputMode.Passthrough) R.string.stats_passthrough else R.string.stats_pcm)
            listOfNotNull(label, stats.audioOutputFormat.takeIf { mode == AudioOutputMode.Passthrough }).joinToString(" · ")
        },
        R.string.stats_audio_decoder to when {
            stats.audioDecoder != null -> decoder(stats.audioDecoder!!, stats.audioDecoderKind)
            stats.audioOutput == AudioOutputMode.Passthrough -> stringResource(R.string.stats_no_decoder)
            else -> null
        },
        R.string.stats_sample_rate to stats.sampleRate?.let { String.format(Locale.US, "%.1f kHz", it / 1000.0) },
        R.string.stats_channels to stats.channels?.toString(),
        R.string.stats_dropped_frames to stats.droppedFrames.toString(),
    )
    Column(modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(8.dp)) {
        lines.forEach { (label, value) ->
            Text(
                "${stringResource(label)}: ${value ?: unknown}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun decoder(name: String, kind: DecoderKind?): String = when (kind) {
    DecoderKind.Hardware -> stringResource(R.string.stats_decoder_hardware, name)
    DecoderKind.Software -> stringResource(R.string.stats_decoder_software, name)
    DecoderKind.Ffmpeg -> stringResource(R.string.stats_decoder_ffmpeg, name)
    null -> name
}

internal fun PlaybackLevel.label(): Int = when (this) {
    PlaybackLevel.DirectPlay -> R.string.level_direct_play
    PlaybackLevel.LocalDecode -> R.string.level_local_decode
    PlaybackLevel.Transcode -> R.string.level_transcode
}
