package com.leftovers.app.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Plays each provider's side of a conversation with canned replies in that provider's real format,
 * and checks what the app sends and how it reads the answers. Runs without any API keys.
 */
class ProviderSessionsTest {
    private class Request(val method: String, val url: String, val headers: Map<String, String>, val body: JSONObject?)

    private val sent = mutableListOf<Request>()
    private val replies = ArrayDeque<HttpReply>()

    @Before fun fakeNetwork() {
        transport = HttpTransport { method, url, headers, body ->
            sent += Request(method, url, headers, body?.let(::JSONObject))
            replies.removeFirstOrNull() ?: error("No reply queued for $url")
        }
    }

    @After fun realNetwork() {
        transport = UrlConnectionTransport
    }

    private fun reply(json: String, code: Int = 200) = replies.addLast(HttpReply(code, json))

    private val tools = listOf(
        ToolSpec("get_overview", "Overview", emptyList()),
        ToolSpec(
            "add_entry", "Add an entry",
            listOf(Param("amount", "number", "Amount", required = true), Param("type", "string", "Type", enum = listOf("expense", "income"))),
            changesData = true,
        ),
    )

    private fun config(provider: AiProvider, model: String = provider.defaultModel, key: String = "test-key", url: String? = null, thorough: Boolean = false) =
        AssistantConfig(provider, model, key, url ?: provider.baseUrl!!, thorough)

    private val photo = ImageInput("image/jpeg", "QUJD")

    // ---------- Claude ----------

    @Test fun claudeToolLoop() = runBlocking {
        val session = chatSession(config(AiProvider.CLAUDE), "system rules", tools)
        reply("""{"content":[{"type":"thinking","thinking":"","signature":"sig1"},{"type":"text","text":"Checking"},
            {"type":"tool_use","id":"toolu_1","name":"add_entry","input":{"amount":250,"type":"expense"}}],"stop_reason":"tool_use"}""")
        val turn = session.sendUser("Add 250 for coffee")

        val first = sent.single()
        assertEquals("https://api.anthropic.com/v1/messages", first.url)
        assertEquals("test-key", first.headers["x-api-key"])
        assertEquals("2023-06-01", first.headers["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", first.headers["anthropic-beta"])
        val body = first.body!!
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        assertEquals("system rules", body.getString("system"))
        val schema = body.getJSONArray("tools").getJSONObject(1).getJSONObject("input_schema")
        assertEquals("object", schema.getString("type"))
        assertEquals(JSONArray(listOf("amount")).toString(), schema.getJSONArray("required").toString())

        assertEquals("Checking", turn.text)
        assertEquals(ToolCall("toolu_1", "add_entry", JSONObject()).let { it.id to it.name }, turn.calls.single().let { it.id to it.name })
        assertEquals(250, turn.calls.single().args.getInt("amount"))

        reply("""{"content":[{"type":"text","text":"Added."}],"stop_reason":"end_turn"}""")
        val done = session.sendToolResults(listOf(ToolOutcome(turn.calls.single(), "Applied: Added")))
        assertEquals("Added.", done.text)
        val messages = sent.last().body!!.getJSONArray("messages")
        // The assistant turn goes back exactly as received, thinking block included.
        val echoed = messages.getJSONObject(1).getJSONArray("content")
        assertEquals("thinking", echoed.getJSONObject(0).getString("type"))
        assertEquals("sig1", echoed.getJSONObject(0).getString("signature"))
        val result = messages.getJSONObject(2).getJSONArray("content").getJSONObject(0)
        assertEquals("tool_result", result.getString("type"))
        assertEquals("toolu_1", result.getString("tool_use_id"))
        assertEquals("Applied: Added", result.getString("content"))
    }

    @Test fun claudeThoroughHaikuAndPhoto() = runBlocking {
        reply("""{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}""")
        chatSession(config(AiProvider.CLAUDE, thorough = true), "s", tools).sendUser("hi", photo)
        assertEquals("high", sent.last().body!!.getJSONObject("output_config").getString("effort"))
        val content = sent.last().body!!.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val image = content.getJSONObject(0)
        assertEquals("image", image.getString("type"))
        assertEquals("base64", image.getJSONObject("source").getString("type"))
        assertEquals("image/jpeg", image.getJSONObject("source").getString("media_type"))
        assertEquals("QUJD", image.getJSONObject("source").getString("data"))
        assertEquals("hi", content.getJSONObject(1).getString("text"))

        reply("""{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}""")
        chatSession(config(AiProvider.CLAUDE, model = "claude-haiku-4-5"), "s", tools).sendUser("hi")
        val haiku = sent.last()
        assertFalse(haiku.body!!.has("output_config"))
        assertFalse(haiku.body.has("fallbacks"))
        assertNull(haiku.headers["anthropic-beta"])
    }

    @Test fun claudeRetriesWithoutEffortWhenRejected() = runBlocking {
        reply("""{"error":{"type":"invalid_request_error","message":"output_config.effort is not supported"}}""", 400)
        reply("""{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}""")
        val turn = chatSession(config(AiProvider.CLAUDE, model = "claude-some-older"), "s", tools).sendUser("hi")
        assertEquals("ok", turn.text)
        assertTrue(sent[0].body!!.has("output_config"))
        assertFalse(sent[1].body!!.has("output_config"))
    }

    @Test fun claudeRefusalIsReported() = runBlocking {
        reply("""{"content":[],"stop_reason":"refusal","stop_details":{"type":"refusal","category":null}}""")
        try {
            chatSession(config(AiProvider.CLAUDE), "s", tools).sendUser("hi")
            fail("expected a refusal")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("declined"))
        }
    }

    // ---------- ChatGPT and OpenAI-compatible providers ----------

    @Test fun openAiToolLoop() = runBlocking {
        val session = chatSession(config(AiProvider.OPENAI), "rules", tools)
        reply("""{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"refusal":null,
            "tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_overview","arguments":"{}"}},
                          {"id":"call_2","type":"function","function":{"name":"add_entry","arguments":"{\"amount\":99.5}"}}]}}]}""")
        val turn = session.sendUser("hi")
        val req = sent.single()
        assertEquals("https://api.openai.com/v1/chat/completions", req.url)
        assertEquals("Bearer test-key", req.headers["Authorization"])
        assertEquals("low", req.body!!.getString("reasoning_effort"))
        assertEquals("system", req.body.getJSONArray("messages").getJSONObject(0).getString("role"))
        val fn = req.body.getJSONArray("tools").getJSONObject(1)
        assertEquals("function", fn.getString("type"))
        assertEquals("add_entry", fn.getJSONObject("function").getString("name"))

        assertEquals(listOf("get_overview", "add_entry"), turn.calls.map { it.name })
        assertEquals(99.5, turn.calls[1].args.getDouble("amount"), 0.0)

        reply("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Done"}}]}""")
        val done = session.sendToolResults(turn.calls.map { ToolOutcome(it, "ok") })
        assertEquals("Done", done.text)
        val msgs = sent.last().body!!.getJSONArray("messages")
        assertEquals(2, msgs.getJSONObject(2).getJSONArray("tool_calls").length())
        assertEquals("tool", msgs.getJSONObject(3).getString("role"))
        assertEquals("call_1", msgs.getJSONObject(3).getString("tool_call_id"))
        assertEquals("call_2", msgs.getJSONObject(4).getString("tool_call_id"))
    }

    @Test fun compatibleProvidersUseTheirOwnAddressAndNoReasoningSetting() = runBlocking {
        val expected = mapOf(
            AiProvider.MISTRAL to "https://api.mistral.ai/v1/chat/completions",
            AiProvider.GROQ to "https://api.groq.com/openai/v1/chat/completions",
            AiProvider.DEEPSEEK to "https://api.deepseek.com/v1/chat/completions",
            AiProvider.GROK to "https://api.x.ai/v1/chat/completions",
            AiProvider.OPENROUTER to "https://openrouter.ai/api/v1/chat/completions",
        )
        for ((provider, url) in expected) {
            reply("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"hi from ${provider.label}"}}]}""")
            val turn = chatSession(config(provider), "s", tools).sendUser("hi")
            assertEquals(url, sent.last().url)
            assertEquals("Bearer test-key", sent.last().headers["Authorization"])
            assertFalse("${provider.label} shouldn't get reasoning_effort", sent.last().body!!.has("reasoning_effort"))
            assertEquals("hi from ${provider.label}", turn.text)
        }
    }

    @Test fun customServerWithoutKeyAndWithPhoto() = runBlocking {
        reply("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"read it"}}]}""")
        chatSession(config(AiProvider.CUSTOM, model = "llama", key = "", url = "https://my.server/v1"), "s", tools).sendUser("bill", photo)
        val req = sent.single()
        assertEquals("https://my.server/v1/chat/completions", req.url)
        assertNull(req.headers["Authorization"])
        val parts = req.body!!.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("data:image/jpeg;base64,QUJD", parts.getJSONObject(1).getJSONObject("image_url").getString("url"))
    }

    @Test fun openAiRetriesWithoutReasoningWhenRejected() = runBlocking {
        reply("""{"error":{"message":"Unsupported parameter: 'reasoning_effort' is not supported with this model."}}""", 400)
        reply("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok"}}]}""")
        val turn = chatSession(config(AiProvider.OPENAI, model = "gpt-4o"), "s", tools).sendUser("hi")
        assertEquals("ok", turn.text)
        assertTrue(sent[0].body!!.has("reasoning_effort"))
        assertFalse(sent[1].body!!.has("reasoning_effort"))
    }

    // ---------- Gemini ----------

    @Test fun geminiToolLoop() = runBlocking {
        val session = chatSession(config(AiProvider.GEMINI), "rules", tools)
        reply("""{"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
            {"text":"thinking…","thought":true},
            {"functionCall":{"name":"add_entry","args":{"amount":120},"id":"fc_9"},"thoughtSignature":"SIG"}]}}]}""")
        val turn = session.sendUser("Add 120 bus")
        val req = sent.single()
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent", req.url)
        assertEquals("test-key", req.headers["x-goog-api-key"])
        assertEquals("low", req.body!!.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertEquals("rules", req.body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val params = req.body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(1).getJSONObject("parameters")
        assertEquals("OBJECT", params.getString("type"))
        assertEquals("NUMBER", params.getJSONObject("properties").getJSONObject("amount").getString("type"))

        assertEquals("", turn.text) // thought parts are never shown
        assertEquals("fc_9", turn.calls.single().id)
        assertEquals(120, turn.calls.single().args.getInt("amount"))

        reply("""{"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"text":"Added ₹120."}]}}]}""")
        val done = session.sendToolResults(listOf(ToolOutcome(turn.calls.single(), "Applied")))
        assertEquals("Added ₹120.", done.text)
        val contents = sent.last().body!!.getJSONArray("contents")
        assertEquals("SIG", contents.getJSONObject(1).getJSONArray("parts").getJSONObject(1).getString("thoughtSignature"))
        val fr = contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
        assertEquals("add_entry", fr.getString("name"))
        assertEquals("fc_9", fr.getString("id"))
        assertEquals("Applied", fr.getJSONObject("response").getString("result"))
    }

    @Test fun geminiPhotoThoroughAndOldModels() = runBlocking {
        reply("""{"candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]}}]}""")
        chatSession(config(AiProvider.GEMINI, thorough = true), "s", tools).sendUser("bill", photo)
        val parts = sent.last().body!!.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals("QUJD", parts.getJSONObject(0).getJSONObject("inlineData").getString("data"))
        assertEquals("bill", parts.getJSONObject(1).getString("text"))
        assertEquals("high", sent.last().body!!.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))

        reply("""{"candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]}}]}""")
        chatSession(config(AiProvider.GEMINI, model = "gemini-2.5-flash"), "s", tools).sendUser("hi")
        assertFalse(sent.last().body!!.has("generationConfig"))
    }

    @Test fun geminiRetriesWithoutThinkingLevelWhenRejected() = runBlocking {
        reply("""{"error":{"code":400,"message":"Thinking level MINIMAL is not supported for this model."}}""", 400)
        reply("""{"candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]}}]}""")
        val turn = chatSession(config(AiProvider.GEMINI, model = "gemini-flash-latest"), "s", tools).sendUser("hi")
        assertEquals("ok", turn.text)
        assertFalse(sent[1].body!!.has("generationConfig"))
    }

    // ---------- Errors and model lists ----------

    @Test fun badKeyAndNoNetworkGiveClearMessages() = runBlocking {
        reply("""{"error":{"message":"invalid x-api-key"}}""", 401)
        try {
            chatSession(config(AiProvider.CLAUDE), "s", tools).sendUser("hi")
            fail()
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("API key was rejected"))
        }
        transport = HttpTransport { _, _, _, _ -> throw java.io.IOException("offline") }
        try {
            chatSession(config(AiProvider.GEMINI), "s", tools).sendUser("hi")
            fail()
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("internet"))
        }
    }

    @Test fun modelListsAreReadAndFiltered() = runBlocking {
        reply("""{"data":[{"id":"claude-opus-5-5"},{"id":"claude-haiku-4-5"}]}""")
        assertEquals(listOf("claude-opus-5-5", "claude-haiku-4-5"), listModels(AiProvider.CLAUDE, AiProvider.CLAUDE.baseUrl!!, "k"))
        assertEquals("https://api.anthropic.com/v1/models?limit=100", sent.last().url)

        reply("""{"data":[{"id":"gpt-5-mini"},{"id":"text-embedding-3-small"},{"id":"whisper-1"},{"id":"gpt-image-1"}]}""")
        assertEquals(listOf("gpt-5-mini"), listModels(AiProvider.OPENAI, AiProvider.OPENAI.baseUrl!!, "k"))

        reply("""{"models":[{"name":"models/gemini-flash-lite-latest","supportedGenerationMethods":["generateContent"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
            {"name":"models/antigravity-preview-latest","supportedGenerationMethods":["generateContent"]}]}""")
        assertEquals(listOf("gemini-flash-lite-latest"), listModels(AiProvider.GEMINI, AiProvider.GEMINI.baseUrl!!, "k"))
    }
}
