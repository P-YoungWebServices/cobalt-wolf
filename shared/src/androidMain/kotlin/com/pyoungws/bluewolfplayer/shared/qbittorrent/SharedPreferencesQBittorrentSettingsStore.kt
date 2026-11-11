package com.pyoungws.bluewolfplayer.shared.qbittorrent

import android.content.Context
import com.pyoungws.bluewolfplayer.shared.auth.KeystoreCipher

/**
 * Keeps [QBittorrentSettings] in app-private SharedPreferences. URL and username are stored
 * plain; the password is AES-256-GCM encrypted with an Android Keystore key.
 */
class SharedPreferencesQBittorrentSettingsStore(context: Context) : QBittorrentSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    override fun load() = QBittorrentSettings(
        serverUrl = preferences.getString(KEY_URL, null),
        username = preferences.getString(KEY_USERNAME, null),
        password = preferences.getString(KEY_PASSWORD, null)
            ?.let { runCatching { cipher.decrypt(it) }.getOrNull() },
    )

    override fun save(settings: QBittorrentSettings) {
        preferences.edit()
            .putString(KEY_URL, settings.serverUrl)
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_PASSWORD, settings.password?.let { runCatching { cipher.encrypt(it) }.getOrNull() })
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "qbittorrent_settings"
        const val KEY_ALIAS = "qbittorrent_settings_keys"
        const val KEY_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
