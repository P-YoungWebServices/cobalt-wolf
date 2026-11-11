package com.pyoungws.bluewolfplayer.shared.arrs

import android.content.Context
import com.pyoungws.bluewolfplayer.shared.auth.KeystoreCipher

/**
 * Keeps [ArrSettings] in app-private SharedPreferences. Server URLs are stored plain; API keys
 * are AES-256-GCM encrypted with an Android Keystore key before being written.
 */
class SharedPreferencesArrSettingsStore(context: Context) : ArrSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    override fun load() = ArrSettings(
        radarr = service(KEY_RADARR_URL, KEY_RADARR_KEY),
        sonarr = service(KEY_SONARR_URL, KEY_SONARR_KEY),
    )

    override fun save(settings: ArrSettings) {
        preferences.edit()
            .putString(KEY_RADARR_URL, settings.radarr.serverUrl)
            .putString(KEY_RADARR_KEY, encrypt(settings.radarr.apiKey))
            .putString(KEY_SONARR_URL, settings.sonarr.serverUrl)
            .putString(KEY_SONARR_KEY, encrypt(settings.sonarr.apiKey))
            .apply()
    }

    private fun service(urlKey: String, apiKeyKey: String) = ArrServiceSettings(
        serverUrl = preferences.getString(urlKey, null),
        apiKey = decrypt(preferences.getString(apiKeyKey, null)),
    )

    private fun encrypt(value: String?): String? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { cipher.encrypt(it) }.getOrNull() }

    private fun decrypt(value: String?): String? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    private companion object {
        const val PREFS_NAME = "arr_settings"
        const val KEY_ALIAS = "arr_settings_keys"
        const val KEY_RADARR_URL = "radarr_url"
        const val KEY_RADARR_KEY = "radarr_api_key"
        const val KEY_SONARR_URL = "sonarr_url"
        const val KEY_SONARR_KEY = "sonarr_api_key"
    }
}
