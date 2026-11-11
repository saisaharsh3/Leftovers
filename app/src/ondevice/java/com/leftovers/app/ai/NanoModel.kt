package com.leftovers.app.ai

import androidx.annotation.Keep
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/**
 * Gemini Nano through Android's AICore, which keeps the model on the phone and shares it between apps: nothing is
 * downloaded for Leftovers on a phone that already has it. Dev builds only; the app finds this class by name.
 */
@Keep
class NanoModel : LocalModel {
    private val client by lazy { Generation.getClient() }

    override suspend fun status(): LocalStatus = when (client.checkStatus()) {
        FeatureStatus.AVAILABLE -> LocalStatus.READY
        FeatureStatus.DOWNLOADABLE -> LocalStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> LocalStatus.DOWNLOADING
        else -> LocalStatus.UNAVAILABLE
    }

    override suspend fun download() {
        client.download().collect { if (it is DownloadStatus.DownloadFailed) throw it.e }
    }

    override suspend fun generate(prompt: String, maxOutputTokens: Int, image: ByteArray?): String {
        val text = TextPart(prompt)
        val settings: GenerateContentRequest.Builder.() -> Unit = {
            temperature = 0.2f
            this.maxOutputTokens = maxOutputTokens
        }
        // A photo, such as a bill, goes first, then what to do with it.
        val request = if (image != null) generateContentRequest(ImagePart(image), text, settings) else generateContentRequest(text, settings)
        val response = client.generateContent(request)
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override suspend fun warmup() {
        client.warmup()
    }

    override suspend fun tokenLimit(): Int = runCatching { client.getTokenLimit() }.getOrDefault(4_000)

    override fun explain(e: Throwable): String {
        val code = (e as? GenAiException ?: e.cause as? GenAiException)?.errorCode
            ?: return "${e.javaClass.simpleName}${e.message?.let { ": $it" }.orEmpty()}"
        val why = when (code) {
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> "Android AICore on this phone is missing or too old. Update \"Android AICore\" in the Play Store, then try again."
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> "it needs a system update: check Settings → System → Software update."
            GenAiException.ErrorCode.NOT_AVAILABLE -> "Android hasn't turned it on for this phone yet. It's also off on phones with an unlocked bootloader."
            GenAiException.ErrorCode.NOT_SUPPORTED -> "Android says this phone doesn't support it."
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> "there isn't enough free storage for the model."
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> "Android only lets it answer while Leftovers is open."
            GenAiException.ErrorCode.BUSY, GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> "it's busy or has been used a lot; try again in a while."
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> "the question was too long for it."
            else -> e.message?.takeIf { it.isNotBlank() } ?: "it stopped"
        }
        return "$why (code $code)"
    }
}
