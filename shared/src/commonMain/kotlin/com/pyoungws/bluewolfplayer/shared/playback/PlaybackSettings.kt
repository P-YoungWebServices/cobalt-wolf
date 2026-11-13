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
    /** Scale applied to rendered subtitles (1.0 = unchanged). */
    val subtitleScale: Float = 1f,
    /** What to do when an intro segment is reached. */
    val introBehavior: SegmentBehavior = SegmentBehavior.Ask,
    /** What to do when an outro/credits segment is reached. */
    val outroBehavior: SegmentBehavior = SegmentBehavior.Ask,
    /** Seconds to rewind when resuming an item (0 = resume exactly at the saved position). */
    val resumeRewindSeconds: Int = 0,
    /** Consecutive episodes allowed before the "still watching?" prompt pauses playback; 0 disables. */
    val stillWatchingEpisodes: Int = 3,
    /** Play local trailer/intros before movies (cinema mode). */
    val cinemaMode: Boolean = false,
    /** Hand playback to an external player app instead of the built-in one. */
    val externalPlayer: Boolean = false,
    /** Preferred audio language (three-letter ISO 639-2, e.g. "eng"); null = first/primary. */
    val preferredAudioLanguage: String? = null,
    /** Preferred subtitle language (three-letter ISO 639-2); null = none/first. */
    val preferredSubtitleLanguage: String? = null,
    /** When subtitles should be shown by default. */
    val subtitleMode: SubtitlePlaybackMode = SubtitlePlaybackMode.Default,
    /** Passthrough toggles per codec family; off = decode to PCM even when the receiver takes the format. */
    val passthroughAc3Eac3: Boolean = true,
    val passthroughDts: Boolean = true,
    val passthroughTrueHd: Boolean = true,
    /** Cap on channels the server may transcode to; 0 = device maximum. */
    val audioChannelCap: Int = 0,
    /** Render PGS/graphical subtitles on device; off drops them from the profile so the server burns them. */
    val pgsSubtitles: Boolean = true,
    /** Subtitle text appearance (basic renderer; ASS styling is honoured by libass instead). */
    val subtitleColor: SubtitleColor = SubtitleColor.White,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.Transparent,
    val subtitleBold: Boolean = false,
    /** HDR formats offered to the server; unchecked formats get tone-mapped via transcode. */
    val allowHdr10: Boolean = true,
    val allowHlg: Boolean = true,
    /** Initial video framing; still switchable per playback in the player UI. */
    val defaultZoom: ZoomMode = ZoomMode.Fit,
    /** Seconds of max buffer; 0 keeps the engine default (240 s). */
    val bufferSeconds: Int = 0,
    /** Series whose last episode activity is older than this are hidden from Next up; 0 disables the cutoff. */
    val nextUpCutoffDays: Int = 365,
    /** Video codec the server should transcode to when a transcode is needed. */
    val transcodeCodec: TranscodeCodec = TranscodeCodec.Auto,
    /** Highest H.264 level offered to the server (times 10, e.g. 51 = 5.1); streams above it get transcoded. 0 = decoder max. */
    val h264LevelCap: Int = 0,
    /** Highest HEVC level offered to the server (e.g. 153 = 5.1); 0 = decoder max. */
    val hevcLevelCap: Int = 0,
    /** Order and visibility of home rows; rows listed here come first, hidden ones are removed. */
    val homeRowOrder: List<String> = emptyList(),
    val hiddenHomeRows: Set<String> = emptySet(),
) {
    companion object {
        /** Stable row ids used in [homeRowOrder]/[hiddenHomeRows]. */
        const val ROW_LIBRARIES = "libraries"
        const val ROW_CONTINUE = "continue"
        const val ROW_NEXT_UP = "nextup"
        const val ROW_RESUME_AUDIO = "resumeaudio"
        const val ROW_LIVE_TV = "livetv"
        const val ROW_ON_NOW = "onnow"
        const val ROW_RECENT_PREFIX = "recent:"
    }
}

/** Whether an auto-skip segment acts automatically or only offers the button. */
enum class SegmentBehavior { Ask, Auto, Off }

/** Video codec the server transcodes to. Auto is H.264, which every device decodes. */
enum class TranscodeCodec { Auto, H264, Hevc, Av1 }

enum class SubtitleColor { White, Yellow, Green, Cyan }
enum class SubtitleBackground { Transparent, SemiTransparent, Opaque }
enum class ZoomMode { Fit, Fill, Zoom, Crop }

/** How the player picks a subtitle track at start. */
enum class SubtitlePlaybackMode {
    /** Server/default behaviour — none unless flagged default. */
    Default,
    /** Always show the preferred-language subtitle. */
    Always,
    /** Only forced subtitles in the preferred language. */
    Forced,
    /** Never show subtitles. */
    None,
}

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
