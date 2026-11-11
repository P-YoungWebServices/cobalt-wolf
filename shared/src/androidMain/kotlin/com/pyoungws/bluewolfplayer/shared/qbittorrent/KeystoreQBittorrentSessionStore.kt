package com.pyoungws.bluewolfplayer.shared.qbittorrent

import android.content.Context
import com.pyoungws.bluewolfplayer.shared.auth.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stores the qBittorrent `SID` cookie in app-private preferences, encrypted with AES-256-GCM
 * via an Android Keystore key. The decrypted value is cached: HttpCookies asks for the cookie
 * on every request.
 */
class KeystoreQBittorrentSessionStore(context: Context) : QBittorrentSessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)
    private val mutex = Mutex()
    private var cachedPlain: Pair<String, String>? = null

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encrypted = prefs.getString(KEY_COOKIE, null) ?: return@withLock null
            cachedPlain?.takeIf { it.first == encrypted }?.second ?: runCatching { cipher.decrypt(encrypted) }
                .onFailure { clearSync() }
                .getOrNull()
                ?.also { cachedPlain = encrypted to it }
        }
    }

    override suspend fun save(cookie: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encrypted = runCatching { cipher.encrypt(cookie) }.getOrThrow()
            prefs.edit().putString(KEY_COOKIE, encrypted).commit()
            cachedPlain = encrypted to cookie
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { clearSync() } }

    private fun clearSync() {
        cachedPlain = null
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "qbittorrent_session"
        const val KEY_COOKIE = "session_cookie"
        const val KEY_ALIAS = "blue_wolf_qbittorrent_session_key"
    }
}
