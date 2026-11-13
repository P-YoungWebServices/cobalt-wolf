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
            maxBitrate = enum(KEY_MAX_BITRATE, defaults.maxBitrate),
            styledSubtitles = preferences.getBoolean(KEY_STYLED_SUBTITLES, defaults.styledSubtitles),
            subtitleScale = preferences.getFloat(KEY_SUBTITLE_SCALE, defaults.subtitleScale),
            introBehavior = enum(KEY_INTRO_BEHAVIOR, defaults.introBehavior),
            outroBehavior = enum(KEY_OUTRO_BEHAVIOR, defaults.outroBehavior),
            nextUpCutoffDays = preferences.getInt(KEY_NEXT_UP_CUTOFF, defaults.nextUpCutoffDays),
            homeRowOrder = preferences.getString(KEY_HOME_ROW_ORDER, null)?.split(",").orEmpty(),
            hiddenHomeRows = preferences.getString(KEY_HOME_HIDDEN_ROWS, null)?.split(",")?.toSet().orEmpty(),
        )
    }

    override fun save(settings: PlaybackSettings) {
        preferences.edit()
            .putString(KEY_DECODING, settings.decoding.name)
            .putString(KEY_HDR, settings.hdr.name)
            .putBoolean(KEY_DOLBY_VISION, settings.dolbyVision)
            .putString(KEY_AUDIO_OUTPUT, settings.audioOutput.name)
            .putString(KEY_FRAME_RATE, settings.frameRateMatching.name)
            .putString(KEY_MAX_BITRATE, settings.maxBitrate.name)
            .putBoolean(KEY_STYLED_SUBTITLES, settings.styledSubtitles)
            .putFloat(KEY_SUBTITLE_SCALE, settings.subtitleScale)
            .putString(KEY_INTRO_BEHAVIOR, settings.introBehavior.name)
            .putString(KEY_OUTRO_BEHAVIOR, settings.outroBehavior.name)
            .putInt(KEY_NEXT_UP_CUTOFF, settings.nextUpCutoffDays)
            .putString(KEY_HOME_ROW_ORDER, settings.homeRowOrder.joinToString(","))
            .putString(KEY_HOME_HIDDEN_ROWS, settings.hiddenHomeRows.joinToString(","))
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
        const val KEY_MAX_BITRATE = "max_bitrate"
        const val KEY_STYLED_SUBTITLES = "styled_subtitles"
        const val KEY_SUBTITLE_SCALE = "subtitle_scale"
        const val KEY_INTRO_BEHAVIOR = "intro_behavior"
        const val KEY_OUTRO_BEHAVIOR = "outro_behavior"
        const val KEY_NEXT_UP_CUTOFF = "next_up_cutoff"
        const val KEY_HOME_ROW_ORDER = "home_row_order"
        const val KEY_HOME_HIDDEN_ROWS = "home_hidden_rows"
    }
}
