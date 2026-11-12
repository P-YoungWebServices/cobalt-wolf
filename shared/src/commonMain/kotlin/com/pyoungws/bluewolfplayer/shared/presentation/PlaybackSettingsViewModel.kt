package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import com.pyoungws.bluewolfplayer.shared.playback.AudioCodec
import com.pyoungws.bluewolfplayer.shared.playback.AudioOutputPreference
import com.pyoungws.bluewolfplayer.shared.playback.CapabilityDetector
import com.pyoungws.bluewolfplayer.shared.playback.DecodePath
import com.pyoungws.bluewolfplayer.shared.playback.DecodingPreference
import com.pyoungws.bluewolfplayer.shared.playback.DeviceCapabilities
import com.pyoungws.bluewolfplayer.shared.playback.FrameRateMatching
import com.pyoungws.bluewolfplayer.shared.playback.HdrOutput
import com.pyoungws.bluewolfplayer.shared.playback.HdrPreference
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettings
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsRepository
import com.pyoungws.bluewolfplayer.shared.playback.StreamBitrate
import com.pyoungws.bluewolfplayer.shared.playback.VideoCodec
import com.pyoungws.bluewolfplayer.shared.playback.VideoRange
import com.pyoungws.bluewolfplayer.shared.playback.VideoSupport
import com.pyoungws.bluewolfplayer.shared.playback.audioPath
import com.pyoungws.bluewolfplayer.shared.playback.hdrOutput
import com.pyoungws.bluewolfplayer.shared.playback.videoSupport
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
    fun setShowStats(enabled: Boolean) = update { it.copy(showStats = enabled) }

    fun resetToAutomatic() = update { PlaybackSettings(showStats = it.showStats) }

    private fun update(transform: (PlaybackSettings) -> PlaybackSettings) {
        repository.update(transform)
        _uiState.value = state(repository.settings.value, _uiState.value.capabilities)
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
