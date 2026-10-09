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
        // Only a real OTP or collect request; bank alerts often say "never share your OTP" or "if you have not requested this".
        """(\bis your (otp|one time password)\b|\b(otp|one time password) (is|for)\b|\bverification code\b|\bhas requested\b|\bcollect request\b|""" +
            """\b(will be debited|due date|minimum due|payment due|declined|failed|unsuccessful|statement is ready|pre-?approved|eligible for|""" +
            """apply now|limit (?:increase|enhancement)|reward points|loan offer|emi offer)\b)""",
        RegexOption.IGNORE_CASE,
    )
    /** Promotions talk about prices too; skip them unless they also confirm a payment. */
    private val promo = Regex("""(\d+\s?% off|\boffer\b|\bsale\b|\bcoupon\b|\bdeal of)""", RegexOption.IGNORE_CASE)
    private val confirmed = Regex("""\b(debited|charged|paid|payment successful|order confirmed|order placed)\b""", RegexOption.IGNORE_CASE)

    /** An amount right after one of these words is the one that was paid. */
    private val totalLabel = Regex(
        """(?:order total|grand total|total paid|amount paid|total amount|amount|total)\s*[:\-]?""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantWords = Regex(
        """\b(?:at|to|towards|vpa|merchant:?)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|via|ref\w*|upi|txn|avl|avail|from|using|for)\b|\s*[(\[]|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )

    /** [merchant] is who was paid, or for [isIncome] who the money came from. */
    data class Parsed(val amountMinor: Long, val merchant: String, val isIncome: Boolean = false)

    /** [senderName] is the display name of the From address, e.g. "Swiggy" or "HDFC Bank Alerts". */
    fun parse(subject: String, senderName: String, body: String, extraWords: Collection<String> = emptyList()): Parsed? {
        val text = (subject + "\n" + body).take(20_000)
        if (skip.containsMatchIn(text)) return null
        // "credited" or "received" without "debited" is money coming in, suggested as income.
        val income = creditOnly.containsMatchIn(text) && !Regex("""\bdebited\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (!income && !paidWords.containsMatchIn(text) && extraWords.none { it.isNotBlank() && text.contains(it.trim(), ignoreCase = true) }) return null
        if (promo.containsMatchIn(text) && !income && !confirmed.containsMatchIn(text)) return null

        // Any currency (see MoneyText); a total-like label wins over the first amount mentioned.
        val minor = MoneyText.after(totalLabel, text) ?: MoneyText.find(text) ?: return null

        if (income) return Parsed(minor, SmsParser.payer(text), isIncome = true)
        val who = SmsParser.payee(text, merchantWords).ifEmpty { cleanSender(senderName).replaceFirstChar { it.uppercase() } }
        return Parsed(minor, who)
    }

    /**
     * A readable name for a sender with no display name, from its domain: "alerts@axisbank.com" → "Axis Bank",
     * "noreply@paytm.com" → "Paytm". The part before @ ("alerts") says nothing about who sent it.
     */
    fun nameFromAddress(address: String): String {
        val labels = address.substringAfter('@').lowercase().split('.').filter { it.isNotBlank() }
        // The name is the label before the suffix: axisbank in axisbank.com, hdfcbank in alerts.hdfcbank.net, sbi in sbi.co.in.
        val suffixes = setOf("com", "net", "org", "in", "co", "bank", "uk", "us", "au", "ae", "sg", "io", "money", "club", "app")
        val name = labels.lastOrNull { it !in suffixes } ?: return address.substringBefore('@')
        val words = name.replace(Regex("""bank$"""), " bank").replace('-', ' ').trim()
        return words.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            // Short names and ones without vowels are initials: SBI, HDFC.
            if (w.length <= 3 || w.none { it in "aeiou" }) w.uppercase() else w.replaceFirstChar { it.uppercase() }
        }
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
