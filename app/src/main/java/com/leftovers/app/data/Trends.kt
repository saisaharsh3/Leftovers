package com.leftovers.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** One short observation for Insights, e.g. "Food is up 30% vs last month". */
data class Trend(val text: String, val good: Boolean?)

/**
 * Up to three observations about [month]'s entries of [type] compared with the month before.
 * [format] turns minor units into display money.
 */
fun spendingTrends(
    all: List<TransactionItem>,
    month: YearMonth,
    type: TxType,
    format: (Long) -> String,
    today: LocalDate = LocalDate.now(),
): List<Trend> {
    val current = all.filter { it.type == type && YearMonth.from(it.date) == month }
    if (current.isEmpty()) return emptyList()
    val previous = all.filter { it.type == type && YearMonth.from(it.date) == month.minusMonths(1) }
    val isExpense = type == TxType.EXPENSE
    val trends = mutableListOf<Trend>()

    // Biggest category swing against last month, ignoring tiny amounts.
    val now = current.groupBy { it.categoryName }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }
    val before = previous.groupBy { it.categoryName }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }
    val floor = (current.sumOf { it.amountMinor } / 20).coerceAtLeast(1)
    now.entries
        .filter { (name, total) -> (before[name] ?: 0) > 0 && maxOf(total, before[name]!!) >= floor }
        .map { (name, total) -> Triple(name, total, ((total - before[name]!!) * 100.0 / before[name]!!).roundToInt()) }
        .filter { abs(it.third) >= 20 }
        .sortedByDescending { abs(it.second - before[it.first]!!) }
        .take(2)
        .forEach { (name, _, pct) ->
            val up = pct > 0
            trends += Trend("$name is ${if (up) "up" else "down"} ${abs(pct)}% vs last month", if (isExpense) !up else up)
        }

    // Weekend vs weekday spending per day, once there is enough to compare.
    if (isExpense && current.size >= 5) {
        val lastDay = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        val days = (1..lastDay).map { month.atDay(it) }
        val weekend = days.filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }.toSet()
        val weekdays = days.size - weekend.size
        if (weekend.isNotEmpty() && weekdays > 0) {
            val perWeekend = current.filter { it.date in weekend }.sumOf { it.amountMinor }.toDouble() / weekend.size
            val perWeekday = current.filter { it.date !in weekend }.sumOf { it.amountMinor }.toDouble() / weekdays
            if (perWeekday > 0 && perWeekend >= perWeekday * 1.5) {
                trends += Trend("You spend %.1f× more per day on weekends".format(perWeekend / perWeekday), null)
            } else if (perWeekend > 0 && perWeekday >= perWeekend * 1.5) {
                trends += Trend("Weekdays cost you %.1f× more per day than weekends".format(perWeekday / perWeekend), null)
            }
        }
    }

    // Where the month is heading, while it's still running.
    if (isExpense && month == YearMonth.from(today) && today.dayOfMonth in 5 until month.lengthOfMonth()) {
        val projected = current.sumOf { it.amountMinor } * month.lengthOfMonth() / today.dayOfMonth
        val last = previous.sumOf { it.amountMinor }
        val vs = if (last > 0) ", ${if (projected > last) "more" else "less"} than last month's ${format(last)}" else ""
        trends += Trend("At this pace you'll spend about ${format(projected)} this month$vs", if (last > 0) projected <= last else null)
    }
    return trends.take(3)
}
