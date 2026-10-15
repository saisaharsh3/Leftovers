package com.leftovers.app.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Leftovers' own PIN or password for app lock, instead of the phone's screen lock. Only a salted PBKDF2 hash is
 * kept, never the code itself, and it's never written into a backup. Wrong tries make the next one wait longer.
 */
class AppPasscode(context: Context) {
    enum class Kind { PIN, PASSWORD }

    private val prefs: SharedPreferences = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private val _kind = MutableStateFlow(readKind())
    /** The kind set, or null when app lock uses the phone's screen lock. */
    val kind: StateFlow<Kind?> = _kind.asStateFlow()

    private fun readKind(): Kind? = prefs.getString(KIND, null)?.let { k -> Kind.entries.firstOrNull { it.name == k } }

    suspend fun set(code: String, kind: Kind) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KIND, kind.name)
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .putInt(FAILURES, 0)
            .remove(WAIT_UNTIL)
            .apply()
        _kind.value = kind
    }

    fun clear() {
        prefs.edit().clear().apply()
        _kind.value = null
    }

    /** Milliseconds to wait before the next try is allowed; 0 when it can be tried now. */
    fun waitMs(): Long = (prefs.getLong(WAIT_UNTIL, 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    /** Checks [code]; a wrong one counts towards the wait. Always false while a wait is running. */
    suspend fun check(code: String): Boolean = withContext(Dispatchers.Default) {
        if (waitMs() > 0) return@withContext false
        val salt = prefs.getString(SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext false
        val stored = prefs.getString(HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext false
        val ok = MessageDigest.isEqual(stored, hash(code, salt))
        if (ok) {
            prefs.edit().putInt(FAILURES, 0).remove(WAIT_UNTIL).apply()
        } else {
            val failures = prefs.getInt(FAILURES, 0) + 1
            val wait = waitAfter(failures)
            prefs.edit().putInt(FAILURES, failures).apply {
                if (wait > 0) putLong(WAIT_UNTIL, SystemClock.elapsedRealtime() + wait)
            }.apply()
        }
        ok
    }

    /** A hash slow enough to make guessing a PIN costly, quick enough not to be noticed. */
    private fun hash(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256))
            .encoded

    companion object {
        private const val KIND = "kind"
        private const val SALT = "salt"
        private const val HASH = "hash"
        private const val FAILURES = "failures"
        private const val WAIT_UNTIL = "wait_until"
        private const val ITERATIONS = 60_000

        const val MIN_PIN = 4
        const val MAX_PIN = 8
        const val MIN_PASSWORD = 6

        /** No wait for the first four wrong tries, then 30 seconds, doubling each time up to 15 minutes. */
        fun waitAfter(failures: Int): Long = if (failures < 5) 0 else (30_000L shl (failures - 5).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
    }
}
