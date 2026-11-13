package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import com.pyoungws.cobaltwolf.shared.playback.AudioCodec
import com.pyoungws.cobaltwolf.shared.playback.AudioOutputPreference
import com.pyoungws.cobaltwolf.shared.playback.CapabilityDetector
import com.pyoungws.cobaltwolf.shared.playback.DecodePath
import com.pyoungws.cobaltwolf.shared.playback.DecodingPreference
import com.pyoungws.cobaltwolf.shared.playback.DeviceCapabilities
import com.pyoungws.cobaltwolf.shared.playback.FrameRateMatching
import com.pyoungws.cobaltwolf.shared.playback.HdrOutput
import com.pyoungws.cobaltwolf.shared.playback.HdrPreference
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettings
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettingsRepository
import com.pyoungws.cobaltwolf.shared.playback.SubtitleBackground
import com.pyoungws.cobaltwolf.shared.playback.SubtitleColor
import com.pyoungws.cobaltwolf.shared.playback.SubtitlePlaybackMode
import com.pyoungws.cobaltwolf.shared.playback.TranscodeCodec
import com.pyoungws.cobaltwolf.shared.playback.ZoomMode
import com.pyoungws.cobaltwolf.shared.playback.SegmentBehavior
import com.pyoungws.cobaltwolf.shared.playback.StreamBitrate
import com.pyoungws.cobaltwolf.shared.playback.VideoCodec
import com.pyoungws.cobaltwolf.shared.playback.VideoRange
import com.pyoungws.cobaltwolf.shared.playback.VideoSupport
import com.pyoungws.cobaltwolf.shared.playback.audioPath
import com.pyoungws.cobaltwolf.shared.playback.hdrOutput
import com.pyoungws.cobaltwolf.shared.playback.videoSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How one HDR format ends up on screen: [output] is null when the server has to tone-map it. */
data class HdrSelection(val range: VideoRange, val output: HdrOutput?)

/**
 * What was detected on the device and what the player will do with it under the current
 * settings: per video codec, per audio codec and per HDR format.
 */
data class PlaybackSettingsUiState(
    val settings: PlaybackSettings,
    val capabilities: DeviceCapabilities,
    /** A null support means the codec is transcoded by the server. */
    val video: List<Pair<VideoCodec, VideoSupport?>>,
    val audio: List<Pair<AudioCodec, DecodePath>>,
    val hdr: List<HdrSelection>,
)

class PlaybackSettingsViewModel(
    private val repository: PlaybackSettingsRepository,
    private val detector: CapabilityDetector,
) : ViewModel() {
    private val _uiState = MutableStateFlow(state(repository.settings.value, detector.detect()))
    val uiState: StateFlow<PlaybackSettingsUiState> = _uiState.asStateFlow()

    /** Re-detects the device, e.g. after a display or receiver was connected. */
    fun refresh() {
        _uiState.value = state(repository.settings.value, detector.detect())
    }

    fun setDecoding(value: DecodingPreference) = update { it.copy(decoding = value) }
    fun setHdr(value: HdrPreference) = update { it.copy(hdr = value) }
    fun setDolbyVision(enabled: Boolean) = update { it.copy(dolbyVision = enabled) }
    fun setAudioOutput(value: AudioOutputPreference) = update { it.copy(audioOutput = value) }
    fun setFrameRateMatching(value: FrameRateMatching) = update { it.copy(frameRateMatching = value) }
    fun setMaxBitrate(value: StreamBitrate) = update { it.copy(maxBitrate = value) }
    fun setStyledSubtitles(enabled: Boolean) = update { it.copy(styledSubtitles = enabled) }
    fun setSubtitleScale(value: Float) = update { it.copy(subtitleScale = value) }
    fun setIntroBehavior(value: SegmentBehavior) = update { it.copy(introBehavior = value) }
    fun setOutroBehavior(value: SegmentBehavior) = update { it.copy(outroBehavior = value) }
    fun setNextUpCutoffDays(days: Int) = update { it.copy(nextUpCutoffDays = days) }
    fun setResumeRewindSeconds(seconds: Int) = update { it.copy(resumeRewindSeconds = seconds) }
    fun setStillWatchingEpisodes(count: Int) = update { it.copy(stillWatchingEpisodes = count) }
    fun setCinemaMode(enabled: Boolean) = update { it.copy(cinemaMode = enabled) }
    fun setExternalPlayer(enabled: Boolean) = update { it.copy(externalPlayer = enabled) }
    fun setPreferredAudioLanguage(value: String?) = update { it.copy(preferredAudioLanguage = value?.trim()?.ifBlank { null }) }
    fun setPreferredSubtitleLanguage(value: String?) = update { it.copy(preferredSubtitleLanguage = value?.trim()?.ifBlank { null }) }
    fun setSubtitleMode(value: SubtitlePlaybackMode) = update { it.copy(subtitleMode = value) }
    fun setPassthroughAc3Eac3(v: Boolean) = update { it.copy(passthroughAc3Eac3 = v) }
    fun setPassthroughDts(v: Boolean) = update { it.copy(passthroughDts = v) }
    fun setPassthroughTrueHd(v: Boolean) = update { it.copy(passthroughTrueHd = v) }
    fun setAudioChannelCap(v: Int) = update { it.copy(audioChannelCap = v) }
    fun setPgsSubtitles(v: Boolean) = update { it.copy(pgsSubtitles = v) }
    fun setSubtitleColor(v: SubtitleColor) = update { it.copy(subtitleColor = v) }
    fun setSubtitleBackground(v: SubtitleBackground) = update { it.copy(subtitleBackground = v) }
    fun setSubtitleBold(v: Boolean) = update { it.copy(subtitleBold = v) }
    fun setAllowHdr10(v: Boolean) = update { it.copy(allowHdr10 = v) }
    fun setAllowHlg(v: Boolean) = update { it.copy(allowHlg = v) }
    fun setDefaultZoom(v: ZoomMode) = update { it.copy(defaultZoom = v) }
    fun setBufferSeconds(v: Int) = update { it.copy(bufferSeconds = v) }
    fun setTranscodeCodec(v: TranscodeCodec) = update { it.copy(transcodeCodec = v) }
    fun setH264LevelCap(v: Int) = update { it.copy(h264LevelCap = v) }
    fun setHevcLevelCap(v: Int) = update { it.copy(hevcLevelCap = v) }

    fun setHomeRowHidden(rowId: String, hidden: Boolean) = update {
        it.copy(hiddenHomeRows = if (hidden) it.hiddenHomeRows + rowId else it.hiddenHomeRows - rowId)
    }

    fun moveHomeRow(rowId: String, delta: Int) = update { settings ->
        val order = (settings.homeRowOrder + HOME_ROW_IDS).distinct().toMutableList()
        val index = order.indexOf(rowId).coerceAtLeast(0)
        val target = (index + delta).coerceIn(0, order.lastIndex)
        if (index != target) {
            order.removeAt(index)
            order.add(target, rowId)
        }
        settings.copy(homeRowOrder = order)
    }

    fun resetToAutomatic() = update { PlaybackSettings() }

    private fun update(transform: (PlaybackSettings) -> PlaybackSettings) {
        repository.update(transform)
        _uiState.value = state(repository.settings.value, _uiState.value.capabilities)
    }

    companion object {
        /** All reorderable home rows. `recent:<libraryId>` rows stay in feed order after these. */
        val HOME_ROW_IDS = listOf(PlaybackSettings.ROW_LIBRARIES, PlaybackSettings.ROW_CONTINUE, PlaybackSettings.ROW_NEXT_UP, PlaybackSettings.ROW_RESUME_AUDIO, PlaybackSettings.ROW_LIVE_TV, PlaybackSettings.ROW_ON_NOW)
    }

    private fun state(settings: PlaybackSettings, capabilities: DeviceCapabilities) = PlaybackSettingsUiState(
        settings = settings,
        capabilities = capabilities,
        video = VideoCodec.entries.map { it to videoSupport(it, capabilities, settings) },
        audio = AudioCodec.entries.map { it to audioPath(it, capabilities, settings) },
        // HEVC carries nearly all HDR; it stands in for the summary.
        hdr = VideoRange.entries.filter { it != VideoRange.Sdr }.map { HdrSelection(it, hdrOutput(it, VideoCodec.Hevc, capabilities, settings)) },
    )
}
