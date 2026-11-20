package com.example.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.pinDataStore by preferencesDataStore(name = "mauzo_pin_prefs")

data class PinVerifyResult(
    val success: Boolean,
    val remainingCooldownSeconds: Long = 0L,
    val failedAttempts: Int = 0
)

/**
 * Manages local PIN authentication using salted PBKDF2-HMAC-SHA256 and constant-time comparison.
 */
class PinManager(private val context: Context) {

    companion object {
        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        private val KEY_LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until_ms")
    }

    suspend fun isPinConfigured(): Boolean {
        val prefs = context.pinDataStore.data.first()
        return !prefs[KEY_PIN_HASH].isNullOrBlank() && !prefs[KEY_PIN_SALT].isNullOrBlank()
    }

    suspend fun setPin(pin: String): Result<Unit> {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN lazima iwe tarakimu 4 hadi 6"))
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        context.pinDataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
            prefs[KEY_PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[KEY_FAILED_ATTEMPTS] = 0
            prefs[KEY_LOCKOUT_UNTIL] = 0L
        }
        return Result.success(Unit)
    }

    suspend fun verifyPin(pin: String, nowMs: Long = System.currentTimeMillis()): PinVerifyResult {
        val prefs = context.pinDataStore.data.first()
        val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL] ?: 0L
        if (nowMs < lockoutUntil) {
            val remaining = ((lockoutUntil - nowMs) / 1000L).coerceAtLeast(1L)
            return PinVerifyResult(
                success = false,
                remainingCooldownSeconds = remaining,
                failedAttempts = prefs[KEY_FAILED_ATTEMPTS] ?: 0
            )
        }

        val storedHashB64 = prefs[KEY_PIN_HASH] ?: return PinVerifyResult(false)
        val storedSaltB64 = prefs[KEY_PIN_SALT] ?: return PinVerifyResult(false)
        val expectedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)
        val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val candidateHash = pbkdf2(pin, salt)

        return if (MessageDigest.isEqual(expectedHash, candidateHash)) {
            context.pinDataStore.edit { editPrefs ->
                editPrefs[KEY_FAILED_ATTEMPTS] = 0
                editPrefs[KEY_LOCKOUT_UNTIL] = 0L
            }
            PinVerifyResult(success = true)
        } else {
            val nextAttempts = (prefs[KEY_FAILED_ATTEMPTS] ?: 0) + 1
            val cooldownMs = when {
                nextAttempts >= 5 -> 60_000L
                nextAttempts >= 3 -> 15_000L
                else -> 0L
            }
            context.pinDataStore.edit { editPrefs ->
                editPrefs[KEY_FAILED_ATTEMPTS] = nextAttempts
                editPrefs[KEY_LOCKOUT_UNTIL] = if (cooldownMs > 0L) nowMs + cooldownMs else 0L
            }
            PinVerifyResult(
                success = false,
                remainingCooldownSeconds = cooldownMs / 1000L,
                failedAttempts = nextAttempts
            )
        }
    }

    suspend fun clearPin() {
        context.pinDataStore.edit { it.clear() }
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
