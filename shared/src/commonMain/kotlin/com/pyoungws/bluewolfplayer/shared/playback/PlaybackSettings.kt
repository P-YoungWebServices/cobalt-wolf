package com.pyoungws.bluewolfplayer.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manual override for how streams are decoded. */
enum class DecodingPreference {
    /** Direct play on the device's decoders, then FFmpeg, then a server transcode. */
    Auto,
    /** Decode with the bundled FFmpeg wherever it supports the codec. */
    PreferLocal,
    /** Always ask the server to transcode. */
    AlwaysTranscode,
}

enum class HdrPreference {
    /** Play HDR as HDR when the decoder and display support the format. */
    Auto,
    /** Never output HDR; the server tone-maps HDR sources to SDR. */
    SdrOnly,
}

enum class AudioOutputPreference {
    /** Passthrough when the audio output accepts the format, otherwise decode to PCM. */
    Auto,
    /** Always decode to PCM on the device. */
    PcmOnly,
}

enum class FrameRateMatching {
    /** Switch display modes on TV; only seamless switches on phones and tablets. */
    Auto,
    /** Only switches that do not blank the screen. */
    SeamlessOnly,
    Off,
}

/** The user's playback overrides. The defaults pick the best path automatically. */
data class PlaybackSettings(
    val decoding: DecodingPreference = DecodingPreference.Auto,
    val hdr: HdrPreference = HdrPreference.Auto,
    /** When off, Dolby Vision plays from its HDR10/HLG/SDR base layer where it has one. */
    val dolbyVision: Boolean = true,
    val audioOutput: AudioOutputPreference = AudioOutputPreference.Auto,
    val frameRateMatching: FrameRateMatching = FrameRateMatching.Auto,
    /** Render ASS/SSA subtitles with libass (full styling) instead of the basic text renderer. */
    val styledSubtitles: Boolean = true,
    val showStats: Boolean = false,
)

/** Persists [PlaybackSettings]. */
interface PlaybackSettingsStore {
    fun load(): PlaybackSettings
    fun save(settings: PlaybackSettings)
}

class PlaybackSettingsRepository(private val store: PlaybackSettingsStore) {
    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    fun update(transform: (PlaybackSettings) -> PlaybackSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        store.save(updated)
    }
}
