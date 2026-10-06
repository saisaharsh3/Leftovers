package com.leftovers.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A command the model wants to run, in provider-neutral form. */
data class ToolCall(val id: String, val name: String, val args: JSONObject)

/** What came back for a [ToolCall]; [content] is JSON or plain text for the model to read. */
data class ToolOutcome(val call: ToolCall, val content: String, val isError: Boolean = false)

/** One model reply: some text, and zero or more commands to run before it continues. */
data class ModelTurn(val text: String, val calls: List<ToolCall>, val truncated: Boolean = false)

class AiException(message: String, val status: Int? = null) : Exception(message)

/** A rejected request (400) that mentions [word], i.e. the model doesn't accept that setting. */
private fun AiException.rejects(word: String) = status == 400 && message.orEmpty().contains(word, ignoreCase = true)

/**
 * A running conversation with one provider. Each implementation keeps the provider's own message
 * history and only ever appends to it, so earlier turns are sent back exactly as received.
 */
/** A photo attached to a message, already resized and re-encoded (so no EXIF or location data). */
class ImageInput(val mimeType: String, val base64: String)

interface ChatSession {
    suspend fun sendUser(text: String, image: ImageInput? = null): ModelTurn
    suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn
}

fun chatSession(config: AssistantConfig, system: String, tools: List<ToolSpec>): ChatSession = when (config.provider.style) {
    ApiStyle.CLAUDE -> ClaudeSession(config, system, tools)
    ApiStyle.OPENAI -> OpenAiSession(config, system, tools)
    ApiStyle.GEMINI -> GeminiSession(config, system, tools)
}

/** The models [apiKey] can use, newest names first where the provider orders them; chat models only. */
suspend fun listModels(provider: AiProvider, baseUrl: String, apiKey: String): List<String> {
    val notChat = Regex("embed|tts|whisper|dall-e|image|audio|moderation|transcri|realtime|speech|rerank|guard|vision-preview|search|lyria|robotics|veo|imagen|computer-use|antigravity|deep-research|nano-banana|omni", RegexOption.IGNORE_CASE)
    val ids = when (provider.style) {
        ApiStyle.CLAUDE -> getJson("$baseUrl/models?limit=100", mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"))
            .optJSONArray("data").ids("id")
        ApiStyle.GEMINI -> {
            val res = getJson("$baseUrl/models?pageSize=200", mapOf("x-goog-api-key" to apiKey))
            val models = res.optJSONArray("models") ?: JSONArray()
            (0 until models.length()).map { models.getJSONObject(it) }
                .filter { m -> m.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.optString(it) == "generateContent" } } == true }
                .map { it.optString("name").removePrefix("models/") }
        }
        ApiStyle.OPENAI -> getJson("$baseUrl/models", authHeader(apiKey)).optJSONArray("data").ids("id")
    }
    return ids.filter { it.isNotBlank() && !notChat.containsMatchIn(it) }.distinct()
}

private fun JSONArray?.ids(key: String): List<String> = if (this == null) emptyList() else (0 until length()).map { optJSONObject(it)?.optString(key).orEmpty() }

private fun authHeader(apiKey: String) = if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")

// ---------- Claude (Messages API) ----------

private class ClaudeSession(private val config: AssistantConfig, private val system: String, tools: List<ToolSpec>) : ChatSession {
    private val messages = JSONArray()
    private var useEffort = true
    private val toolsJson = JSONArray(tools.map { t ->
        JSONObject().put("name", t.name).put("description", t.description).put("input_schema", t.jsonSchema())
    })

    override suspend fun sendUser(text: String, image: ImageInput?): ModelTurn {
        val content: Any = if (image == null) text else JSONArray()
            .put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", image.mimeType).put("data", image.base64)))
            .put(JSONObject().put("type", "text").put("text", text))
        messages.put(JSONObject().put("role", "user").put("content", content))
        return call()
    }

    override suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn {
        // Every result for a turn goes back together in one user message.
        val blocks = JSONArray(results.map {
            JSONObject().put("type", "tool_result").put("tool_use_id", it.call.id).put("content", it.content).put("is_error", it.isError)
        })
        messages.put(JSONObject().put("role", "user").put("content", blocks))
        return call()
    }

    private suspend fun call(): ModelTurn {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 16000)
            .put("system", system)
            .put("tools", toolsJson)
            .put("messages", messages)
        val headers = mutableMapOf("x-api-key" to config.apiKey, "anthropic-version" to "2023-06-01")
        val effort = if (config.thorough) "high" else "low"
        // Haiku takes neither effort nor fallbacks; the current Opus and Sonnet take both.
        // Low effort keeps chat replies quick; it's dropped if this model doesn't take it.
        if (useEffort && !config.model.startsWith("claude-haiku")) body.put("output_config", JSONObject().put("effort", effort))
        if (config.model == "claude-opus-5-5" || config.model == "claude-sonnet-5-5") {
            // If a safety classifier declines, the API retries on Anthropic's recommended model.
            body.put("fallbacks", "default")
            headers["anthropic-beta"] = "server-side-fallback-2026-07-01"
        }
        val res = try {
            postJson("${config.baseUrl}/messages", headers, body) { it.optJSONObject("error")?.optString("message") }
        } catch (e: AiException) {
            if (!useEffort || !e.rejects("effort")) throw e
            useEffort = false
            return call()
        }
        when (res.optString("stop_reason")) {
            "refusal" -> throw AiException("Claude declined to answer that. Try rephrasing it.")
        }
        val content = res.optJSONArray("content") ?: JSONArray()
        // Append the reply exactly as received (thinking blocks included).
        messages.put(JSONObject().put("role", "assistant").put("content", content))
        val text = StringBuilder()
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                "tool_use" -> calls += ToolCall(block.getString("id"), block.getString("name"), block.optJSONObject("input") ?: JSONObject())
            }
        }
        return ModelTurn(text.toString().trim(), calls, truncated = res.optString("stop_reason") == "max_tokens")
    }
}

// ---------- ChatGPT (Chat Completions) ----------

private class OpenAiSession(private val config: AssistantConfig, system: String, tools: List<ToolSpec>) : ChatSession {
    private val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
    private var useReasoning = true
    private val toolsJson = JSONArray(tools.map { t ->
        JSONObject().put("type", "function").put(
            "function",
            JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.jsonSchema()),
        )
    })

    override suspend fun sendUser(text: String, image: ImageInput?): ModelTurn {
        val content: Any = if (image == null) text else JSONArray()
            .put(JSONObject().put("type", "text").put("text", text))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${image.mimeType};base64,${image.base64}")))
        messages.put(JSONObject().put("role", "user").put("content", content))
        return call()
    }

    override suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn {
        results.forEach { messages.put(JSONObject().put("role", "tool").put("tool_call_id", it.call.id).put("content", it.content)) }
        return call()
    }

    private suspend fun call(): ModelTurn {
        val body = JSONObject().put("model", config.model).put("messages", messages).put("tools", toolsJson)
        // Only OpenAI's own reasoning models take this; other compatible servers vary, so leave it off there.
        if (useReasoning && config.provider == AiProvider.OPENAI) body.put("reasoning_effort", if (config.thorough) "high" else "low")
        val res = try {
            postJson("${config.baseUrl}/chat/completions", authHeader(config.apiKey), body) {
                it.optJSONObject("error")?.optString("message")
            }
        } catch (e: AiException) {
            // Older models don't take reasoning_effort; ask again without it.
            if (!useReasoning || !e.rejects("reasoning")) throw e
            useReasoning = false
            return call()
        }
        val choice = res.optJSONArray("choices")?.optJSONObject(0) ?: throw AiException("${config.provider.label} sent an empty reply")
        val message = choice.getJSONObject("message")
        val toolCalls = message.optJSONArray("tool_calls")
        val echo = JSONObject().put("role", "assistant").put("content", message.opt("content") ?: JSONObject.NULL)
        if (toolCalls != null && toolCalls.length() > 0) echo.put("tool_calls", toolCalls)
        messages.put(echo)
        message.optString("refusal").takeIf { it.isNotBlank() && it != "null" }?.let { throw AiException(it) }
        val calls = (0 until (toolCalls?.length() ?: 0)).map { i ->
            val tc = toolCalls!!.getJSONObject(i)
            val fn = tc.getJSONObject("function")
            ToolCall(tc.getString("id"), fn.getString("name"), runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject()))
        }
        val text = if (message.isNull("content")) "" else message.optString("content")
        return ModelTurn(text.trim(), calls, truncated = choice.optString("finish_reason") == "length")
    }
}

// ---------- Gemini (generateContent) ----------

private class GeminiSession(private val config: AssistantConfig, private val system: String, tools: List<ToolSpec>) : ChatSession {
    private val contents = JSONArray()
    private var useThinkingLevel = !config.model.startsWith("gemini-2")
    private val toolsJson = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray(tools.map { t ->
            JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.geminiSchema())
        })),
    )

    override suspend fun sendUser(text: String, image: ImageInput?): ModelTurn {
        val parts = JSONArray()
        image?.let { parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", it.mimeType).put("data", it.base64))) }
        parts.put(JSONObject().put("text", text))
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        return call()
    }

    override suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn {
        val parts = JSONArray(results.map {
            val response = JSONObject().put(if (it.isError) "error" else "result", it.content)
            val fr = JSONObject().put("name", it.call.name).put("response", response)
            if (!it.call.id.startsWith(LOCAL_ID)) fr.put("id", it.call.id)
            JSONObject().put("functionResponse", fr)
        })
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        return call()
    }

    private suspend fun call(): ModelTurn {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", toolsJson)
        if (useThinkingLevel) {
            body.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", if (config.thorough) "high" else "low")))
        }
        val url = "${config.baseUrl}/models/${URLEncoder.encode(config.model, "UTF-8")}:generateContent"
        val res = try {
            postJson(url, mapOf("x-goog-api-key" to config.apiKey), body) { it.optJSONObject("error")?.optString("message") }
        } catch (e: AiException) {
            // Models without thinking levels reject the setting; ask again without it.
            if (!useThinkingLevel || !e.rejects("thinking")) throw e
            useThinkingLevel = false
            return call()
        }
        val candidate = res.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException(res.optJSONObject("promptFeedback")?.optString("blockReason")?.let { "Gemini blocked that request ($it)" } ?: "Gemini sent an empty reply")
        val content = candidate.optJSONObject("content") ?: JSONObject().put("role", "model").put("parts", JSONArray())
        if (!content.has("role")) content.put("role", "model")
        // Echo the content unchanged so any thought signatures go back with it.
        contents.put(content)
        val parts = content.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.optBoolean("thought")) continue
            part.optString("text").takeIf { part.has("text") }?.let { text.append(it) }
            part.optJSONObject("functionCall")?.let { fc ->
                calls += ToolCall(fc.optString("id").ifBlank { "$LOCAL_ID$i" }, fc.getString("name"), fc.optJSONObject("args") ?: JSONObject())
            }
        }
        return ModelTurn(text.toString().trim(), calls, truncated = candidate.optString("finishReason") == "MAX_TOKENS")
    }

    private companion object {
        const val LOCAL_ID = "local-"
    }
}

// ---------- HTTP ----------

/** Retries briefly when the service is overloaded or hiccups (5xx), which is common at busy times. */
private suspend fun postJson(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
    errorMessage: (JSONObject) -> String?,
): JSONObject {
    var attempt = 0
    while (true) {
        try {
            return postOnce(url, headers, body, errorMessage)
        } catch (e: RetryableException) {
            if (++attempt > 2) throw AiException(e.message ?: "The AI service is busy. Try again in a moment.")
            kotlinx.coroutines.delay(1500L * attempt * attempt)
        }
    }
}

private class RetryableException(message: String) : Exception(message)

/** One HTTP exchange. Swapped for a fake in tests so every provider's format can be checked without keys. */
fun interface HttpTransport {
    /** Throws [java.io.IOException] when the server can't be reached. */
    suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpReply
}

class HttpReply(val code: Int, val body: String)

/** The real network, over HttpURLConnection (HTTPS only, enforced by the app's network security config). */
object UrlConnectionTransport : HttpTransport {
    override suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpReply = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = if (method == "GET") 30_000 else 120_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            HttpReply(code, (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}

@Volatile
var transport: HttpTransport = UrlConnectionTransport

private suspend fun getJson(url: String, headers: Map<String, String>): JSONObject {
    val reply = try {
        transport.send("GET", url, headers, null)
    } catch (e: java.io.IOException) {
        throw AiException("Couldn't reach the server. Check the address and your internet connection.")
    }
    val json = runCatching { JSONObject(reply.body) }.getOrNull()
    if (reply.code !in 200..299) {
        val detail = json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        throw AiException(if (reply.code == 401 || reply.code == 403) "The API key was rejected." else detail ?: "Couldn't load models (${reply.code})", reply.code)
    }
    return json ?: throw AiException("Couldn't read the model list")
}

private suspend fun postOnce(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
    errorMessage: (JSONObject) -> String?,
): JSONObject {
    val reply = try {
        transport.send("POST", url, headers, body.toString())
    } catch (e: java.io.IOException) {
        throw AiException("Couldn't reach the AI. Check your internet connection.")
    }
    val code = reply.code
    val json = runCatching { JSONObject(reply.body) }.getOrNull()
    if (code !in 200..299) {
        val detail = json?.let(errorMessage)?.takeIf { it.isNotBlank() }
        if (code in 500..599) throw RetryableException(detail?.let { "The AI service is busy: $it" } ?: "The AI service is having trouble right now. Try again in a moment.")
        throw AiException(
            status = code,
            message = when (code) {
                401, 403 -> "The API key was rejected. Check it in Settings → AI assistant."
                404 -> "Model not found${detail?.let { ": $it" } ?: ""}. Check the model name in Settings."
                429 -> "Too many requests or out of credit${detail?.let { ": $it" } ?: ""}"
                else -> detail ?: "Request failed ($code)"
            },
        )
    }
    return json ?: throw AiException("Couldn't read the AI's reply")
}
