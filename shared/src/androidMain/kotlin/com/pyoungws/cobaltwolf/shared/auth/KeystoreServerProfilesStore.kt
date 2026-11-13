package com.pyoungws.cobaltwolf.shared.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException

/**
 * Persists the saved server/account profiles as JSON in app-private preferences, each access
 * token individually encrypted with AES-256-GCM via the Android Keystore (see [KeystoreSessionStore]).
 * Entries that fail to decrypt (invalidated key, corruption) are dropped on load.
 */
class KeystoreServerProfilesStore(context: Context) : ServerProfilesStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    override suspend fun load(): List<ServerProfile> = withContext(Dispatchers.IO) {
        val encoded = prefs.getString(KEY_PROFILES, null) ?: return@withContext emptyList()
        val stored = try {
            Json.decodeFromString<List<StoredProfile>>(encoded)
        } catch (e: IllegalArgumentException) {
            return@withContext emptyList()
        }
        stored.mapNotNull { profile ->
            try {
                profile.toServerProfile(cipher.decrypt(profile.accessToken))
            } catch (e: GeneralSecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override suspend fun saveAll(profiles: List<ServerProfile>) = withContext(Dispatchers.IO) {
        val stored = profiles.map { StoredProfile(it, cipher.encrypt(it.accessToken)) }
        prefs.edit().putString(KEY_PROFILES, Json.encodeToString(stored)).commit()
        Unit
    }

    /** On disk the token field carries the encrypted value, never the plaintext. */
    @Serializable
    private data class StoredProfile(
        val id: String,
        val serverAddress: String,
        val serverName: String? = null,
        val userId: String,
        val userName: String,
        val accessToken: String,
    ) {
        constructor(profile: ServerProfile, encryptedToken: String) : this(
            id = profile.id,
            serverAddress = profile.serverAddress,
            serverName = profile.serverName,
            userId = profile.userId,
            userName = profile.userName,
            accessToken = encryptedToken,
        )

        fun toServerProfile(token: String) = ServerProfile(
            id = id,
            serverAddress = serverAddress,
            serverName = serverName,
            userId = userId,
            userName = userName,
            accessToken = token,
        )
    }

    private companion object {
        const val PREFS_NAME = "server_profiles"
        const val KEY_PROFILES = "profiles"
        const val KEY_ALIAS = "cobalt_wolf_session_key"
    }
}
