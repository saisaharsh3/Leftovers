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
enum class ApiStyle { CLAUDE, OPENAI, GEMINI, ON_DEVICE }

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
    // The phone's own model (Gemini Nano): no key, nothing leaves the phone. Shown only in builds that have it.
    ON_DEVICE("On this phone", ApiStyle.ON_DEVICE, "on-device", "gemini-nano",
        listOf("gemini-nano"), "", null,
        note = "Runs on the phone with the AI Android keeps there. Nothing leaves the phone. It's small: best for short questions and simple changes, and only while Leftovers is open. The first time after opening Leftovers, it can take a few seconds to start."),
    CUSTOM("Custom", ApiStyle.OPENAI, null, "",
        emptyList(), "API key (if the server needs one)", null,
        note = "Any OpenAI-compatible server over HTTPS, e.g. one you host yourself. Only connect to servers you trust."),
}

/**
 * The phones and tablets Google lists for apps using Gemini Nano this way (ML Kit's Prompt API), by brand, as of
 * November 2026. Others may follow with Android updates; the setup sheet also checks the phone itself.
 */
val onDevicePhones: List<Pair<String, List<String>>> = listOf(
    "Google" to listOf("Pixel 9", "Pixel 9 Pro", "Pixel 9 Pro XL", "Pixel 9 Pro Fold", "Pixel 10", "Pixel 10 Pro", "Pixel 10 Pro XL", "Pixel 10 Pro Fold", "Pixel 11", "Pixel 11 Pro", "Pixel 11 Pro XL", "Pixel 11 Pro Fold"),
    "Samsung" to listOf("Galaxy S26", "Galaxy S26+", "Galaxy S26 Ultra", "Galaxy Z Fold7", "Galaxy Z TriFold", "Galaxy Z Flip8", "Galaxy Z Fold8", "Galaxy Z Fold8 Ultra"),
    "OnePlus" to listOf("OnePlus 13", "OnePlus 13s", "OnePlus 15", "OnePlus 15R"),
    "Xiaomi" to listOf("Xiaomi 14T Pro", "Xiaomi 15", "Xiaomi 15T", "Xiaomi 15T Pro", "Xiaomi 15 Ultra", "Xiaomi 17", "Xiaomi 17 Ultra", "Xiaomi Pad Mini"),
    "POCO" to listOf("POCO F7 Ultra", "POCO F8 Pro", "POCO F8 Ultra", "POCO X7 Pro", "POCO X8 Pro"),
    "OPPO" to listOf("Find N5", "Find X8", "Find X8 Pro", "Find X9", "Find X9 Pro", "Reno 14 Pro 5G", "Reno 15 Pro 5G", "Reno 15 Pro Mini 5G", "Reno 15 Pro Max 5G"),
    "vivo" to listOf("vivo X200", "vivo X200 Pro", "vivo X200 FE", "vivo X200T", "vivo X300", "vivo X300 Pro", "vivo T4 Ultra"),
    "iQOO" to listOf("iQOO 13", "iQOO 15"),
    "realme" to listOf("realme GT 7 Pro", "realme GT 7T"),
    "Honor" to listOf("Magic 7", "Magic 7 Pro", "Magic V5", "Magic 8 Pro"),
    "Motorola" to listOf("Razr 60 Ultra", "Razr Ultra 2025", "Signature"),
    "Sony" to listOf("Xperia 1 VIII"),
    "Sharp" to listOf("AQUOS R11"),
    "Lenovo" to listOf("Idea Tab Pro Gen 2", "Legion Tab Gen 5 (8.8\")"),
)

/** How to check whether a phone has the AI built in that apps can use, in a few steps. */
val onDeviceSteps: List<Pair<String, String>> = listOf(
    "Find your phone in the list" to "Tap \"Phones it works on\" above. If yours isn't there, it doesn't have it yet; more phones get it with updates.",
    "Check Android AICore" to "Settings → Apps → See all apps → AICore (show system apps if needed). With the AI in place it uses a few GB of storage. If it's missing, tap \"Get Android AICore\".",
    "Update it all" to "In the Play Store, update Android AICore and Private Compute Services, then install any system update in Settings → System.",
    "Look here" to "This page asks Android directly. \"Ready on this phone\" means it works; otherwise it says why. Right after opening the app it can take a few seconds to start.",
    "Good to know" to "The AI in your phone's own settings (OnePlus, Samsung, Xiaomi) and in apps like Google AI Edge Gallery can't be used by other apps. It's also off on phones with an unlocked bootloader.",
)

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
        prefs.getString("$MODEL.${provider.name}", null)
            ?: prefs.getString(MODEL, null)?.takeIf { lastProvider == provider }
            ?: provider.defaultModel

    /**
     * Each provider keeps its own key, so switching to the phone's AI and back to Gemini needs no key typed again.
     * Null when none is saved for [provider].
     */
    fun savedKey(provider: AiProvider): String? =
        (prefs.getString("$KEY.${provider.name}", null) ?: prefs.getString(KEY, null)?.takeIf { lastProvider == provider })
            ?.let(KeyVault::decrypt)?.takeIf { it.isNotEmpty() || provider == AiProvider.ON_DEVICE || provider == AiProvider.CUSTOM }

    /** The providers that can be switched to in one tap: a key is saved for them, or they need none. */
    fun ready(): List<AiProvider> = AiProvider.entries.filter {
        if (it == AiProvider.ON_DEVICE) OnDevice.inBuild else savedKey(it) != null && (it.baseUrl != null || lastCustomUrl.isNotEmpty())
    }

    /** Switches to [provider] with its saved key and model. */
    fun switchTo(provider: AiProvider) {
        val key = savedKey(provider) ?: if (provider == AiProvider.ON_DEVICE) "" else return
        connect(provider, lastModel(provider), key, lastCustomUrl)
    }
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
        val sealed = KeyVault.encrypt(apiKey.trim())
        val chosen = model.trim().ifEmpty { provider.defaultModel }
        prefs.edit()
            .putString(PROVIDER, provider.name)
            .putString(MODEL, chosen)
            .putString("$MODEL.${provider.name}", chosen)
            .putString(KEY, sealed)
            .putString("$KEY.${provider.name}", sealed)
            .apply {
                // The phone's own AI has no server; the custom server's address stays for switching back to it.
                if (provider != AiProvider.ON_DEVICE) putString(BASE_URL, url)
            }
            .apply()
        _config.value = load()
    }

    /** Disconnects and deletes the connected provider's key; keys saved for the others stay. */
    fun disconnect() {
        prefs.edit().remove(KEY).remove("$KEY.$lastProvider").apply()
        _config.value = null
    }

    private fun load(): AssistantConfig? {
        val provider = AiProvider.entries.firstOrNull { it.name == prefs.getString(PROVIDER, null) } ?: return null
        val key = prefs.getString(KEY, null)?.let(KeyVault::decrypt) ?: return null
        // Keys saved before each provider kept its own: filed under the provider they were for.
        if (!prefs.contains("$KEY.${provider.name}")) prefs.getString(KEY, null)?.let { prefs.edit().putString("$KEY.${provider.name}", it).apply() }
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

/** AES-GCM with a non-exportable Keystore key. Also protects the backup password. */
internal object KeyVault {
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
