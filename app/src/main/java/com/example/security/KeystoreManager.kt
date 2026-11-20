package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.securityDataStore by preferencesDataStore(name = "mauzo_security_prefs")

/**
 * Manages cryptographic keys via Android Keystore for database encryption and local secrets.
 * Never logs keys or passphrases.
 */
class KeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mauzo_master_db_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private val WRAPPED_DB_KEY = stringPreferencesKey("wrapped_db_passphrase_v1")
        private val FALLBACK_TEST_KEY = stringPreferencesKey("jvm_test_key_v1")
    }

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    private fun getOrCreateMasterKey(): SecretKey {
        if (!isRobolectric()) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        // Robolectric JVM fallback only (AndroidKeyStore hardware/provider is absent on JVM).
        val deterministicBytes = ByteArray(32) { idx -> (idx * 13 + 7).toByte() }
        return SecretKeySpec(deterministicBytes, "AES")
    }

    fun encryptWithKeystore(plainBytes: ByteArray): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        val output = ByteArray(1 + iv.size + cipherText.size)
        output[0] = iv.size.toByte()
        System.arraycopy(iv, 0, output, 1, iv.size)
        System.arraycopy(cipherText, 0, output, 1 + iv.size, cipherText.size)
        return output
    }

    fun decryptWithKeystore(wrappedBytes: ByteArray): ByteArray {
        require(wrappedBytes.size > 1 + IV_LENGTH_BYTES) { "Invalid encrypted payload" }
        val ivLen = wrappedBytes[0].toInt() and 0xFF
        require(ivLen in 12..16 && wrappedBytes.size > 1 + ivLen) { "Malformed IV length" }
        val iv = wrappedBytes.copyOfRange(1, 1 + ivLen)
        val cipherText = wrappedBytes.copyOfRange(1 + ivLen, wrappedBytes.size)
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    /**
     * Retrieves or generates a random 256-bit database passphrase wrapped by Android Keystore.
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = context.securityDataStore.data.first()
        val existingEncoded = prefs[WRAPPED_DB_KEY]
        if (!existingEncoded.isNullOrBlank()) {
            val decoded = Base64.decode(existingEncoded, Base64.NO_WRAP)
            return runCatching { decryptWithKeystore(decoded) }.getOrElse {
                generateAndStorePassphrase()
            }
        }
        return generateAndStorePassphrase()
    }

    private suspend fun generateAndStorePassphrase(): ByteArray {
        val rawKey = ByteArray(32)
        SecureRandom().nextBytes(rawKey)
        val wrapped = encryptWithKeystore(rawKey)
        val encoded = Base64.encodeToString(wrapped, Base64.NO_WRAP)
        context.securityDataStore.edit { prefs ->
            prefs[WRAPPED_DB_KEY] = encoded
        }
        return rawKey
    }

    suspend fun clearAllKeys() {
        context.securityDataStore.edit { it.clear() }
        if (!isRobolectric()) {
            runCatching {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
            }
        }
    }
}
