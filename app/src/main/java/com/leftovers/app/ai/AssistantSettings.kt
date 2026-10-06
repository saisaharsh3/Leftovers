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

/** How a provider's API is called. Most providers speak the OpenAI Chat Completions format. */
enum class ApiStyle { CLAUDE, OPENAI, GEMINI }

/**
 * The AI services the assistant can talk to. Users bring their own API key. Model names change
 * often, so the setup sheet can also load the live list from the provider.
 */
enum class AiProvider(
    val label: String,
    val style: ApiStyle,
    /** Base URL for the API; null means the user enters it (custom servers). */
    val baseUrl: String?,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val keyHint: String,
    val keyUrl: String?,
    /** Shown in the setup sheet when there's something specific to know about this provider. */
    val note: String? = null,
) {
    CLAUDE("Claude", ApiStyle.CLAUDE, "https://api.anthropic.com/v1", "claude-opus-5-5",
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5"), "sk-ant-…", "https://console.anthropic.com/settings/keys"),
    OPENAI("ChatGPT", ApiStyle.OPENAI, "https://api.openai.com/v1", "gpt-5-mini",
        listOf("gpt-5-mini", "gpt-5"), "sk-…", "https://platform.openai.com/api-keys"),
    // "-latest" aliases follow Google's current models; Flash-Lite answers in a second or two.
    GEMINI("Gemini", ApiStyle.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "gemini-flash-lite-latest",
        listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-pro-latest"), "AIza… or AQ.…", "https://aistudio.google.com/api-keys"),
    MISTRAL("Mistral", ApiStyle.OPENAI, "https://api.mistral.ai/v1", "mistral-small-latest",
        listOf("mistral-small-latest", "mistral-medium-latest", "mistral-large-latest"), "API key", "https://console.mistral.ai/api-keys",
        note = "Based in the EU."),
    GROQ("Groq", ApiStyle.OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
        listOf("llama-3.3-70b-versatile"), "gsk_…", "https://console.groq.com/keys",
        note = "Very fast. Pick a model that supports tool use; tap Load models to see what your key can use."),
    DEEPSEEK("DeepSeek", ApiStyle.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat",
        listOf("deepseek-chat"), "sk-…", "https://platform.deepseek.com/api_keys",
        note = "DeepSeek stores data on servers in China, under its own privacy policy."),
    GROK("Grok", ApiStyle.OPENAI, "https://api.x.ai/v1", "grok-4",
        listOf("grok-4"), "xai-…", "https://console.x.ai",
        note = "By xAI. Tap Load models to see the current Grok models."),
    OPENROUTER("OpenRouter", ApiStyle.OPENAI, "https://openrouter.ai/api/v1", "openrouter/auto",
        listOf("openrouter/auto"), "sk-or-…", "https://openrouter.ai/keys",
        note = "One key for many models. OpenRouter passes your request on to the model's own provider, so both handle it."),
    CUSTOM("Custom", ApiStyle.OPENAI, null, "",
        emptyList(), "API key (if the server needs one)", null,
        note = "Any OpenAI-compatible server over HTTPS, e.g. one you host yourself. Only connect to servers you trust."),
}

/** [thorough] trades speed for more careful answers (more model "thinking"). */
data class AssistantConfig(
    val provider: AiProvider,
    val model: String,
    val apiKey: String,
    val baseUrl: String,
    val thorough: Boolean = false,
)

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
    val lastCustomUrl: String get() = prefs.getString(BASE_URL, null).orEmpty()
    val thorough: Boolean get() = prefs.getBoolean(THOROUGH, false)

    fun setThorough(value: Boolean) {
        prefs.edit().putBoolean(THOROUGH, value).apply()
        _config.value = load()
    }

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

    /** [customUrl] is required for [AiProvider.CUSTOM] and must be HTTPS. */
    fun connect(provider: AiProvider, model: String, apiKey: String, customUrl: String = "") {
        val url = provider.baseUrl ?: requireHttps(customUrl)
        prefs.edit()
            .putString(PROVIDER, provider.name)
            .putString(MODEL, model.trim().ifEmpty { provider.defaultModel })
            .putString(KEY, KeyVault.encrypt(apiKey.trim()))
            .putString(BASE_URL, url)
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
        val url = provider.baseUrl ?: prefs.getString(BASE_URL, null) ?: return null
        return AssistantConfig(provider, prefs.getString(MODEL, null) ?: provider.defaultModel, key, url, prefs.getBoolean(THOROUGH, false))
    }

    private companion object {
        const val PROVIDER = "provider"
        const val MODEL = "model"
        const val KEY = "api_key"
        const val SHARE_NOTES = "share_notes"
        const val ALLOW_CHANGES = "allow_changes"
        const val BASE_URL = "base_url"
        const val THOROUGH = "thorough"
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

/** Normalises a custom server address; only HTTPS is allowed so keys and data are never sent in the clear. */
fun requireHttps(url: String): String {
    val u = url.trim().trimEnd('/')
    require(u.startsWith("https://") && u.length > "https://".length) { "The server address must start with https://" }
    return u.removeSuffix("/chat/completions")
}
