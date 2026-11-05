package com.leftovers.app.ai

import androidx.annotation.Keep
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
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

    override suspend fun generate(prompt: String, maxOutputTokens: Int): String {
        val response = client.generateContent(
            generateContentRequest(TextPart(prompt)) {
                temperature = 0.2f
                this.maxOutputTokens = maxOutputTokens
            },
        )
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override suspend fun tokenLimit(): Int = runCatching { client.getTokenLimit() }.getOrDefault(4_000)
}
