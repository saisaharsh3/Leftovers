package com.leftovers.app.ai

import com.leftovers.app.data.BalancePoint
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How a chart in the chat is drawn. */
enum class ChartKind { BARS, LINE, DONUT, RANKED, PAIR }

/** One bar, point or slice: what it's called, its amount (and a second one for in-and-out), and a colour if it has its own. */
data class ChartPoint(val label: String, val value: Long, val second: Long = 0, val color: Long? = null)

/**
 * A chart the assistant asked for, worked out by the app from the user's own entries: the AI only picks what to
 * show, so the numbers are always real. [total] is shown with it when adding up makes sense.
 */
data class ChartData(
    val title: String,
    val subtitle: String,
    val kind: ChartKind,
    val points: List<ChartPoint>,
    val total: Long? = null,
    val firstName: String = "",
    val secondName: String = "",
)

/** What a chart measures. */
enum class Measure { SPENDING, INCOME, IN_AND_OUT, BALANCE }

/** How its amounts are grouped. */
enum class Grouping { DAY, WEEK, MONTH, CATEGORY, ACCOUNT }

object AssistantCharts {
    /** Slices or rows shown before the rest are put together as "Everything else". */
    const val MAX_PARTS = 8
    /** Days before a daily chart groups by week instead, and weeks before it goes by month. */
    const val MAX_DAYS = 62
    const val MAX_WEEKS = 26

    /**
     * Works the chart out. [entries] are already narrowed to the category or account asked for; [accountNames]
     * names the accounts for [Grouping.ACCOUNT]. [wanted] is the kind the AI asked for, if any; when it doesn't fit
     * the grouping (a pie of days, say) the fitting one is used.
     */
    fun build(
        measure: Measure,
        grouping: Grouping,
        from: LocalDate,
        to: LocalDate,
        entries: List<TransactionItem>,
        accountNames: Map<Long, String> = emptyMap(),
        wanted: ChartKind? = null,
        title: String? = null,
        subtitle: String,
    ): ChartData {
        require(measure != Measure.BALANCE) { "Use balance() for a balance" }
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val byTime = grouping == Grouping.DAY || grouping == Grouping.WEEK || grouping == Grouping.MONTH
        if (measure == Measure.IN_AND_OUT) {
            require(byTime) { "Money in and out goes by day, week or month, not by ${grouping.name.lowercase()}" }
            val g = fit(grouping, from, to)
            val points = buckets(g, from, to).map { (label, range) ->
                val part = inRange.filter { it.date in range }
                ChartPoint(label, part.sum(TxType.INCOME), part.sum(TxType.EXPENSE))
            }
            return ChartData(title ?: "Money in and out", subtitle, ChartKind.PAIR, points, firstName = "In", secondName = "Out")
        }
        val type = if (measure == Measure.INCOME) TxType.INCOME else TxType.EXPENSE
        val ofType = inRange.filter { it.type == type }
        val name = if (type == TxType.INCOME) "Income" else "Spending"
        if (byTime) {
            val g = fit(grouping, from, to)
            val points = buckets(g, from, to).map { (label, range) -> ChartPoint(label, ofType.filter { it.date in range }.sumOf { it.amountMinor }) }
            val kind = if (wanted == ChartKind.LINE) ChartKind.LINE else ChartKind.BARS
            val by = when (g) { Grouping.DAY -> "day"; Grouping.WEEK -> "week"; else -> "month" }
            return ChartData(title ?: "$name by $by", subtitle, kind, points, total = ofType.sumOf { it.amountMinor })
        }
        val groups = if (grouping == Grouping.CATEGORY) {
            ofType.groupBy { it.categoryId }.map { (_, list) -> ChartPoint(list.first().categoryName, list.sumOf { it.amountMinor }, color = list.first().categoryColor) }
        } else {
            ofType.groupBy { it.accountId }.map { (id, list) -> ChartPoint(id?.let { accountNames[it] } ?: "No account", list.sumOf { it.amountMinor }) }
        }.filter { it.value > 0 }.sortedByDescending { it.value }
        val parts = if (groups.size > MAX_PARTS) groups.take(MAX_PARTS - 1) + ChartPoint("Everything else", groups.drop(MAX_PARTS - 1).sumOf { it.value }) else groups
        // A ring reads well for a few parts; more than that, ranked rows are clearer.
        val kind = when {
            wanted == ChartKind.RANKED || wanted == ChartKind.BARS -> ChartKind.RANKED
            wanted == ChartKind.DONUT && parts.size <= MAX_PARTS -> ChartKind.DONUT
            parts.size <= 5 -> ChartKind.DONUT
            else -> ChartKind.RANKED
        }
        val by = if (grouping == Grouping.CATEGORY) "category" else "account"
        return ChartData(title ?: "$name by $by", subtitle, kind, parts, total = ofType.sumOf { it.amountMinor })
    }

    /** A balance over time, from points the app worked out (see balanceHistory), as a line. */
    fun balance(points: List<BalancePoint>, title: String?, subtitle: String): ChartData {
        val label = DateTimeFormatter.ofPattern("d MMM")
        return ChartData(title ?: "Balance", subtitle, ChartKind.LINE, points.map { ChartPoint(it.day.format(label), it.balanceMinor) })
    }

    /** A grouping that keeps the bars countable: days become weeks, weeks become months. */
    internal fun fit(grouping: Grouping, from: LocalDate, to: LocalDate): Grouping {
        val days = ChronoUnit.DAYS.between(from, to) + 1
        return when {
            grouping == Grouping.DAY && days > MAX_DAYS -> fit(Grouping.WEEK, from, to)
            grouping == Grouping.WEEK && days / 7 > MAX_WEEKS -> Grouping.MONTH
            else -> grouping
        }
    }

    /** The day, week (from Monday) or month buckets from [from] to [to], each with its label and days. */
    internal fun buckets(grouping: Grouping, from: LocalDate, to: LocalDate): List<Pair<String, ClosedRange<LocalDate>>> {
        val out = ArrayList<Pair<String, ClosedRange<LocalDate>>>()
        val dayLabel = DateTimeFormatter.ofPattern("d MMM")
        val monthLabel = DateTimeFormatter.ofPattern(if (from.year == to.year) "MMM" else "MMM yy")
        when (grouping) {
            Grouping.DAY -> {
                var d = from
                while (!d.isAfter(to)) { out += d.format(dayLabel) to (d..d); d = d.plusDays(1) }
            }
            Grouping.WEEK -> {
                var d = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                while (!d.isAfter(to)) {
                    val end = d.plusDays(6)
                    out += maxOf(d, from).format(dayLabel) to (maxOf(d, from)..minOf(end, to))
                    d = d.plusWeeks(1)
                }
            }
            else -> {
                var m = YearMonth.from(from)
                while (!m.isAfter(YearMonth.from(to))) {
                    out += m.format(monthLabel) to (maxOf(m.atDay(1), from)..minOf(m.atEndOfMonth(), to))
                    m = m.plusMonths(1)
                }
            }
        }
        return out
    }

    private fun List<TransactionItem>.sum(type: TxType) = filter { it.type == type }.sumOf { it.amountMinor }
}
