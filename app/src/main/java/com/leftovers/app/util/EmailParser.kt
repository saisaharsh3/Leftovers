package com.leftovers.app.util

import java.math.BigDecimal

/**
 * Recognises payment emails (bank and card alerts, UPI confirmations, shop receipts) and pulls out the
 * amount and who was paid. Everything happens in memory; nothing from the email is kept.
 */
object EmailParser {
    /** Words that mean money actually left the account, or an order was paid for. */
    private val paidWords = Regex(
        """\b(debited|spent|paid|payment of|payment successful|purchase|charged|txn|transaction|order (?:total|placed|confirmed)|grand total|amount paid|total paid|receipt|invoice)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditOnly = Regex("""\b(credited|received|refund(?:ed)?|cashback)\b""", RegexOption.IGNORE_CASE)
    private val skip = Regex(
        """\b(otp|one time password|verification code|will be debited|due date|minimum due|payment due|requested|declined|failed|unsuccessful|statement is ready)\b""",
        RegexOption.IGNORE_CASE,
    )
    /** Promotions talk about prices too; skip them unless they also confirm a payment. */
    private val promo = Regex("""(\d+\s?% off|\boffer\b|\bsale\b|\bcoupon\b|\bdeal of)""", RegexOption.IGNORE_CASE)
    private val confirmed = Regex("""\b(debited|charged|paid|payment successful|order confirmed|order placed)\b""", RegexOption.IGNORE_CASE)

    private val amount = Regex(
        """(?:rs\.?|inr|₹|usd|us\$|\$|eur|€|gbp|£)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    /** Amounts next to these words are the one that was paid. */
    private val totalNear = Regex(
        """(?:order total|grand total|total paid|amount paid|total amount|amount|total)\s*[:\-]?\s*(?:rs\.?|inr|₹|usd|us\$|\$|eur|€|gbp|£)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantWords = Regex(
        """\b(?:at|to|towards|vpa|merchant:?)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|via|ref|upi|txn|avl|avail|from|using|for)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )

    data class Parsed(val amountMinor: Long, val merchant: String)

    /** [senderName] is the display name of the From address, e.g. "Swiggy" or "HDFC Bank Alerts". */
    fun parse(subject: String, senderName: String, body: String): Parsed? {
        val text = (subject + "\n" + body).take(20_000)
        if (skip.containsMatchIn(text)) return null
        if (!paidWords.containsMatchIn(text)) return null
        if (creditOnly.containsMatchIn(text) && !Regex("""\bdebited\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        if (promo.containsMatchIn(text) && !confirmed.containsMatchIn(text)) return null

        val value = (totalNear.find(text) ?: amount.find(text))?.groupValues?.get(1)?.replace(",", "") ?: return null
        val minor = runCatching { BigDecimal(value).movePointRight(2).toLong() }.getOrNull() ?: return null
        if (minor <= 0) return null

        val named = merchantWords.find(text)?.groupValues?.get(1)?.trim()
            ?.substringBefore('@')
            ?.replace(Regex("""^(vpa|upi|merchant)\s+""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.length >= 2 && !it.contains("a/c", true) && !it.matches(Regex("""[xX*\d ]+""")) }
        val who = (named ?: cleanSender(senderName)).replaceFirstChar { it.uppercase() }
        return Parsed(minor, who)
    }

    /** "Swiggy Orders" → "Swiggy"; bank alert senders stay as they are. */
    private fun cleanSender(name: String): String =
        name.replace(Regex("""\b(orders?|receipts?|no-?reply|alerts?|notifications?|team|support)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ").trim().trim('-', '|', ':').trim()
            .take(40)

    private val tags = Regex("""<[^>]+>""")
    private val hidden = Regex("""<(style|script|head)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val entities = mapOf("&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&rsquo;" to "'", "&#8377;" to "₹", "&#x20b9;" to "₹", "&rupee;" to "₹")

    /** Plain text from an HTML email body, good enough for finding amounts. */
    fun htmlToText(html: String): String {
        var t = hidden.replace(html, " ")
        t = t.replace(Regex("""<(br|/p|/div|/tr|/li|/h\d)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        t = tags.replace(t, " ")
        entities.forEach { (k, v) -> t = t.replace(k, v, ignoreCase = true) }
        t = Regex("""&#(\d+);""").replace(t) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: " " }
        return t.replace(Regex("""[ \t ]+"""), " ").replace(Regex(""" *\n\s*"""), "\n").trim()
    }
}
