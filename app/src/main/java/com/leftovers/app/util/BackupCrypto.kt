package com.leftovers.app.util

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup is password protected and no password, or the wrong one, was given. */
class BackupPasswordException(message: String) : Exception(message)

/**
 * Password protection for backup files: AES-256-GCM with a key stretched from the password by
 * PBKDF2. The file stays JSON, so it is still recognisable as a Leftovers backup.
 */
object BackupCrypto {
    private const val ITERATIONS = 150_000
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun isEncrypted(json: JSONObject) = json.optBoolean("encrypted", false)

    fun encrypt(plain: String, password: String): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("app", "leftovers")
            .put("encrypted", true)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", b64.encodeToString(salt))
            .put("iv", b64.encodeToString(iv))
            .put("data", b64.encodeToString(cipher.doFinal(plain.toByteArray())))
    }

    fun decrypt(wrapper: JSONObject, password: String): String {
        val b64 = Base64.getDecoder()
        val salt = b64.decode(wrapper.getString("salt"))
        val iv = b64.decode(wrapper.getString("iv"))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, wrapper.optInt("iterations", ITERATIONS)), GCMParameterSpec(128, iv))
        return try {
            String(cipher.doFinal(b64.decode(wrapper.getString("data"))))
        } catch (e: AEADBadTagException) {
            throw BackupPasswordException("Wrong password")
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
