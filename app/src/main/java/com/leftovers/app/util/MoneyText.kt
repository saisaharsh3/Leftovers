package com.leftovers.app.util

import java.math.BigDecimal

/**
 * Finds a money amount in bank SMS and email text, in any common currency, written either way round:
 * "Rs.250", "₹ 1,250.00", "$12.50", "USD 12.50", "12.50 EUR", "AED 45", "RM 30", "Rp 50.000".
 */
object MoneyText {
    /** Symbols and words for money that aren't an ISO code, as banks write them. */
    private val symbols = listOf(
        "rs.", "rs", "₹", "tk.", "tk", "us\$", "\$", "€", "euro", "euros", "£", "s\$", "a\$", "c\$", "rmb", "¥", "₩", "rm",
        "₱", "฿", "rp", "₫", "৳", "₨", "ksh", "₦", "r\$", "₺", "tl", "₽", "₵", "₴", "zł", "kč", "kr", "br", "₸", "₾", "₼", "₪",
        "lei", "лв", "din", "nt\$", "hk\$", "rub.", "руб", "грн", "dh", "dhs",
    )

    /**
     * Every ISO currency code ("BYN", "KZT", "GEL"...), so a bank in any country is understood. A few codes are
     * also everyday words ("ALL", "TOP", "TRY"); those only count by their symbol, to avoid "try 3 times" as money.
     */
    private val wordCodes = setOf("all", "top", "try", "cup", "bob", "mop", "pen", "sos", "gel", "mad", "bam", "lak", "now", "std", "mro")
    private val codes: List<String> = runCatching {
        java.util.Currency.getAvailableCurrencies().map { it.currencyCode.lowercase() }.filter { it.length == 3 && it.all(Char::isLetter) && it !in wordCodes }
    }.getOrDefault(emptyList()) + listOf("inr", "usd", "eur", "gbp", "aed", "byn")

    /** Symbols and codes, longest first so "US$" wins over "$" and "R$" over "R". */
    private val marks = (symbols + codes).distinct().sortedByDescending { it.length }

    private val markPattern = marks.joinToString("|") { Regex.escape(it) }
    private const val NUMBER = """([0-9][0-9,. ' ]*[0-9]|[0-9])"""

    /** Currency before the number ("USD 12.50"), or after it ("12.50 USD"). Marks made of letters need word edges. */
    private val before = Regex("""(?<![A-Za-z])(?:$markPattern)\s?$NUMBER""", RegexOption.IGNORE_CASE)
    private val after = Regex("""$NUMBER\s?(?:$markPattern)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    /** The first amount in [text], in minor units (e.g. paise or cents), or null. */
    fun find(text: String): Long? = all(text).firstOrNull()

    /** "Avl Bal Rs.12,000", "balance of Rs 10,000", "Available limit INR 50,000": an amount that isn't what was paid. */
    private val balanceLabel = Regex("""\b(bal(ance)?|limit|avl|avail(able)?|outstanding)\b[^0-9]{0,16}$""", RegexOption.IGNORE_CASE)

    /**
     * The amount paid in a bank message: the first one not labelled as a balance or limit, so "Avl Bal Rs.12,000
     * after debit of Rs.500" is 500. When every amount is labelled so, the first one.
     */
    fun paid(text: String): Long? {
        val found = (before.findAll(text).map { it.range.first to it.groupValues[1] } + after.findAll(text).map { it.range.first to it.groupValues[1] })
            .sortedBy { it.first }.toList()
        val first = found.firstOrNull { (at, _) -> !balanceLabel.containsMatchIn(text.substring((at - 24).coerceAtLeast(0), at)) } ?: found.firstOrNull()
        return first?.let { toMinor(it.second) }
    }

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
