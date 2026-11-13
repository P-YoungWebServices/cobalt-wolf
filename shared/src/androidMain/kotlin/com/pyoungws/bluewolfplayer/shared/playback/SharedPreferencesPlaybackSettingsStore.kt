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
            resumeRewindSeconds = preferences.getInt(KEY_RESUME_REWIND, defaults.resumeRewindSeconds),
            stillWatchingEpisodes = preferences.getInt(KEY_STILL_WATCHING, defaults.stillWatchingEpisodes),
            cinemaMode = preferences.getBoolean(KEY_CINEMA_MODE, defaults.cinemaMode),
            externalPlayer = preferences.getBoolean(KEY_EXTERNAL_PLAYER, defaults.externalPlayer),
            preferredAudioLanguage = preferences.getString(KEY_PREF_AUDIO_LANG, null),
            preferredSubtitleLanguage = preferences.getString(KEY_PREF_SUB_LANG, null),
            subtitleMode = enum(KEY_SUBTITLE_MODE, defaults.subtitleMode),
            passthroughAc3Eac3 = preferences.getBoolean(KEY_PT_AC3, defaults.passthroughAc3Eac3),
            passthroughDts = preferences.getBoolean(KEY_PT_DTS, defaults.passthroughDts),
            passthroughTrueHd = preferences.getBoolean(KEY_PT_TRUEHD, defaults.passthroughTrueHd),
            audioChannelCap = preferences.getInt(KEY_AUDIO_CHANNEL_CAP, defaults.audioChannelCap),
            pgsSubtitles = preferences.getBoolean(KEY_PGS_SUBS, defaults.pgsSubtitles),
            subtitleColor = enum(KEY_SUBTITLE_COLOR, defaults.subtitleColor),
            subtitleBackground = enum(KEY_SUBTITLE_BG, defaults.subtitleBackground),
            subtitleBold = preferences.getBoolean(KEY_SUBTITLE_BOLD, defaults.subtitleBold),
            allowHdr10 = preferences.getBoolean(KEY_ALLOW_HDR10, defaults.allowHdr10),
            allowHlg = preferences.getBoolean(KEY_ALLOW_HLG, defaults.allowHlg),
            defaultZoom = enum(KEY_DEFAULT_ZOOM, defaults.defaultZoom),
            bufferSeconds = preferences.getInt(KEY_BUFFER_SECONDS, defaults.bufferSeconds),
            nextUpCutoffDays = preferences.getInt(KEY_NEXT_UP_CUTOFF, defaults.nextUpCutoffDays),
            transcodeCodec = enum(KEY_TRANSCODE_CODEC, defaults.transcodeCodec),
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
            .putInt(KEY_RESUME_REWIND, settings.resumeRewindSeconds)
            .putInt(KEY_STILL_WATCHING, settings.stillWatchingEpisodes)
            .putBoolean(KEY_CINEMA_MODE, settings.cinemaMode)
            .putBoolean(KEY_EXTERNAL_PLAYER, settings.externalPlayer)
            .putString(KEY_PREF_AUDIO_LANG, settings.preferredAudioLanguage)
            .putString(KEY_PREF_SUB_LANG, settings.preferredSubtitleLanguage)
            .putString(KEY_SUBTITLE_MODE, settings.subtitleMode.name)
            .putBoolean(KEY_PT_AC3, settings.passthroughAc3Eac3)
            .putBoolean(KEY_PT_DTS, settings.passthroughDts)
            .putBoolean(KEY_PT_TRUEHD, settings.passthroughTrueHd)
            .putInt(KEY_AUDIO_CHANNEL_CAP, settings.audioChannelCap)
            .putBoolean(KEY_PGS_SUBS, settings.pgsSubtitles)
            .putString(KEY_SUBTITLE_COLOR, settings.subtitleColor.name)
            .putString(KEY_SUBTITLE_BG, settings.subtitleBackground.name)
            .putBoolean(KEY_SUBTITLE_BOLD, settings.subtitleBold)
            .putBoolean(KEY_ALLOW_HDR10, settings.allowHdr10)
            .putBoolean(KEY_ALLOW_HLG, settings.allowHlg)
            .putString(KEY_DEFAULT_ZOOM, settings.defaultZoom.name)
            .putInt(KEY_BUFFER_SECONDS, settings.bufferSeconds)
            .putInt(KEY_NEXT_UP_CUTOFF, settings.nextUpCutoffDays)
            .putString(KEY_TRANSCODE_CODEC, settings.transcodeCodec.name)
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
        const val KEY_RESUME_REWIND = "resume_rewind_seconds"
        const val KEY_STILL_WATCHING = "still_watching_episodes"
        const val KEY_CINEMA_MODE = "cinema_mode"
        const val KEY_EXTERNAL_PLAYER = "external_player"
        const val KEY_PREF_AUDIO_LANG = "pref_audio_lang"
        const val KEY_PREF_SUB_LANG = "pref_sub_lang"
        const val KEY_SUBTITLE_MODE = "subtitle_mode"
        const val KEY_PT_AC3 = "passthrough_ac3_eac3"
        const val KEY_PT_DTS = "passthrough_dts"
        const val KEY_PT_TRUEHD = "passthrough_truehd"
        const val KEY_AUDIO_CHANNEL_CAP = "audio_channel_cap"
        const val KEY_PGS_SUBS = "pgs_subtitles"
        const val KEY_SUBTITLE_COLOR = "subtitle_color"
        const val KEY_SUBTITLE_BG = "subtitle_background"
        const val KEY_SUBTITLE_BOLD = "subtitle_bold"
        const val KEY_ALLOW_HDR10 = "allow_hdr10"
        const val KEY_ALLOW_HLG = "allow_hlg"
        const val KEY_DEFAULT_ZOOM = "default_zoom"
        const val KEY_BUFFER_SECONDS = "buffer_seconds"
        const val KEY_NEXT_UP_CUTOFF = "next_up_cutoff"
        const val KEY_TRANSCODE_CODEC = "transcode_codec"
        const val KEY_HOME_ROW_ORDER = "home_row_order"
        const val KEY_HOME_HIDDEN_ROWS = "home_hidden_rows"
    }
}
