package com.leftovers.app.ai

import org.json.JSONObject

/**
 * The assistant on the phone's own model. It has no tool calling of its own, so the tools are written out in the
 * prompt and it answers with one line, CALL {"tool": …, "args": {…}}, when it wants one; the app runs it as for any
 * other AI, changes still waiting for Apply. The model reads about 4,000 tokens at most, so the prompt keeps the
 * app's facts, the tools in one line each, and as much of the conversation as fits, newest kept.
 */
internal class OnDeviceSession(
    system: String,
    private val tools: List<ToolSpec>,
    private val model: suspend () -> LocalModel? = { OnDevice.override ?: OnDevice.model },
) : ChatSession {
    /** The app's facts (date, currency, categories, accounts): the first part of the usual instructions. */
    private val facts = system.substringBefore("\n\n").take(1_500)
    private val turns = mutableListOf<String>()
    private var calls = 0
    /** The photo sent with the latest message, read again with each step of the answer to it. */
    private var photo: ByteArray? = null

    override suspend fun sendUser(text: String, image: ImageInput?): ModelTurn {
        photo = image?.let { java.util.Base64.getDecoder().decode(it.base64) }
        turns += "User: ${text.trim()}" + if (photo != null) " [photo attached]" else ""
        return reply()
    }

    override suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn {
        results.forEach { turns += "Result of ${it.call.name}: ${it.content.take(1_200)}" }
        return reply()
    }

    private suspend fun reply(): ModelTurn {
        val m = model() ?: throw AiException("This phone's AI isn't in this build.")
        val status = try {
            // Right after the app opens, Android may still be starting its AI: wait for it rather than give up.
            if (m === (OnDevice.override ?: OnDevice.model)) OnDevice.awaitReady() else m.status()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AiException("This phone's AI isn't ready: ${OnDevice.failed(e)}")
        }
        if (status != LocalStatus.READY) {
            throw AiException("This phone's AI isn't ready" + (OnDevice.problem?.let { ": $it" } ?: ". Check it in the AI assistant's settings."))
        }
        val answer = try {
            m.generate(prompt(m.tokenLimit()), MAX_OUT, photo).trim()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AiException("This phone's AI couldn't answer: ${OnDevice.failed(e)}")
        }
        val call = parseCall(answer, tools.map { it.name }.toSet())
        // A few lookups per question at most, so a confused small model can't loop.
        if (call != null && calls < 4) {
            calls++
            turns += "Assistant: CALL " + JSONObject().put("tool", call.name).put("args", call.args)
            return ModelTurn("", listOf(call))
        }
        calls = 0
        photo = null
        val text = answer.substringBefore("CALL").trim().ifEmpty { "Sorry, I couldn't work that out. Try asking more simply." }
        turns += "Assistant: $text"
        return ModelTurn(text, emptyList())
    }

    /** The prompt, trimmed to fit [limit] tokens with room for the answer; older turns go first. */
    internal fun prompt(limit: Int): String {
        val head = buildString {
            append(facts).append("\n\n")
            append("Tools you can use:\n")
            tools.forEach { t ->
                val params = t.params.joinToString(", ") { p ->
                    p.name + ":" + p.type + (if (p.required) "*" else "") + (p.enum?.let { " (" + it.joinToString("|") + ")" } ?: "")
                }
                append("- ").append(t.name).append("(").append(params).append("): ").append(t.description.take(110)).append('\n')
            }
            append("\nTo use a tool, reply with one line only: CALL {\"tool\": \"name\", \"args\": {...}} (* marks required args). ")
            append("Look up amounts, dates and ids with the tools; never make them up. Changes are shown to the user to Apply, so call them directly. ")
            append("Otherwise answer the user briefly in plain words, writing money with its currency symbol (₹1,250, not 1250 INR).\n")
            if (photo != null) {
                append("The photo is a bill or receipt: read the total actually paid, the date and the shop, then add it with add_entry, ")
                append("or add_split_bill if its items belong to different categories. Use the date on the bill.\n")
            }
            append('\n')
        }
        // About 3.5 characters to a token; the answer needs its share.
        val room = ((limit - MAX_OUT) * 3.5).toInt() - head.length
        val kept = ArrayDeque<String>()
        var used = 0
        for (t in turns.asReversed()) {
            if (used + t.length + 1 > room && kept.isNotEmpty()) break
            kept.addFirst(t.take(room.coerceAtLeast(200)))
            used += t.length + 1
        }
        return head + kept.joinToString("\n") + "\nAssistant:"
    }

    companion object {
        private const val MAX_OUT = 400

        /** A CALL line naming one of [names], with its arguments; null for a plain answer. */
        internal fun parseCall(answer: String, names: Set<String>): ToolCall? {
            val at = answer.indexOf("CALL")
            val json = OnDevice.jsonIn(if (at >= 0) answer.substring(at) else answer.takeIf { it.trimStart().startsWith("{") } ?: return null) ?: return null
            val name = json.optString("tool").ifEmpty { json.optString("name") }
            if (name !in names) return null
            return ToolCall("local-${System.nanoTime()}", name, json.optJSONObject("args") ?: json.optJSONObject("arguments") ?: JSONObject())
        }
    }
}
