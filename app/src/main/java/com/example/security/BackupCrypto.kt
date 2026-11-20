package com.example.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts user-controlled backup files using PBKDF2-HMAC-SHA256 + AES-256-GCM.
 */
object BackupCrypto {
    private val MAGIC_HEADER = "MAUZO_BAK_V1:".toByteArray(StandardCharsets.UTF_8)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encryptBackup(jsonPayload: String, passphrase: String): ByteArray {
        require(passphrase.length >= 4) { "Neno la siri la chelezo lazima liwe na herufi angalau 4" }
        val rng = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(jsonPayload.toByteArray(StandardCharsets.UTF_8))

        val output = ByteArray(MAGIC_HEADER.size + SALT_LEN + IV_LEN + cipherBytes.size)
        var offset = 0
        System.arraycopy(MAGIC_HEADER, 0, output, offset, MAGIC_HEADER.size)
        offset += MAGIC_HEADER.size
        System.arraycopy(salt, 0, output, offset, SALT_LEN)
        offset += SALT_LEN
        System.arraycopy(iv, 0, output, offset, IV_LEN)
        offset += IV_LEN
        System.arraycopy(cipherBytes, 0, output, offset, cipherBytes.size)
        return output
    }

    fun decryptBackup(encryptedBytes: ByteArray, passphrase: String): String {
        val minLen = MAGIC_HEADER.size + SALT_LEN + IV_LEN + 16
        require(encryptedBytes.size >= minLen) { "Faili la chelezo sio sahihi au limeharibika" }

        val header = encryptedBytes.copyOfRange(0, MAGIC_HEADER.size)
        require(header.contentEquals(MAGIC_HEADER)) { "Faili hili sio chelezo halali la Mauzo App" }

        var offset = MAGIC_HEADER.size
        val salt = encryptedBytes.copyOfRange(offset, offset + SALT_LEN)
        offset += SALT_LEN
        val iv = encryptedBytes.copyOfRange(offset, offset + IV_LEN)
        offset += IV_LEN
        val cipherBytes = encryptedBytes.copyOfRange(offset, encryptedBytes.size)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
