package com.leftovers.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The AI services the assistant can talk to. Users bring their own API key. */
enum class AiProvider(val label: String, val defaultModel: String, val suggestedModels: List<String>, val keyHint: String) {
    CLAUDE("Claude", "claude-opus-5-5", listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5"), "sk-ant-…"),
    OPENAI("ChatGPT", "gpt-5", listOf("gpt-5", "gpt-5-mini"), "sk-…"),
    // "-latest" aliases follow Google's current models, so the default doesn't go stale when one is retired.
    GEMINI("Gemini", "gemini-flash-latest", listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-pro-latest"), "AIza… or AQ.…"),
}

data class AssistantConfig(val provider: AiProvider, val model: String, val apiKey: String)

/**
 * Which AI is connected. The API key is encrypted with a key held in the Android Keystore,
 * never included in backups, and wiped on disconnect.
 */
class AssistantSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AssistantConfig?> = _config.asStateFlow()

    /** Last provider/model chosen, so the setup sheet remembers them after a disconnect. */
    val lastProvider: AiProvider get() = AiProvider.entries.firstOrNull { it.name == prefs.getString(PROVIDER, null) } ?: AiProvider.CLAUDE
    fun lastModel(provider: AiProvider): String =
        prefs.getString(MODEL, null)?.takeIf { lastProvider == provider } ?: provider.defaultModel

    private val _privacy = MutableStateFlow(loadPrivacy())
    val privacy: StateFlow<AssistantPrivacy> = _privacy.asStateFlow()

    fun setPrivacy(value: AssistantPrivacy) {
        prefs.edit().putBoolean(SHARE_NOTES, value.shareNotes).putBoolean(ALLOW_CHANGES, value.allowChanges).apply()
        _privacy.value = value
    }

    // Notes can hold personal details, so they stay on the phone unless the user opts in.
    private fun loadPrivacy() = AssistantPrivacy(
        shareNotes = prefs.getBoolean(SHARE_NOTES, false),
        allowChanges = prefs.getBoolean(ALLOW_CHANGES, true),
    )

    fun connect(provider: AiProvider, model: String, apiKey: String) {
        prefs.edit()
            .putString(PROVIDER, provider.name)
            .putString(MODEL, model.trim().ifEmpty { provider.defaultModel })
            .putString(KEY, KeyVault.encrypt(apiKey.trim()))
            .apply()
        _config.value = load()
    }

    fun disconnect() {
        prefs.edit().remove(KEY).apply()
        _config.value = null
    }

    private fun load(): AssistantConfig? {
        val provider = AiProvider.entries.firstOrNull { it.name == prefs.getString(PROVIDER, null) } ?: return null
        val key = prefs.getString(KEY, null)?.let(KeyVault::decrypt) ?: return null
        return AssistantConfig(provider, prefs.getString(MODEL, null) ?: provider.defaultModel, key)
    }

    private companion object {
        const val PROVIDER = "provider"
        const val MODEL = "model"
        const val KEY = "api_key"
        const val SHARE_NOTES = "share_notes"
        const val ALLOW_CHANGES = "allow_changes"
    }
}

/** AES-GCM with a non-exportable Keystore key. */
private object KeyVault {
    private const val ALIAS = "leftovers_assistant_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}
