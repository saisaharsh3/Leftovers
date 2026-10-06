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

class AiException(message: String) : Exception(message)

/**
 * A running conversation with one provider. Each implementation keeps the provider's own message
 * history and only ever appends to it, so earlier turns are sent back exactly as received.
 */
interface ChatSession {
    suspend fun sendUser(text: String): ModelTurn
    suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn
}

fun chatSession(config: AssistantConfig, system: String, tools: List<ToolSpec>): ChatSession = when (config.provider) {
    AiProvider.CLAUDE -> ClaudeSession(config, system, tools)
    AiProvider.OPENAI -> OpenAiSession(config, system, tools)
    AiProvider.GEMINI -> GeminiSession(config, system, tools)
}

// ---------- Claude (Messages API) ----------

private class ClaudeSession(private val config: AssistantConfig, private val system: String, tools: List<ToolSpec>) : ChatSession {
    private val messages = JSONArray()
    private val toolsJson = JSONArray(tools.map { t ->
        JSONObject().put("name", t.name).put("description", t.description).put("input_schema", t.jsonSchema())
    })

    override suspend fun sendUser(text: String): ModelTurn {
        messages.put(JSONObject().put("role", "user").put("content", text))
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
        // Haiku takes neither effort nor fallbacks; the current Opus and Sonnet take both.
        if (!config.model.startsWith("claude-haiku")) body.put("output_config", JSONObject().put("effort", "medium"))
        if (config.model == "claude-opus-5-5" || config.model == "claude-sonnet-5-5") {
            // If a safety classifier declines, the API retries on Anthropic's recommended model.
            body.put("fallbacks", "default")
            headers["anthropic-beta"] = "server-side-fallback-2026-07-01"
        }
        val res = postJson("https://api.anthropic.com/v1/messages", headers, body) { it.optJSONObject("error")?.optString("message") }
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
    private val toolsJson = JSONArray(tools.map { t ->
        JSONObject().put("type", "function").put(
            "function",
            JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.jsonSchema()),
        )
    })

    override suspend fun sendUser(text: String): ModelTurn {
        messages.put(JSONObject().put("role", "user").put("content", text))
        return call()
    }

    override suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn {
        results.forEach { messages.put(JSONObject().put("role", "tool").put("tool_call_id", it.call.id).put("content", it.content)) }
        return call()
    }

    private suspend fun call(): ModelTurn {
        val body = JSONObject().put("model", config.model).put("messages", messages).put("tools", toolsJson)
        val res = postJson("https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer ${config.apiKey}"), body) {
            it.optJSONObject("error")?.optString("message")
        }
        val choice = res.optJSONArray("choices")?.optJSONObject(0) ?: throw AiException("ChatGPT sent an empty reply")
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
    private val toolsJson = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray(tools.map { t ->
            JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.geminiSchema())
        })),
    )

    override suspend fun sendUser(text: String): ModelTurn {
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))))
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
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${URLEncoder.encode(config.model, "UTF-8")}:generateContent"
        val res = postJson(url, mapOf("x-goog-api-key" to config.apiKey), body) { it.optJSONObject("error")?.optString("message") }
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

private suspend fun postOnce(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
    errorMessage: (JSONObject) -> String?,
): JSONObject = withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 120_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (code !in 200..299) {
            val detail = json?.let(errorMessage)?.takeIf { it.isNotBlank() }
            if (code in 500..599) throw RetryableException(detail?.let { "The AI service is busy: $it" } ?: "The AI service is having trouble right now. Try again in a moment.")
            throw AiException(
                when (code) {
                    401, 403 -> "The API key was rejected. Check it in Settings → AI assistant."
                    404 -> "Model not found${detail?.let { ": $it" } ?: ""}. Check the model name in Settings."
                    429 -> "Too many requests or out of credit${detail?.let { ": $it" } ?: ""}"
                    else -> detail ?: "Request failed ($code)"
                },
            )
        }
        json ?: throw AiException("Couldn't read the AI's reply")
    } catch (e: java.io.IOException) {
        throw AiException("Couldn't reach the AI. Check your internet connection.")
    } finally {
        conn.disconnect()
    }
}
