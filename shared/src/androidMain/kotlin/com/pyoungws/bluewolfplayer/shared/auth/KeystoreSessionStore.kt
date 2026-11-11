package com.pyoungws.bluewolfplayer.shared.auth

import android.content.Context
import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException

/**
 * Stores the session in app-private preferences. The access token is encrypted with AES-256-GCM
 * using a non-exportable key held in the Android Keystore (hardware-backed where available).
 * The preferences file is excluded from backups; see the app's backup rules.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        val server = prefs.getString(KEY_SERVER, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        val encryptedToken = prefs.getString(KEY_TOKEN, null)
        if (server == null || userId == null || encryptedToken == null) return@withContext null
        try {
            Session(serverAddress = server, userId = userId, accessToken = cipher.decrypt(encryptedToken))
        } catch (e: GeneralSecurityException) {
            // Key was invalidated or data is corrupt; force a fresh sign-in.
            clearSync()
            null
        } catch (e: IllegalArgumentException) {
            clearSync()
            null
        }
    }

    override suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_SERVER, session.serverAddress)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_TOKEN, cipher.encrypt(session.accessToken))
            .commit()
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { clearSync() }

    private fun clearSync() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "session"
        const val KEY_SERVER = "server_address"
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_ALIAS = "blue_wolf_session_key"
    }
}
