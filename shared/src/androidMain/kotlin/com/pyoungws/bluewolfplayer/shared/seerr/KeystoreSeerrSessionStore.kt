package com.pyoungws.bluewolfplayer.shared.seerr

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Seerr session cookie in app-private preferences, encrypted with AES-256-GCM using a
 * non-exportable key held in the Android Keystore (same scheme as the Jellyfin session store).
 */
class KeystoreSeerrSessionStore(context: Context) : SeerrSessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // HttpCookies asks for the cookie on every request, often several at once; keystore access
    // and cipher init are serialized here so a parallel decrypt can never observe a half-created
    // key. The decrypted value is cached to avoid a decrypt per request.
    private val mutex = Mutex()
    private var cachedPlain: Pair<String, String>? = null
    private var cachedKey: SecretKey? = null

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encrypted = prefs.getString(KEY_COOKIE, null) ?: return@withLock null
            cachedPlain?.takeIf { it.first == encrypted }?.second ?: try {
                decrypt(encrypted).also { cachedPlain = encrypted to it }
            } catch (e: GeneralSecurityException) {
                clearSync()
                null
            } catch (e: IllegalArgumentException) {
                clearSync()
                null
            }
        }
    }

    override suspend fun save(cookieValue: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encrypted = encrypt(cookieValue)
            prefs.edit().putString(KEY_COOKIE, encrypted).commit()
            cachedPlain = encrypted to cookieValue
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { clearSync() } }

    private fun clearSync() {
        cachedPlain = null
        prefs.edit().clear().commit()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH) { "Encrypted cookie is too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH))
        }
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it.also { key -> cachedKey = key } }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey().also { cachedKey = it }
    }

    private companion object {
        const val PREFS_NAME = "seerr_session"
        const val KEY_COOKIE = "session_cookie"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "blue_wolf_seerr_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
