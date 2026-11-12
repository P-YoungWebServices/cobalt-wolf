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

/** Cap on the bitrate the server may send; a source above it is transcoded down. */
enum class StreamBitrate(val bps: Int?) {
    /** No cap beyond the device's own profile; the server direct-plays what the profile accepts. */
    Auto(null),
    Mbps120(120_000_000),
    Mbps80(80_000_000),
    Mbps60(60_000_000),
    Mbps40(40_000_000),
    Mbps20(20_000_000),
    Mbps10(10_000_000),
    Mbps5(5_000_000),
    Mbps2(2_000_000),
}

/** The user's playback overrides. The defaults pick the best path automatically. */
data class PlaybackSettings(
    val decoding: DecodingPreference = DecodingPreference.Auto,
    val hdr: HdrPreference = HdrPreference.Auto,
    /** When off, Dolby Vision plays from its HDR10/HLG/SDR base layer where it has one. */
    val dolbyVision: Boolean = true,
    val audioOutput: AudioOutputPreference = AudioOutputPreference.Auto,
    val frameRateMatching: FrameRateMatching = FrameRateMatching.Auto,
    /** Maximum bitrate the server may stream; media above it is transcoded down. */
    val maxBitrate: StreamBitrate = StreamBitrate.Auto,
    /** Render ASS/SSA subtitles with libass (full styling) instead of the basic text renderer. */
    val styledSubtitles: Boolean = true,
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
