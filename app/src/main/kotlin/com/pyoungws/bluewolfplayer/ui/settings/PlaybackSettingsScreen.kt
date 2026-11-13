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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pyoungws.bluewolfplayer.shared.playback.SegmentBehavior
import com.pyoungws.bluewolfplayer.shared.playback.SubtitleBackground
import com.pyoungws.bluewolfplayer.shared.playback.SubtitleColor
import com.pyoungws.bluewolfplayer.shared.playback.SubtitlePlaybackMode
import com.pyoungws.bluewolfplayer.shared.playback.TranscodeCodec
import com.pyoungws.bluewolfplayer.shared.playback.ZoomMode
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettings
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
            item { Header(R.string.home_rows_title) }
            item { DisplayOptionsSection() }
            item { HomeRows(state, viewModel) }
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
        Choice(
            R.string.playback_subtitle_scale, settings.subtitleScale, viewModel::setSubtitleScale,
            0.75f to R.string.subtitle_scale_75,
            1f to R.string.subtitle_scale_100,
            1.25f to R.string.subtitle_scale_125,
            1.5f to R.string.subtitle_scale_150,
            2f to R.string.subtitle_scale_200,
        )
        Choice(
            R.string.playback_intro_behavior, settings.introBehavior, viewModel::setIntroBehavior,
            SegmentBehavior.Ask to R.string.segment_ask,
            SegmentBehavior.Auto to R.string.segment_auto,
            SegmentBehavior.Off to R.string.segment_off,
        )
        Choice(
            R.string.playback_outro_behavior, settings.outroBehavior, viewModel::setOutroBehavior,
            SegmentBehavior.Ask to R.string.segment_ask,
            SegmentBehavior.Auto to R.string.segment_auto,
            SegmentBehavior.Off to R.string.segment_off,
        )
        Choice(
            R.string.playback_next_up_cutoff, settings.nextUpCutoffDays, viewModel::setNextUpCutoffDays,
            0 to R.string.cutoff_off,
            90 to R.string.cutoff_90,
            180 to R.string.cutoff_180,
            365 to R.string.cutoff_365,
            730 to R.string.cutoff_730,
        )
        Choice(
            R.string.settings_resume_rewind, settings.resumeRewindSeconds, viewModel::setResumeRewindSeconds,
            0 to R.string.settings_seconds_0,
            5 to R.string.settings_seconds_5,
            10 to R.string.settings_seconds_10,
            30 to R.string.settings_seconds_30,
        )
        Choice(
            R.string.settings_still_watching, settings.stillWatchingEpisodes, viewModel::setStillWatchingEpisodes,
            0 to R.string.cutoff_off,
            2 to R.string.settings_episodes_2,
            3 to R.string.settings_episodes_3,
            4 to R.string.settings_episodes_4,
        )
        Toggle(R.string.settings_cinema_mode, R.string.settings_cinema_mode_summary, settings.cinemaMode, viewModel::setCinemaMode)
        Toggle(R.string.settings_external_player, R.string.settings_external_player_summary, settings.externalPlayer, viewModel::setExternalPlayer)
        TextPref(R.string.settings_pref_audio_lang, R.string.settings_pref_audio_lang_summary, settings.preferredAudioLanguage, viewModel::setPreferredAudioLanguage)
        TextPref(R.string.settings_pref_sub_lang, R.string.settings_pref_sub_lang_summary, settings.preferredSubtitleLanguage, viewModel::setPreferredSubtitleLanguage)
        Choice(
            R.string.settings_subtitle_mode, settings.subtitleMode, viewModel::setSubtitleMode,
            SubtitlePlaybackMode.Default to R.string.subtitle_mode_default,
            SubtitlePlaybackMode.Always to R.string.subtitle_mode_always,
            SubtitlePlaybackMode.Forced to R.string.subtitle_mode_forced,
            SubtitlePlaybackMode.None to R.string.subtitle_mode_none,
        )
        Toggle(R.string.settings_pt_ac3, R.string.settings_pt_summary, settings.passthroughAc3Eac3, viewModel::setPassthroughAc3Eac3)
        Toggle(R.string.settings_pt_dts, R.string.settings_pt_summary, settings.passthroughDts, viewModel::setPassthroughDts)
        Toggle(R.string.settings_pt_truehd, R.string.settings_pt_summary, settings.passthroughTrueHd, viewModel::setPassthroughTrueHd)
        Choice(
            R.string.settings_audio_channel_cap, settings.audioChannelCap, viewModel::setAudioChannelCap,
            0 to R.string.channels_device, 2 to R.string.channels_2, 6 to R.string.channels_6, 8 to R.string.channels_8,
        )
        Toggle(R.string.settings_pgs_subs, R.string.settings_pgs_subs_summary, settings.pgsSubtitles, viewModel::setPgsSubtitles)
        Choice(
            R.string.settings_subtitle_color, settings.subtitleColor, viewModel::setSubtitleColor,
            SubtitleColor.White to R.string.color_white, SubtitleColor.Yellow to R.string.color_yellow,
            SubtitleColor.Green to R.string.color_green, SubtitleColor.Cyan to R.string.color_cyan,
        )
        Choice(
            R.string.settings_subtitle_bg, settings.subtitleBackground, viewModel::setSubtitleBackground,
            SubtitleBackground.Transparent to R.string.bg_transparent,
            SubtitleBackground.SemiTransparent to R.string.bg_semi,
            SubtitleBackground.Opaque to R.string.bg_opaque,
        )
        Toggle(R.string.settings_subtitle_bold, R.string.settings_subtitle_bold_summary, settings.subtitleBold, viewModel::setSubtitleBold)
        Toggle(R.string.settings_allow_hdr10, R.string.settings_allow_hdr_summary, settings.allowHdr10, viewModel::setAllowHdr10)
        Toggle(R.string.settings_allow_hlg, R.string.settings_allow_hdr_summary, settings.allowHlg, viewModel::setAllowHlg)
        Choice(
            R.string.settings_default_zoom, settings.defaultZoom, viewModel::setDefaultZoom,
            ZoomMode.Fit to R.string.zoom_fit, ZoomMode.Fill to R.string.zoom_fill, ZoomMode.Zoom to R.string.zoom_zoom, ZoomMode.Crop to R.string.zoom_crop,
        )
        Choice(
            R.string.settings_buffer_length, settings.bufferSeconds, viewModel::setBufferSeconds,
            0 to R.string.buffer_default, 120 to R.string.buffer_120, 240 to R.string.buffer_240, 480 to R.string.buffer_480,
        )
        Choice(
            R.string.settings_transcode_codec, settings.transcodeCodec, viewModel::setTranscodeCodec,
            TranscodeCodec.Auto to R.string.transcode_auto,
            TranscodeCodec.H264 to R.string.transcode_h264,
            TranscodeCodec.Hevc to R.string.transcode_hevc,
            TranscodeCodec.Av1 to R.string.transcode_av1,
        )
        Choice(
            R.string.settings_h264_level, settings.h264LevelCap, viewModel::setH264LevelCap,
            0 to R.string.level_max,
            40 to R.string.level_40, 41 to R.string.level_41, 42 to R.string.level_42,
            50 to R.string.level_50, 51 to R.string.level_51, 52 to R.string.level_52,
        )
        Choice(
            R.string.settings_hevc_level, settings.hevcLevelCap, viewModel::setHevcLevelCap,
            0 to R.string.level_max,
            120 to R.string.level_40, 123 to R.string.level_41, 150 to R.string.level_50,
            153 to R.string.level_51, 156 to R.string.level_52, 180 to R.string.level_60,
            183 to R.string.level_61, 186 to R.string.level_62,
        )
        OutlinedButton(onClick = viewModel::resetToAutomatic, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.playback_reset))
        }
    }
}

@Composable
private fun DisplayOptionsSection() {
    val (options, setOptions) = com.pyoungws.bluewolfplayer.ui.common.rememberDisplayOptions()
    Column {
        Toggle(R.string.display_backdrops, R.string.display_backdrops_summary, options.showBackdrops) { setOptions(options.copy(showBackdrops = it)) }
        Toggle(R.string.display_blur, R.string.display_blur_summary, options.blurBackdrops) { setOptions(options.copy(blurBackdrops = it)) }
        Toggle(R.string.display_watched_badge, R.string.display_watched_badge_summary, options.watchedBadge) { setOptions(options.copy(watchedBadge = it)) }
        Toggle(R.string.display_clock, R.string.display_clock_summary, options.clock) { setOptions(options.copy(clock = it)) }
        Choice(
            R.string.display_theme, options.theme, { setOptions(options.copy(theme = it)) },
            com.pyoungws.bluewolfplayer.ui.common.AppTheme.System to R.string.theme_system,
            com.pyoungws.bluewolfplayer.ui.common.AppTheme.Light to R.string.theme_light,
            com.pyoungws.bluewolfplayer.ui.common.AppTheme.Dark to R.string.theme_dark,
        )
    }
}

@Composable
private fun HomeRows(state: PlaybackSettingsUiState, viewModel: PlaybackSettingsViewModel) {
    val settings = state.settings
    val order = (settings.homeRowOrder + PlaybackSettingsViewModel.HOME_ROW_IDS).distinct()
    val labels = mapOf(
        PlaybackSettings.ROW_LIBRARIES to R.string.row_libraries,
        PlaybackSettings.ROW_CONTINUE to R.string.row_continue,
        PlaybackSettings.ROW_NEXT_UP to R.string.row_next_up,
        PlaybackSettings.ROW_RESUME_AUDIO to R.string.row_resume_audio,
        PlaybackSettings.ROW_LIVE_TV to R.string.row_live_tv,
    )
    Column {
        order.forEachIndexed { index, rowId ->
            val hidden = rowId in settings.hiddenHomeRows
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(labels[rowId] ?: R.string.row_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.moveHomeRow(rowId, -1) }, enabled = index > 0) { Text(stringResource(R.string.row_move_up)) }
                TextButton(onClick = { viewModel.moveHomeRow(rowId, 1) }, enabled = index < order.lastIndex) { Text(stringResource(R.string.row_move_down)) }
                TextButton(onClick = { viewModel.setHomeRowHidden(rowId, !hidden) }) {
                    Text(stringResource(if (hidden) R.string.filter_all else R.string.row_hidden))
                }
            }
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
private fun TextPref(title: Int, summary: Int, value: String?, onChange: (String?) -> Unit) {
    var text by remember { mutableStateOf(value.orEmpty()) }
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it.ifBlank { null }) },
        label = { Text(stringResource(title)) },
        supportingText = { Text(stringResource(summary)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
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
