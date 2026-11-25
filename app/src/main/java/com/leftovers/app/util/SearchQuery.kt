package com.leftovers.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A search in Activity read for filters as well as words: "food >500 last month" is food, over 500, in last month.
 * Amounts: ">500", ">=500", "<200", "<=200", "over 500", "above", "under", "below", "more than", "less than".
 * Dates: "today", "yesterday", "this/last week", "this/last month", "this/last year", "last 7 days", a month's name
 * ("march", "mar 2026"). What's left are words, found in the note, category or account as before.
 */
data class SearchQuery(
    val words: List<String>,
    /** Smallest and largest amount, in minor units; "over" and "under" ([minStrict], [maxStrict]) leave them out. */
    val min: Long? = null,
    val max: Long? = null,
    val minStrict: Boolean = false,
    val maxStrict: Boolean = false,
    /** First and last day, both included. */
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** How the dates were said, for showing back ("last month"). */
    val dateLabel: String? = null,
) {
    val hasFilters get() = min != null || max != null || from != null || to != null

    fun allows(amountMinor: Long, date: LocalDate): Boolean =
        (min == null || if (minStrict) amountMinor > min else amountMinor >= min) &&
            (max == null || if (maxStrict) amountMinor < max else amountMinor <= max) &&
            (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))

    /** What was understood, for a line under the search box: "Over ₹500 · last month". Null without filters. */
    fun describe(format: (Long) -> String): String? {
        if (!hasFilters) return null
        val parts = buildList {
            min?.let { add((if (minStrict) "over " else "at least ") + format(it)) }
            max?.let { add((if (maxStrict) "under " else "up to ") + format(it)) }
            dateLabel?.let(::add)
        }
        return parts.joinToString(" · ").replaceFirstChar { it.titlecase() }
    }

    companion object {
        private val bound = Regex("""^(>=|<=|>|<)(\d[\d,]*(?:\.\d{1,2})?)$""")
        private val number = Regex("""^\d[\d,]*(?:\.\d{1,2})?$""")
        private val year = Regex("""^(19|20)\d\d$""")

        private fun minor(text: String): Long? = AmountInput.toMinor(text.replace(",", ""))

        private fun month(word: String): Month? = Month.entries.firstOrNull { m ->
            val full = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            word == full || (word.length == 3 && full.startsWith(word))
        }

        fun parse(text: String, today: LocalDate = LocalDate.now()): SearchQuery {
            val t = text.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val words = ArrayList<String>()
            var min: Long? = null
            var max: Long? = null
            var from: LocalDate? = null
            var to: LocalDate? = null
            var label: String? = null
            fun dates(a: LocalDate, b: LocalDate, said: String) { from = a; to = b; label = said }
            var minStrict = false
            var maxStrict = false
            fun lower(op: String, v: Long) { min = v; minStrict = op == ">" }
            fun upper(op: String, v: Long) { max = v; maxStrict = op == "<" }
            var i = 0
            while (i < t.size) {
                val w = t[i]
                val next = t.getOrNull(i + 1)
                val after = t.getOrNull(i + 2)
                val b = bound.matchEntire(w)
                val bv = b?.let { minor(it.groupValues[2]) }
                when {
                        b != null && bv != null -> {
                            val op = b.groupValues[1]
                            if (op.startsWith(">")) lower(op, bv) else upper(op, bv)
                            i++
                        }
                        w in setOf(">", ">=", "<", "<=") && next != null && number.matches(next) -> {
                            val v = minor(next)!!
                            if (w.startsWith(">")) lower(w, v) else upper(w, v)
                            i += 2
                        }
                        w in setOf("over", "above") && next != null && number.matches(next) -> { lower(">", minor(next)!!); i += 2 }
                        w in setOf("under", "below") && next != null && number.matches(next) -> { upper("<", minor(next)!!); i += 2 }
                        w == "more" && next == "than" && after != null && number.matches(after) -> { lower(">", minor(after)!!); i += 3 }
                        w == "less" && next == "than" && after != null && number.matches(after) -> { upper("<", minor(after)!!); i += 3 }
                        w == "today" -> { dates(today, today, "today"); i++ }
                        w == "yesterday" -> { dates(today.minusDays(1), today.minusDays(1), "yesterday"); i++ }
                        (w == "this" || w == "last") && next == "week" -> {
                            val monday = today.with(DayOfWeek.MONDAY)
                            if (w == "this") dates(monday, today, "this week") else dates(monday.minusWeeks(1), monday.minusDays(1), "last week")
                            i += 2
                        }
                        (w == "this" || w == "last") && next == "month" -> {
                            val m = YearMonth.from(today).let { if (w == "last") it.minusMonths(1) else it }
                            dates(m.atDay(1), if (w == "this") today else m.atEndOfMonth(), "$w month")
                            i += 2
                        }
                        (w == "this" || w == "last") && next == "year" -> {
                            val y = if (w == "last") today.year - 1 else today.year
                            dates(LocalDate.of(y, 1, 1), if (w == "this") today else LocalDate.of(y, 12, 31), "$w year")
                            i += 2
                        }
                        w == "last" && next != null && next.toIntOrNull() != null && after in setOf("days", "day") -> {
                            val n = next.toInt().coerceIn(1, 3650)
                            dates(today.minusDays(n - 1L), today, "last $n days")
                            i += 3
                        }
                        month(w) != null -> {
                            val m = month(w)!!
                            val y = next?.takeIf { year.matches(it) }?.toInt()
                            // Without a year, the latest one that's begun.
                            val ym = if (y != null) YearMonth.of(y, m)
                            else YearMonth.of(today.year, m).let { if (it.isAfter(YearMonth.from(today))) it.minusYears(1) else it }
                            dates(ym.atDay(1), ym.atEndOfMonth(), m.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.year)
                            i += if (y != null) 2 else 1
                        }
                        else -> { words += t[i]; i++ }
                }
            }
            return SearchQuery(words, min, max, minStrict, maxStrict, from, to, label)
        }
    }
}
