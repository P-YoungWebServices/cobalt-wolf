package com.pyoungws.bluewolfplayer.shared.playback

import android.content.Context

/** Keeps [PlaybackSettings] in app-private SharedPreferences. Unknown or missing values fall back to the defaults. */
class SharedPreferencesPlaybackSettingsStore(context: Context) : PlaybackSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)

    override fun load(): PlaybackSettings {
        val defaults = PlaybackSettings()
        return PlaybackSettings(
            decoding = enum(KEY_DECODING, defaults.decoding),
            hdr = enum(KEY_HDR, defaults.hdr),
            dolbyVision = preferences.getBoolean(KEY_DOLBY_VISION, defaults.dolbyVision),
            audioOutput = enum(KEY_AUDIO_OUTPUT, defaults.audioOutput),
            frameRateMatching = enum(KEY_FRAME_RATE, defaults.frameRateMatching),
            styledSubtitles = preferences.getBoolean(KEY_STYLED_SUBTITLES, defaults.styledSubtitles),
            showStats = preferences.getBoolean(KEY_SHOW_STATS, defaults.showStats),
        )
    }

    override fun save(settings: PlaybackSettings) {
        preferences.edit()
            .putString(KEY_DECODING, settings.decoding.name)
            .putString(KEY_HDR, settings.hdr.name)
            .putBoolean(KEY_DOLBY_VISION, settings.dolbyVision)
            .putString(KEY_AUDIO_OUTPUT, settings.audioOutput.name)
            .putString(KEY_FRAME_RATE, settings.frameRateMatching.name)
            .putBoolean(KEY_STYLED_SUBTITLES, settings.styledSubtitles)
            .putBoolean(KEY_SHOW_STATS, settings.showStats)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T =
        preferences.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val KEY_DECODING = "decoding"
        const val KEY_HDR = "hdr"
        const val KEY_DOLBY_VISION = "dolby_vision"
        const val KEY_AUDIO_OUTPUT = "audio_output"
        const val KEY_FRAME_RATE = "frame_rate_matching"
        const val KEY_STYLED_SUBTITLES = "styled_subtitles"
        const val KEY_SHOW_STATS = "show_stats"
    }
}
