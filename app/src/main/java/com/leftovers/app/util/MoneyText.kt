package com.leftovers.app.util

import java.math.BigDecimal

/**
 * Finds a money amount in bank SMS and email text, in any common currency, written either way round:
 * "Rs.250", "₹ 1,250.00", "$12.50", "USD 12.50", "12.50 EUR", "AED 45", "RM 30", "Rp 50.000".
 */
object MoneyText {
    /** Symbols and codes, longest first so "US$" wins over "$" and "R$" over "R". */
    private val marks = listOf(
        "rs.", "rs", "inr", "₹", "us\$", "usd", "\$", "eur", "€", "gbp", "£", "aed", "sar", "qar", "kwd", "bhd", "omr",
        "sgd", "s\$", "aud", "a\$", "cad", "c\$", "nzd", "hkd", "jpy", "cny", "rmb", "¥", "krw", "₩", "myr", "rm",
        "php", "₱", "thb", "฿", "idr", "rp", "vnd", "₫", "bdt", "৳", "pkr", "lkr", "npr", "₨", "kes", "ksh", "ngn",
        "₦", "zar", "brl", "r\$", "mxn", "chf", "₺", "rub", "₽", "egp", "ghs", "₵", "uah", "₴", "pln", "zł",
        "czk", "kč", "sek", "nok", "dkk", "kr",
    ).sortedByDescending { it.length }

    private val markPattern = marks.joinToString("|") { Regex.escape(it) }
    private const val NUMBER = """([0-9][0-9,. ' ]*[0-9]|[0-9])"""

    /** Currency before the number ("USD 12.50"), or after it ("12.50 USD"). Marks made of letters need word edges. */
    private val before = Regex("""(?<![A-Za-z])(?:$markPattern)\s?$NUMBER""", RegexOption.IGNORE_CASE)
    private val after = Regex("""$NUMBER\s?(?:$markPattern)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    /** The first amount in [text], in minor units (e.g. paise or cents), or null. */
    fun find(text: String): Long? = all(text).firstOrNull()

    /** Every amount in [text], in the order they appear. */
    fun all(text: String): List<Long> =
        (before.findAll(text).map { it.range.first to it.groupValues[1] } + after.findAll(text).map { it.range.first to it.groupValues[1] })
            .sortedBy { it.first }
            .mapNotNull { (_, n) -> toMinor(n) }
            .toList()

    /** Amount right after a total-like word, e.g. "Order total: $12.50", or null. */
    fun after(label: Regex, text: String): Long? {
        val m = label.find(text) ?: return null
        return find(text.substring(m.range.last + 1).take(40))
    }

    /** "1,250.50" → 125050; "1.250,50" (comma decimals) → 125050; "50.000" (dot thousands) → 5000000. */
    fun toMinor(raw: String): Long? {
        val n = raw.replace(Regex("""[ ' ]"""), "")
        val lastDot = n.lastIndexOf('.')
        val lastComma = n.lastIndexOf(',')
        val decimalSep = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 -> if (n.length - lastDot - 1 in 1..2) '.' else null
            lastComma >= 0 -> if (n.length - lastComma - 1 in 1..2) ',' else null
            else -> null
        }
        val plain = if (decimalSep == null) {
            n.replace(",", "").replace(".", "")
        } else {
            val other = if (decimalSep == '.') "," else "."
            n.replace(other, "").replace(decimalSep, '.')
        }
        return runCatching { BigDecimal(plain).movePointRight(2).toLong() }.getOrNull()?.takeIf { it > 0 }
    }
}
