package com.leftovers.desktop.data

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.hashtags
import com.leftovers.app.data.noteText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

/** What a dashboard widget shows: a ready-made one, or [CUSTOM], built from a measure, a grouping and a chart. */
enum class WidgetKind(val title: String, val line: String, val span: Int) {
    CUSTOM("Your own", "Pick what to measure, how to group it and how to show it", 1),
    CASH_FLOW("Cash flow", "Money in and out, month by month", 2),
    NET_WORTH("Net worth", "All your accounts together, over time", 2),
    THIS_VS_LAST("This month against last", "Spending so far, day by day", 1),
    BY_CATEGORY("Spending by category", "Where the money went", 1),
    TOP_PLACES("Top places", "Where you spend most", 1),
    BIGGEST("Biggest expenses", "The largest single payments", 1),
    SAVINGS_RATE("Savings rate", "What's kept of each month's income", 1),
    WEEKDAYS("By day of the week", "Which days cost the most", 1),
    HEATMAP("A year of spending", "Every day, lighter to heavier", 2),
    BUDGET("Budget", "This budget and the category limits", 1),
    UPCOMING("Coming up", "Bills and income in the next 30 days", 1),
    GOALS("Savings goals", "How far along each one is", 1),
    OWED("Money owed", "Who owes whom", 1),
}

enum class Measure(val label: String) { SPENT("Spent"), INCOME("Income"), NET("Kept (income − spent)"), COUNT("Number of entries") }
enum class GroupBy(val label: String) { NONE("Nothing: one number"), MONTH("Month"), WEEK("Week"), DAY("Day"), CATEGORY("Category"), PLACE("Place"), ACCOUNT("Account"), WEEKDAY("Day of the week") }
enum class ChartStyle(val label: String) { NUMBER("Number"), BARS("Bars"), LINE("Line"), DONUT("Donut"), LIST("List") }
enum class WidgetHeight(val label: String, val dp: Int) { SHORT("Short", 200), MEDIUM("Medium", 300), TALL("Tall", 420) }

/** How far back a dashboard, or one widget, looks. */
enum class DashPeriod(val label: String, val months: Int) {
    MONTH("This month", 1), QUARTER("3 months", 3), HALF("6 months", 6), YEAR("12 months", 12), ALL("All time", 0);

    /** The first day it covers; all time starts at the first entry. */
    fun from(entries: List<TransactionItem>, today: LocalDate = LocalDate.now()): LocalDate =
        if (this == ALL) entries.minOfOrNull { it.date }?.withDayOfMonth(1) ?: today.withDayOfMonth(1)
        else YearMonth.from(today).minusMonths((months - 1).toLong()).atDay(1)
}

/** One widget, as the user set it up. Everything has a sensible default, so a new one shows something at once. */
data class Widget(
    val kind: WidgetKind,
    /** A unique id, so widgets of the same kind can be told apart when they're moved. */
    val id: Long = System.nanoTime(),
    val title: String? = null,
    /** Columns it takes, of the dashboard's. */
    val span: Int = kind.span,
    val height: WidgetHeight = WidgetHeight.MEDIUM,
    /** Its own period; null follows the dashboard's. */
    val period: DashPeriod? = null,
    val accountId: Long? = null,
    val categoryIds: Set<Long> = emptySet(),
    val tag: String? = null,
    val color: Long? = null,
    /** For [WidgetKind.CUSTOM], and the chart style of the ready-made charts that can be bars or a line. */
    val measure: Measure = Measure.SPENT,
    val groupBy: GroupBy = GroupBy.MONTH,
    val chart: ChartStyle = ChartStyle.BARS,
) {
    val name: String get() = title?.takeIf { it.isNotBlank() } ?: if (kind == WidgetKind.CUSTOM) customName() else kind.title
    private fun customName() = measure.label.substringBefore(" (") + if (groupBy == GroupBy.NONE) "" else " by ${groupBy.label.lowercase()}"
    val narrows: Boolean get() = accountId != null || categoryIds.isNotEmpty() || tag != null
}

data class Dashboard(val name: String, val widgets: List<Widget>, val period: DashPeriod = DashPeriod.HALF, val columns: Int = 3) {
    companion object {
        val starter: Dashboard get() = Dashboard(
            "Overview",
            listOf(
                Widget(WidgetKind.CASH_FLOW, id = 1), Widget(WidgetKind.THIS_VS_LAST, id = 2, chart = ChartStyle.LINE),
                Widget(WidgetKind.BY_CATEGORY, id = 3), Widget(WidgetKind.TOP_PLACES, id = 4), Widget(WidgetKind.SAVINGS_RATE, id = 5),
                Widget(WidgetKind.NET_WORTH, id = 6, chart = ChartStyle.LINE), Widget(WidgetKind.WEEKDAYS, id = 7),
                Widget(WidgetKind.HEATMAP, id = 8, span = 3, height = WidgetHeight.SHORT),
            ),
        )
    }
}

/** A labelled value, for charts and lists. */
data class Point(val label: String, val value: Long, val icon: String? = null, val color: Long? = null, val count: Int = 0)

/** The numbers behind a widget: its period and filters applied to the entries. */
class DashData(val view: View, val widget: Widget, boardPeriod: DashPeriod, val today: LocalDate = LocalDate.now()) {
    val period: DashPeriod = widget.period ?: boardPeriod
    val from: LocalDate = period.from(view.entries, today)

    /** Every entry the widget's filters let through, any date. */
    val matching: List<TransactionItem> = view.entries.filter { e ->
        (widget.accountId == null || e.accountId == widget.accountId) &&
            (widget.categoryIds.isEmpty() || e.categoryId in widget.categoryIds) &&
            (widget.tag == null || widget.tag in hashtags(e.note))
    }
    /** And in the period, up to today. */
    val items: List<TransactionItem> = matching.filter { it.date >= from && it.date <= today }

    /** The months a chart over time shows: the period's, or the last six when it's shorter than three. */
    val months: List<YearMonth> = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(today) }.toList()
        .let { if (it.size < 3) (5 downTo 0).map { n -> YearMonth.from(today).minusMonths(n.toLong()) } else it }

    private fun spent(list: List<TransactionItem>) = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    private fun earned(list: List<TransactionItem>) = list.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }

    data class MonthFlow(val month: YearMonth, val income: Long, val spent: Long) { val kept get() = income - spent }

    val cashFlow: List<MonthFlow> by lazy {
        val byMonth = matching.groupBy { YearMonth.from(it.date) }
        months.map { m -> byMonth[m].orEmpty().let { MonthFlow(m, earned(it), spent(it)) } }
    }

    /**
     * All accounts' balance (or the widget's one account's) at the end of each month, today for this one: the
     * balance now, less what changed after that day. Accounts left out of the total stay out.
     */
    val netWorth: List<Pair<YearMonth, Long>> by lazy {
        val counted = view.accounts.filter { a -> if (widget.accountId != null) a.id == widget.accountId else !a.outOfTotal }.map { it.id }.toSet()
        val now = view.accounts.filter { it.id in counted }.sumOf { it.balanceMinor }
        fun changeAfter(day: LocalDate): Long {
            val d = day.toEpochDay()
            val entries = view.book.transactions.filter { it.epochDay > d && it.accountId in counted }.sumOf { if (it.type == TxType.INCOME) it.amountMinor else -it.amountMinor }
            val transfers = view.book.transfers.filter { it.epochDay > d }.sumOf { t ->
                (if (t.toAccountId in counted) t.amountMinor else 0L) - (if (t.fromAccountId in counted) t.amountMinor else 0L)
            }
            val owed = view.book.debts.filter { it.epochDay > d && it.accountId in counted }.sumOf { -it.amountMinor }
            return entries + transfers + owed
        }
        months.map { m -> m to now - changeAfter(minOf(m.atEndOfMonth(), today)) }
    }

    val byCategory: List<Point> by lazy { group(GroupBy.CATEGORY, Measure.SPENT) }
    val topPlaces: List<Point> by lazy { group(GroupBy.PLACE, Measure.SPENT) }
    val biggest: List<TransactionItem> by lazy { items.filter { it.type == TxType.EXPENSE }.sortedByDescending { it.amountMinor }.take(12) }

    /** What share of each month's income was kept, in percent; null for a month with no income. */
    val savingsRate: List<Pair<YearMonth, Int?>> by lazy {
        cashFlow.map { f -> f.month to if (f.income > 0) ((f.kept * 100) / f.income).toInt() else null }
    }
    val overallRate: Int? by lazy {
        val inc = cashFlow.sumOf { it.income }
        if (inc > 0) ((cashFlow.sumOf { it.kept } * 100) / inc).toInt() else null
    }

    /** The average spent on each day of the week over the days in the period, the week starting as it does here. */
    val weekdays: List<Point> by lazy {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.toList()
        val spentBy = items.filter { it.type == TxType.EXPENSE }.groupBy { it.date.dayOfWeek }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        weekOrder().map { d -> Point(d.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), (spentBy[d] ?: 0L) / days.count { it.dayOfWeek == d }.coerceAtLeast(1)) }
    }

    /** What was spent each day of the last year, oldest first, ending today. */
    val year: List<Pair<LocalDate, Long>> by lazy {
        val start = today.minusDays(364)
        val spentBy = matching.filter { it.type == TxType.EXPENSE && it.date >= start && it.date <= today }.groupBy { it.epochDay }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        (0L..364L).map { start.plusDays(it).let { d -> d to (spentBy[d.toEpochDay()] ?: 0L) } }
    }

    /** Spending added up day by day: this month so far, and all of last month. */
    val thisVsLast: Pair<List<Long>, List<Long>> by lazy {
        fun running(m: YearMonth, upTo: Int): List<Long> {
            val byDay = matching.filter { it.type == TxType.EXPENSE && YearMonth.from(it.date) == m }.groupBy { it.date.dayOfMonth }
            var total = 0L
            return (1..upTo).map { d -> total += byDay[d].orEmpty().sumOf { it.amountMinor }; total }
        }
        val now = YearMonth.from(today)
        running(now, today.dayOfMonth) to running(now.minusMonths(1), now.minusMonths(1).lengthOfMonth())
    }

    private fun value(list: List<TransactionItem>, m: Measure): Long = when (m) {
        Measure.SPENT -> spent(list)
        Measure.INCOME -> earned(list)
        Measure.NET -> earned(list) - spent(list)
        Measure.COUNT -> list.count { true }.toLong()
    }

    private fun relevant(m: Measure) = when (m) {
        Measure.SPENT -> items.filter { it.type == TxType.EXPENSE }
        Measure.INCOME -> items.filter { it.type == TxType.INCOME }
        else -> items
    }

    private fun weekOrder(): List<DayOfWeek> {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return (0L until 7L).map { first.plus(it) }
    }

    /** The one number of a [GroupBy.NONE] widget. */
    fun total(m: Measure = widget.measure): Long = value(relevant(m), m)

    /** A custom widget's values: over time (in order) or by something (largest first). */
    fun group(by: GroupBy = widget.groupBy, m: Measure = widget.measure): List<Point> {
        val list = relevant(m)
        return when (by) {
            GroupBy.NONE -> listOf(Point(widget.name, value(list, m), count = list.size))
            GroupBy.MONTH -> {
                val byMonth = list.groupBy { YearMonth.from(it.date) }
                months.map { mo -> Point(mo.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + if (mo.year != today.year) " ${mo.year % 100}" else "", value(byMonth[mo].orEmpty(), m)) }
            }
            GroupBy.WEEK -> {
                val wf = WeekFields.of(Locale.getDefault())
                val start = from.with(wf.dayOfWeek(), 1)
                val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { it <= today }.toList().takeLast(26)
                val byWeek = list.groupBy { it.date.with(wf.dayOfWeek(), 1) }
                weeks.map { w -> Point(w.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")), value(byWeek[w].orEmpty(), m)) }
            }
            GroupBy.DAY -> {
                val days = generateSequence(maxOf(from, today.minusDays(89))) { it.plusDays(1) }.takeWhile { it <= today }.toList()
                val byDay = list.groupBy { it.epochDay }
                days.map { d -> Point(d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")), value(byDay[d.toEpochDay()].orEmpty(), m)) }
            }
            GroupBy.CATEGORY -> list.groupBy { it.categoryId }.map { (_, l) -> Point(l.first().categoryName, value(l, m), l.first().categoryEmoji, l.first().categoryColor, l.size) }.sortedByDescending { it.value }
            GroupBy.PLACE -> list.filter { noteText(it.note).isNotBlank() }.groupBy { noteText(it.note).trim().lowercase() }
                .map { (_, l) -> Point(noteText(l.maxBy { it.createdAt }.note).trim(), value(l, m), l.first().categoryEmoji, l.first().categoryColor, l.size) }.sortedByDescending { it.value }
            GroupBy.ACCOUNT -> list.groupBy { it.accountId }.map { (id, l) ->
                val a = view.account(id)
                Point(a?.name ?: "No account", value(l, m), a?.icon, a?.color, l.size)
            }.sortedByDescending { it.value }
            GroupBy.WEEKDAY -> {
                val by = list.groupBy { it.date.dayOfWeek }
                weekOrder().map { d -> Point(d.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), value(by[d].orEmpty(), m)) }
            }
        }
    }

    /** Whether [by] is over time, where order matters, rather than a ranking. */
    fun overTime(by: GroupBy = widget.groupBy) = by in setOf(GroupBy.MONTH, GroupBy.WEEK, GroupBy.DAY, GroupBy.WEEKDAY)
}
