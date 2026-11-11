package com.leftovers.app.ai

import com.leftovers.app.util.MoneyText
import com.leftovers.app.util.SmsParser
import org.json.JSONObject

/** Whether the phone's own model can answer now. */
enum class LocalStatus { READY, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

/** A model that runs on the phone itself; nothing it reads leaves the phone. */
interface LocalModel {
    suspend fun status(): LocalStatus
    /** Asks Android to fetch the model when the phone supports it but hasn't got it yet. */
    suspend fun download()
    /** [image] is a photo (JPEG or PNG bytes) to read along with the prompt, such as a bill. */
    suspend fun generate(prompt: String, maxOutputTokens: Int, image: ByteArray? = null): String
    /** What a prompt and its answer can add up to, in tokens. */
    suspend fun tokenLimit(): Int
    /** Why [e] happened, in plain words, from Android's own error code. */
    fun explain(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    /** Loads the model ahead of the first question, so it answers sooner. */
    suspend fun warmup() {}
}

/**
 * The phone's own AI: Gemini Nano, which Android keeps on phones like the Pixel 9 and 10, Galaxy S26 and OnePlus 15R.
 * Only Dev builds made with Google's library have it (see build.gradle.kts); everywhere else [model] is null and
 * nothing here does anything.
 */
object OnDevice {
    /** Kept for tests, which swap in a fake. */
    @Volatile internal var override: LocalModel? = null

    val model: LocalModel? by lazy {
        runCatching { Class.forName("com.leftovers.app.ai.NanoModel").getDeclaredConstructor().newInstance() as LocalModel }.getOrNull()
    }

    private fun current(): LocalModel? = override ?: model

    /** This build can use the phone's AI, whether or not this phone has it. */
    val inBuild: Boolean get() = current() != null

    /** Why the phone's AI last said no, for the settings sheet and the chat; null when it last worked. */
    @Volatile var problem: String? = null
        private set

    suspend fun status(): LocalStatus {
        val m = current() ?: return LocalStatus.UNAVAILABLE
        return try {
            m.status().also { if (it == LocalStatus.READY) problem = null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching { android.util.Log.w("Leftovers", "On-device AI status", e) }
            problem = m.explain(e)
            LocalStatus.UNAVAILABLE
        }
    }

    /** How long the phone's AI may take to start after Leftovers opens: Android loads it on first use. */
    internal var startupMs = 15_000L

    /**
     * The status once Android has had time to start its AI. Right after Leftovers opens, Android's AICore can
     * answer "not available" for a few seconds while it loads the model; this asks again until it's ready, or the
     * time runs out. [onWaiting] is told while it waits, to say "Starting…" instead of "not ready".
     */
    suspend fun awaitReady(onWaiting: () -> Unit = {}): LocalStatus {
        val until = System.currentTimeMillis() + startupMs
        var s = status()
        while (s == LocalStatus.UNAVAILABLE && current() != null && System.currentTimeMillis() < until) {
            onWaiting()
            kotlinx.coroutines.delay(1_000)
            s = status()
        }
        return s
    }

    /** Starts the phone's AI in the background, so it's ready by the first question. */
    suspend fun warmUp() {
        val m = current() ?: return
        if (awaitReady() == LocalStatus.READY) runCatching { m.warmup() }
    }

    /** Remembers why a request failed, and returns the reason. */
    internal fun failed(e: Throwable): String = (current()?.explain(e) ?: e.message ?: e.javaClass.simpleName).also { problem = it }

    /** The answer to [prompt], or null when the phone's AI isn't there or can't answer (Android allows it in the foreground only). */
    suspend fun ask(prompt: String, maxOutputTokens: Int = 200): String? {
        val m = current() ?: return null
        if (awaitReady() != LocalStatus.READY) return null
        return runCatching { m.generate(prompt, maxOutputTokens).trim() }.onFailure { failed(it) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * A bank message or email the rules couldn't read: the model says whether it's a payment, which way, how much
     * and to whom. Its amount only counts when that number is in the message, so it can't invent one.
     */
    suspend fun readPayment(text: String): SmsParser.Parsed? {
        val amounts = MoneyText.all(text)
        if (amounts.isEmpty()) return null
        val reply = ask(
            "Read this bank or payment message. Reply with JSON only, like " +
                """{"payment": true, "direction": "out", "amount": 250.00, "who": "Swiggy"}. """ +
                "payment is false for OTPs, offers, reminders, balances and failed payments. direction is out when money left " +
                "the account and in when it came in. who is the shop or person, or empty.\n\nMessage:\n" + text.take(1_500),
            maxOutputTokens = 80,
        ) ?: return null
        val json = jsonIn(reply) ?: return null
        if (!json.optBoolean("payment")) return null
        val minor = MoneyText.toMinor(json.opt("amount")?.toString().orEmpty()) ?: return null
        if (minor !in amounts) return null
        return SmsParser.Parsed(minor, json.optString("who").trim().take(40), isIncome = json.optString("direction") == "in")
    }

    /** One of [names] for an entry with [note], or null when none fits or the model isn't there. */
    suspend fun pickCategory(note: String, names: List<String>): String? {
        if (note.isBlank() || names.isEmpty()) return null
        val reply = ask(
            "Which category fits a payment with the note \"${note.take(80)}\"? Choose exactly one of: ${names.joinToString(", ")}. " +
                "Reply with the category name only, or None if none fits.",
            maxOutputTokens = 16,
        ) ?: return null
        return matchName(reply, names)
    }

    /** A sentence or two about a month, from its numbers in [facts]. */
    suspend fun summarise(facts: String): String? = ask(
        "In two short, plain sentences, sum up this month of someone's spending for them. Use only these numbers, " +
            "don't give advice unless spending went over the budget.\n\n$facts",
        maxOutputTokens = 120,
    )

    /** The first JSON object in a model reply, which may wrap it in a code block or words. */
    internal fun jsonIn(reply: String): JSONObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(reply.substring(start, end + 1)) }.getOrNull()
    }

    /** [reply]'s category among [names]: the exact name, or the one it contains, ignoring case. */
    internal fun matchName(reply: String, names: List<String>): String? {
        val r = reply.trim().trim('.', '"', '*', '\'').trim()
        return names.firstOrNull { it.equals(r, ignoreCase = true) }
            ?: names.sortedByDescending { it.length }.firstOrNull { r.contains(it, ignoreCase = true) }
    }
}
