package com.leftovers.app.ai

import org.json.JSONObject

/**
 * The assistant on the phone's own model. It has no tool calling of its own, so the tools are written out in the
 * prompt and it answers with one line, CALL {"tool": …, "args": {…}}, when it wants one; the app runs it as for any
 * other AI, changes still waiting for Apply. The model reads about 4,000 tokens at most, so the prompt keeps the
 * app's facts, the tools in one line each, and as much of the conversation as fits, newest kept.
 *
 * It's a small model, so the prompt is made easy for it: only the tools that fit the question (not all of them every
 * time), results as plain lines instead of JSON, two worked examples, and a reminder at the end of what to do next.
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
    /** The tools offered for the question being answered: see [shortlist]. */
    private var offered: List<ToolSpec> = tools

    override fun seed(history: List<PastTurn>) {
        normalised(history).forEach { turns += (if (it.user) "User: " else "Assistant: ") + it.text.trim() }
    }

    override suspend fun sendUser(text: String, image: ImageInput?): ModelTurn {
        photo = image?.let { java.util.Base64.getDecoder().decode(it.base64) }
        turns += "User: ${text.trim()}" + if (photo != null) " [photo attached]" else ""
        offered = shortlist(text, tools, photo != null)
        return reply()
    }

    override suspend fun sendToolResults(results: List<ToolOutcome>): ModelTurn {
        results.forEach { turns += "Result of ${it.call.name}: ${plain(it.content).take(1_500)}" }
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
            offered.forEach { t ->
                val params = t.params.joinToString(", ") { p ->
                    p.name + ":" + p.type + (if (p.required) "*" else "") + (p.enum?.let { " (" + it.joinToString("|") + ")" } ?: "")
                }
                append("- ").append(t.name).append("(").append(params).append("): ").append(t.description.take(110)).append('\n')
            }
            append("\nTo use a tool, reply with one line only: CALL {\"tool\": \"name\", \"args\": {...}} (* marks required args). ")
            append("Look up amounts, dates and ids with the tools; never make them up. Changes are shown to the user to Apply, so call them directly. ")
            append("Otherwise answer the user briefly in plain words, writing money with its currency symbol (₹1,250, not 1250 INR). ")
            append("Answer only from what a Result says; if it isn't there, say you don't know. Never mention tools, CALL or Result to the user.\n")
            // Shown how, a small model follows the pattern far more reliably than told how.
            append("\nExample:\nUser: How are things this month?\nAssistant: CALL {\"tool\": \"")
            append(offered.firstOrNull { !it.changesData }?.name ?: offered.firstOrNull()?.name ?: "tool")
            append("\", \"args\": {}}\nResult: spent: 2800\nAssistant: You've spent ₹2,800 this month.\n")
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
        // The last thing it reads is what to do now: after a result, answer from it (or look up one more thing).
        val next = if (turns.lastOrNull()?.startsWith("Result of ") == true) {
            "\n(Now answer the user's last question from the Result above, in one to three short sentences. Use the names and amounts exactly as the Result has them.)"
        } else ""
        return head + kept.joinToString("\n") + next + "\nAssistant:"
    }

    companion object {
        private const val MAX_OUT = 400
        /** Tools listed for one question when the app has more than this: the ones that fit it, and the basics. */
        private const val MAX_TOOLS = 8

        /** Words in a question that point to a tool. A tool not here goes by the words in its own name. */
        private val hints = mapOf(
            "get_overview" to "overview safe left today balance budget month how much doing things status remaining",
            "summarize" to "total spent spend how much sum average income earned between last this week month year",
            "group_by" to "category categories breakdown most biggest top where which by each split up",
            "find_entries" to "find show list entries entry transactions last recent yesterday when did what paid bought",
            "list_subscriptions" to "subscription subscriptions bill bills renew recurring due upcoming rent netflix",
            "list_goals" to "goal goals saving savings save target",
            "list_money_owed" to "owe owes owed lent lend borrowed borrow debt back friend who",
            "app_help" to "how do can where feature setting settings help turn enable",
            "make_statement_pdf" to "pdf statement report",
            "export_csv" to "csv export excel spreadsheet sheet",
            "add_entry" to "add log spent paid bought expense income received got salary",
            "update_entry" to "change edit update move fix correct rename wrong",
            "delete_entry" to "delete remove undo erase",
            "set_monthly_budget" to "budget monthly set",
            "set_category_limit" to "limit cap category",
            "add_subscription" to "subscription subscribe every monthly yearly recurring",
            "stop_subscription" to "cancel stop subscription unsubscribe",
            "add_transfer" to "transfer moved withdraw withdrew atm deposit between",
            "add_goal" to "goal save saving target new",
            "add_goal_deposit" to "goal put aside deposit saved add",
            "add_lent_or_borrowed" to "lent lend borrowed borrow gave owe owes",
            "add_split_bill" to "split bill shared share receipt with",
            "settle_up" to "settle paid back received repaid returned",
        )

        /** Always offered, so a plain question or "add 250 for coffee" works however it's worded. */
        private val basics = setOf("get_overview", "summarize", "find_entries", "add_entry")

        /**
         * The tools to offer for [question]: all of them when there are few, otherwise the [MAX_TOOLS] that fit it
         * best. A small model picks the right one from eight far more often than from thirty, and the rest of the
         * prompt has more room for the conversation.
         */
        internal fun shortlist(question: String, tools: List<ToolSpec>, photo: Boolean = false): List<ToolSpec> {
            if (tools.size <= MAX_TOOLS) return tools
            val words = Regex("[a-z]+").findAll(question.lowercase()).map { it.value }.toSet()
            fun score(t: ToolSpec): Int {
                val keys = (hints[t.name] ?: t.name.replace('_', ' ')).split(' ')
                var n = keys.count { it in words } * 2
                // Plurals and endings: "subscriptions", "owed", "spending".
                n += keys.count { k -> k !in words && k.length > 3 && words.any { w -> w.startsWith(k) || k.startsWith(w) && w.length > 3 } }
                if (t.name in basics) n += 1
                if (photo && (t.name == "add_entry" || t.name == "add_split_bill")) n += 10
                return n
            }
            val ranked = tools.map { it to score(it) }.sortedByDescending { it.second }
            val picked = ranked.take(MAX_TOOLS).map { it.first }.toSet()
            // In the app's own order, which groups looking things up before changing them.
            return tools.filter { it in picked }
        }

        /**
         * A tool's JSON result as plain lines ("owes user: 1550"), which a small model reads far more reliably than
         * braces and quotes. Text that isn't JSON is left as it is.
         */
        internal fun plain(content: String): String {
            val t = content.trim()
            val value: Any = runCatching {
                when {
                    t.startsWith("{") -> JSONObject(t)
                    t.startsWith("[") -> org.json.JSONArray(t)
                    else -> return content
                }
            }.getOrElse { return content }
            val out = StringBuilder()
            fun key(k: String) = k.replace('_', ' ')
            fun simple(v: Any?) = v !is JSONObject && v !is org.json.JSONArray
            fun write(v: Any?, indent: String) {
                when (v) {
                    is JSONObject -> v.keys().forEach { k ->
                        val x = v.opt(k)
                        if (simple(x)) out.append(indent).append(key(k)).append(": ").append(x).append('\n')
                        else { out.append(indent).append(key(k)).append(":\n"); write(x, "$indent  ") }
                    }
                    is org.json.JSONArray -> for (i in 0 until v.length()) {
                        val x = v.opt(i)
                        when {
                            simple(x) -> out.append(indent).append("- ").append(x).append('\n')
                            // One thing with only simple facts: on one line.
                            x is JSONObject && x.keys().asSequence().all { simple(x.opt(it)) } ->
                                out.append(indent).append("- ").append(x.keys().asSequence().joinToString(", ") { key(it) + ": " + x.opt(it) }).append('\n')
                            x is JSONObject -> {
                                val firstSimple = x.keys().asSequence().filter { simple(x.opt(it)) }.toList()
                                out.append(indent).append("- ").append(firstSimple.joinToString(", ") { key(it) + ": " + x.opt(it) }).append('\n')
                                x.keys().asSequence().filter { !simple(x.opt(it)) }.forEach { k -> out.append(indent).append("  ").append(key(k)).append(":\n"); write(x.opt(k), "$indent    ") }
                            }
                            else -> write(x, "$indent  ")
                        }
                    }
                    else -> out.append(indent).append(v).append('\n')
                }
            }
            write(value, "")
            return out.toString().trimEnd()
        }

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
