package com.pyoungws.bluewolfplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.playback.AudioCodec
import com.pyoungws.bluewolfplayer.shared.playback.AudioOutputPreference
import com.pyoungws.bluewolfplayer.shared.playback.DecodePath
import com.pyoungws.bluewolfplayer.shared.playback.DecodingPreference
import com.pyoungws.bluewolfplayer.shared.playback.DeviceCapabilities
import com.pyoungws.bluewolfplayer.shared.playback.FrameRateMatching
import com.pyoungws.bluewolfplayer.shared.playback.HdrPreference
import com.pyoungws.bluewolfplayer.shared.playback.StreamBitrate
import com.pyoungws.bluewolfplayer.shared.playback.LOCAL_DECODE_MAX_HEIGHT
import com.pyoungws.bluewolfplayer.shared.playback.LOCAL_DECODE_MAX_WIDTH
import com.pyoungws.bluewolfplayer.shared.playback.VideoCodec
import com.pyoungws.bluewolfplayer.shared.playback.VideoSupport
import com.pyoungws.bluewolfplayer.shared.presentation.HdrSelection
import com.pyoungws.bluewolfplayer.shared.presentation.PlaybackSettingsUiState
import com.pyoungws.bluewolfplayer.shared.presentation.PlaybackSettingsViewModel
import com.pyoungws.bluewolfplayer.ui.common.tvListScroll
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

/**
 * Lists what was detected on the device (decoders, HDR types, passthrough, channels), what the
 * player will therefore do per format, and the manual overrides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit, viewModel: PlaybackSettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The display and receiver can change while the screen is in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playback_settings_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.playback_refresh)) } },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).tvListScroll(listState), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Header(R.string.playback_section_overrides) }
            item { Overrides(state, viewModel) }
            item { Header(R.string.playback_section_detected) }
            item { Detected(state.capabilities) }
            item { Header(R.string.playback_section_selection) }
            item { Selection(state) }
        }
    }
}

@Composable
private fun Overrides(state: PlaybackSettingsUiState, viewModel: PlaybackSettingsViewModel) {
    val settings = state.settings
    Column {
        Choice(
            R.string.playback_decoding, settings.decoding, viewModel::setDecoding,
            DecodingPreference.Auto to R.string.playback_decoding_auto,
            DecodingPreference.PreferLocal to R.string.playback_decoding_local,
            DecodingPreference.AlwaysTranscode to R.string.playback_decoding_transcode,
        )
        Choice(
            R.string.playback_hdr, settings.hdr, viewModel::setHdr,
            HdrPreference.Auto to R.string.playback_hdr_auto,
            HdrPreference.SdrOnly to R.string.playback_hdr_sdr,
        )
        Toggle(R.string.playback_dolby_vision, R.string.playback_dolby_vision_summary, settings.dolbyVision, viewModel::setDolbyVision)
        Choice(
            R.string.playback_audio_output, settings.audioOutput, viewModel::setAudioOutput,
            AudioOutputPreference.Auto to R.string.playback_audio_auto,
            AudioOutputPreference.PcmOnly to R.string.playback_audio_pcm,
        )
        Choice(
            R.string.playback_frame_rate, settings.frameRateMatching, viewModel::setFrameRateMatching,
            FrameRateMatching.Auto to R.string.playback_frame_rate_auto,
            FrameRateMatching.SeamlessOnly to R.string.playback_frame_rate_seamless,
            FrameRateMatching.Off to R.string.playback_frame_rate_off,
        )
        Choice(
            R.string.playback_max_bitrate, settings.maxBitrate, viewModel::setMaxBitrate,
            StreamBitrate.Auto to R.string.playback_bitrate_auto,
            StreamBitrate.Mbps120 to R.string.playback_bitrate_120,
            StreamBitrate.Mbps80 to R.string.playback_bitrate_80,
            StreamBitrate.Mbps60 to R.string.playback_bitrate_60,
            StreamBitrate.Mbps40 to R.string.playback_bitrate_40,
            StreamBitrate.Mbps20 to R.string.playback_bitrate_20,
            StreamBitrate.Mbps10 to R.string.playback_bitrate_10,
            StreamBitrate.Mbps5 to R.string.playback_bitrate_5,
            StreamBitrate.Mbps2 to R.string.playback_bitrate_2,
        )
        Toggle(R.string.playback_styled_subtitles, R.string.playback_styled_subtitles_summary, settings.styledSubtitles, viewModel::setStyledSubtitles)
        OutlinedButton(onClick = viewModel::resetToAutomatic, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.playback_reset))
        }
    }
}

@Composable
private fun Detected(capabilities: DeviceCapabilities) {
    val none = stringResource(R.string.playback_none)
    Column {
        Fact(R.string.playback_video_decoders, capabilities.videoDecoders.map {
            stringResource(
                R.string.playback_decoder_line, it.codec.label, it.decoderName,
                stringResource(if (it.isHardware) R.string.playback_hardware else R.string.playback_software),
                it.maxWidth, it.maxHeight, stringResource(if (it.supports10Bit) R.string.playback_10_bit else R.string.playback_8_bit),
            )
        }.ifEmpty { listOf(none) })
        Fact(R.string.playback_ffmpeg_video, listOf(capabilities.localVideoCodecs.joinToString { it.label }.ifEmpty { none }))
        Fact(R.string.playback_dolby_vision_profiles, listOf(capabilities.dolbyVisionProfiles.sorted().joinToString().ifEmpty { none }))
        Fact(R.string.playback_display_hdr, listOf(capabilities.displayHdrTypes.joinToString { it.label }.ifEmpty { stringResource(R.string.playback_none_sdr) }))
        Fact(R.string.playback_display_modes, capabilities.displayModes.map {
            val active = if (it.id == capabilities.activeDisplayModeId) " ✓" else ""
            stringResource(R.string.playback_display_mode, it.width, it.height, String.format(Locale.US, "%.3f", it.refreshRate)) + active
        }.ifEmpty { listOf(none) })
        Fact(R.string.playback_passthrough, listOf(capabilities.passthrough.joinToString { it.label }.ifEmpty { stringResource(R.string.playback_none_pcm) }))
        Fact(R.string.playback_max_channels, listOf(capabilities.maxPcmChannels.toString()))
        Fact(R.string.playback_audio_decoders, listOf(capabilities.audioDecoders.joinToString { it.label }.ifEmpty { none }))
        Fact(R.string.playback_ffmpeg_audio, listOf(capabilities.localAudioCodecs.joinToString { it.label }.ifEmpty { none }))
    }
}

@Composable
private fun Selection(state: PlaybackSettingsUiState) {
    Column {
        Fact(R.string.playback_selection_video, state.video.map { (codec, support) -> "${codec.label}: ${videoPath(codec, support, state.capabilities)}" })
        Fact(R.string.playback_selection_hdr, state.hdr.map { "${it.range.label}: ${hdrPath(it)}" })
        Fact(R.string.playback_selection_audio, state.audio.map { (codec, path) -> "${codec.label}: ${audioPath(codec, path)}" })
    }
}

@Composable
private fun videoPath(codec: VideoCodec, support: VideoSupport?, capabilities: DeviceCapabilities): String {
    val local = stringResource(R.string.playback_path_local, LOCAL_DECODE_MAX_WIDTH, LOCAL_DECODE_MAX_HEIGHT)
    val device = capabilities.videoDecoder(codec)
    return when {
        support == null -> stringResource(R.string.playback_path_transcode)
        support.path == DecodePath.Local || device == null -> local
        else -> {
            val deviceLine = stringResource(
                R.string.playback_path_device, device.maxWidth, device.maxHeight,
                stringResource(if (device.supports10Bit) R.string.playback_10_bit else R.string.playback_8_bit),
            )
            // FFmpeg takes what the device decoder cannot, such as 10-bit on an 8-bit decoder.
            if (codec in capabilities.localVideoCodecs) stringResource(R.string.playback_path_device_then_local, deviceLine, local) else deviceLine
        }
    }
}

@Composable
private fun hdrPath(selection: HdrSelection): String = selection.output?.label ?: stringResource(R.string.playback_path_tone_map)

@Composable
private fun audioPath(codec: AudioCodec, path: DecodePath): String = stringResource(
    when (path) {
        DecodePath.Passthrough -> R.string.playback_audio_passthrough
        DecodePath.Device -> R.string.playback_audio_device
        DecodePath.Local -> R.string.playback_audio_local
        DecodePath.Transcode -> R.string.playback_path_transcode
    },
)

@Composable
private fun Header(title: Int) {
    Column {
        HorizontalDivider()
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Fact(title: Int, lines: List<String>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun <T> Choice(title: Int, selected: T, onSelect: (T) -> Unit, vararg options: Pair<T, Int>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(12.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Toggle(title: Int, summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
