package com.leftovers.app.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Where the total balance is heading by the end of [today]'s month: day-to-day spending carries on at
 * this month's pace, and bills and income still due this month land as scheduled.
 * Null in the first two days of a month, when there's too little to go on.
 */
fun forecastMonthEnd(balanceMinor: Long, items: List<TransactionItem>, recurring: List<RecurringItem>, today: LocalDate = LocalDate.now()): Long? {
    if (today.dayOfMonth < 3) return null
    val month = YearMonth.from(today)
    val subs = recurring.filter { it.active }
    // Bills a subscription logged are counted on their own schedule, not as a daily habit.
    fun isBill(t: TransactionItem) = subs.any {
        it.type == t.type && it.categoryId == t.categoryId && it.amountMinor == t.amountMinor && it.name.equals(t.note.trim(), ignoreCase = true)
    }
    val spent = items
        .filter { it.type == TxType.EXPENSE && YearMonth.from(it.date) == month && it.date <= today && !isBill(it) }
        .sumOf { it.amountMinor }
    val daily = spent / today.dayOfMonth
    val daysLeft = month.lengthOfMonth() - today.dayOfMonth
    fun pending(type: TxType) = subs.filter { it.type == type && it.toRecurring().isPendingIn(month) }.sumOf { it.amountMinor }
    return balanceMinor - daily * daysLeft - pending(TxType.EXPENSE) + pending(TxType.INCOME)
}

/** A repeating expense that looks like a subscription the user hasn't set up yet. */
data class SubscriptionSuggestion(val key: String, val name: String, val amountMinor: Long, val categoryId: Long, val dayOfMonth: Int)

/**
 * Expenses with the same note logged once a month for the last three months, for about the same
 * amount on about the same day, that aren't already a subscription and weren't dismissed.
 */
fun findLikelySubscriptions(
    items: List<TransactionItem>,
    recurring: List<RecurringItem>,
    dismissed: Set<String>,
    today: LocalDate = LocalDate.now(),
): List<SubscriptionSuggestion> {
    val known = recurring.map { it.name.trim().lowercase() }.toSet()
    val thisMonth = YearMonth.from(today)
    return items
        .filter { it.type == TxType.EXPENSE && it.note.isNotBlank() }
        .groupBy { it.note.trim().lowercase() }
        .mapNotNull { (name, list) ->
            val key = "sub:$name"
            if (name in known || key in dismissed) return@mapNotNull null
            val latest = list.maxBy { it.epochDay }
            val lastMonth = YearMonth.from(latest.date)
            // Still going: seen this month or last.
            if (lastMonth < thisMonth.minusMonths(1)) return@mapNotNull null
            val byMonth = list.groupBy { YearMonth.from(it.date) }
            // Exactly once in each of the last three months; daily coffees aren't subscriptions.
            val recent = (0L..2L).map { byMonth[lastMonth.minusMonths(it)].orEmpty() }
            if (recent.any { it.size != 1 }) return@mapNotNull null
            val entries = recent.map { it.single() }
            if (entries.any { abs(it.amountMinor - latest.amountMinor) > latest.amountMinor / 10 }) return@mapNotNull null
            val days = entries.map { it.date.dayOfMonth }
            if (days.max() - days.min() > 5) return@mapNotNull null
            SubscriptionSuggestion(key, latest.note.trim(), latest.amountMinor, latest.categoryId, latest.date.dayOfMonth)
        }
        .sortedByDescending { it.amountMinor }
}

private val tagPattern = Regex("""#([\p{L}\p{N}_-]+)""")

/** Hashtags in a note, lower-cased, without the #. */
fun hashtags(note: String): List<String> = tagPattern.findAll(note).map { it.groupValues[1].lowercase() }.distinct().toList()

/** Every tag used in [items], most used first. */
fun List<TransactionItem>.allTags(): List<String> =
    flatMap { hashtags(it.note) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
