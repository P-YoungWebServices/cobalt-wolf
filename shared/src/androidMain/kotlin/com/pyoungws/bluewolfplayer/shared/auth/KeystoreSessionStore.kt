package com.pyoungws.bluewolfplayer.shared.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.pyoungws.bluewolfplayer.shared.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the session in app-private preferences. The access token is encrypted with AES-256-GCM
 * using a non-exportable key held in the Android Keystore (hardware-backed where available).
 * The preferences file is excluded from backups; see the app's backup rules.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        val server = prefs.getString(KEY_SERVER, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        val encryptedToken = prefs.getString(KEY_TOKEN, null)
        if (server == null || userId == null || encryptedToken == null) return@withContext null
        try {
            Session(serverAddress = server, userId = userId, accessToken = decrypt(encryptedToken))
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
            .putString(KEY_TOKEN, encrypt(session.accessToken))
            .commit()
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { clearSync() }

    private fun clearSync() {
        prefs.edit().clear().commit()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH) { "Encrypted token is too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH))
        }
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "session"
        const val KEY_SERVER = "server_address"
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "access_token"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "blue_wolf_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
