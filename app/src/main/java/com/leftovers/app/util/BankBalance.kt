package com.leftovers.app.util

/**
 * The balance a bank message reports ("Avl Bal Rs.12,000.50", "Available Balance: INR 1,23,456.78", "A/c bal is
 * Rs 2,000"), to show next to the app's own figure for that account (#59). Only the number is kept, never the
 * message. A card's credit limit or amount outstanding isn't a balance, so those are left alone.
 */
object BankBalance {
    private val pattern = Regex(
        """(?<![a-z])(?:(?:avl|avbl|avail|available|clr|clear|cleared|total|net|a/c|ac|account|acct)\.?\s*)?bal(?:ance)?\b\.?\s*(?:is|of|now|:|-|=)?\s*(?:(rs|inr|₹)\.?\s*)?(-)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(cr|dr)?\b""",
        RegexOption.IGNORE_CASE,
    )

    // Not the money in the account: what a card can still spend, or what's owed on it.
    private val notBalance = Regex("""\b(limit|lmt|outstanding|due|min(imum)?\s+amount|statement)\b""", RegexOption.IGNORE_CASE)

    /** The balance [text] reports, in minor units (negative when overdrawn), or null when it gives none. */
    fun read(text: String): Long? {
        val m = pattern.findAll(text).firstOrNull { m ->
            val before = text.substring((m.range.first - 20).coerceAtLeast(0), m.range.first)
            // "Rs" or "INR" given, or a decimal amount: not a bare number like a reference.
            (m.groupValues[1].isNotEmpty() || '.' in m.groupValues[3] || ',' in m.groupValues[3]) && !notBalance.containsMatchIn(before + m.value)
        } ?: return null
        val minor = MoneyText.toMinor(m.groupValues[3]) ?: return null
        val negative = m.groupValues[2] == "-" || m.groupValues[4].equals("dr", ignoreCase = true)
        return if (negative) -minor else minor
    }
}
