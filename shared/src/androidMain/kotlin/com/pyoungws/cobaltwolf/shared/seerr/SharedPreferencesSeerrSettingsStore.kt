package com.pyoungws.cobaltwolf.shared.seerr

import android.content.Context

/** Keeps [SeerrSettings] in app-private SharedPreferences. */
class SharedPreferencesSeerrSettingsStore(context: Context) : SeerrSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load() = SeerrSettings(serverUrl = preferences.getString(KEY_SERVER_URL, null))

    override fun save(settings: SeerrSettings) {
        preferences.edit().putString(KEY_SERVER_URL, settings.serverUrl).apply()
    }

    private companion object {
        const val PREFS_NAME = "seerr_settings"
        const val KEY_SERVER_URL = "server_url"
    }
}
