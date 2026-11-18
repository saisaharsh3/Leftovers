package com.leftovers.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How much time Insights shows. */
enum class PeriodKind(val title: String) {
    MONTH("Month"),
    THREE_MONTHS("3 months"),
    SIX_MONTHS("6 months"),
    YEAR("Year"),
    ALL("All time"),
    DATES("Pick dates"),
}

/**
 * The stretch of time Insights shows: a month, the months up to [anchor], a calendar year, everything, or picked
 * dates. The arrows move it by its own length; it never goes past today.
 */
data class InsightsPeriod(
    val kind: PeriodKind = PeriodKind.MONTH,
    val anchor: YearMonth = YearMonth.now(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    /** The first day; for [PeriodKind.ALL], the day of the oldest entry ([oldest]). */
    fun start(oldest: LocalDate? = null): LocalDate = when (kind) {
        PeriodKind.MONTH -> anchor.atDay(1)
        PeriodKind.THREE_MONTHS -> anchor.minusMonths(2).atDay(1)
        PeriodKind.SIX_MONTHS -> anchor.minusMonths(5).atDay(1)
        PeriodKind.YEAR -> LocalDate.of(anchor.year, 1, 1)
        PeriodKind.ALL -> oldest ?: LocalDate.now().withDayOfMonth(1)
        PeriodKind.DATES -> from ?: LocalDate.now()
    }

    fun end(): LocalDate = when (kind) {
        PeriodKind.MONTH, PeriodKind.THREE_MONTHS, PeriodKind.SIX_MONTHS -> anchor.atEndOfMonth()
        PeriodKind.YEAR -> LocalDate.of(anchor.year, 12, 31)
        PeriodKind.ALL -> LocalDate.now()
        PeriodKind.DATES -> to ?: LocalDate.now()
    }

    /** The days counted so far: up to today when the period hasn't ended. */
    fun daysSoFar(today: LocalDate = LocalDate.now(), oldest: LocalDate? = null): Long {
        val last = minOf(end(), today)
        return (ChronoUnit.DAYS.between(start(oldest), last) + 1).coerceAtLeast(1)
    }

    /** Whether the arrows can move it; all time can't. */
    val steps: Boolean get() = kind != PeriodKind.ALL

    /** Whether there's a later one that has started. */
    fun hasNext(today: LocalDate = LocalDate.now()): Boolean = steps && shift(1).start() <= today

    /** The same length of time, [by] lengths later (or earlier, when negative). */
    fun shift(by: Int): InsightsPeriod = when (kind) {
        PeriodKind.MONTH -> copy(anchor = anchor.plusMonths(by.toLong()))
        PeriodKind.THREE_MONTHS -> copy(anchor = anchor.plusMonths(3L * by))
        PeriodKind.SIX_MONTHS -> copy(anchor = anchor.plusMonths(6L * by))
        PeriodKind.YEAR -> copy(anchor = anchor.plusYears(by.toLong()))
        PeriodKind.ALL -> this
        PeriodKind.DATES -> {
            val length = ChronoUnit.DAYS.between(start(), end()) + 1
            copy(from = start().plusDays(length * by), to = end().plusDays(length * by))
        }
    }

    /** The one just before, to compare with; none for all time. */
    fun previous(): InsightsPeriod? = if (steps) shift(-1) else null

    fun contains(day: LocalDate, oldest: LocalDate? = null) = day >= start(oldest) && day <= end()

    /** What the period is called: "November 2026", "Sep – Nov 2026", "2026", "All time", "3 – 17 Nov". */
    fun label(): String {
        fun month(m: YearMonth, style: TextStyle) = m.month.getDisplayName(style, Locale.getDefault())
        return when (kind) {
            PeriodKind.MONTH -> "${month(anchor, TextStyle.FULL)} ${anchor.year}"
            PeriodKind.THREE_MONTHS, PeriodKind.SIX_MONTHS -> {
                val first = YearMonth.from(start())
                if (first.year == anchor.year) "${month(first, TextStyle.SHORT)} – ${month(anchor, TextStyle.SHORT)} ${anchor.year}"
                else "${month(first, TextStyle.SHORT)} ${first.year} – ${month(anchor, TextStyle.SHORT)} ${anchor.year}"
            }
            PeriodKind.YEAR -> "${anchor.year}"
            PeriodKind.ALL -> "All time"
            PeriodKind.DATES -> {
                val a = start()
                val b = end()
                val day = DateTimeFormatter.ofPattern("d MMM")
                when {
                    a == b -> a.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                    a.year != b.year -> a.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " – " + b.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                    a.month == b.month -> "${a.dayOfMonth} – ${b.format(day)}"
                    else -> "${a.format(day)} – ${b.format(day)}"
                }
            }
        }
    }

    /** How the comparison reads: "vs last month", "vs the 3 months before". */
    fun versus(): String = when (kind) {
        PeriodKind.MONTH -> "vs last month"
        PeriodKind.THREE_MONTHS -> "vs the 3 months before"
        PeriodKind.SIX_MONTHS -> "vs the 6 months before"
        PeriodKind.YEAR -> "vs last year"
        PeriodKind.ALL -> ""
        PeriodKind.DATES -> "vs the ${ChronoUnit.DAYS.between(start(), end()) + 1} days before"
    }

    /** Totals by day for short periods, by month for longer ones (over about two months). */
    fun byMonth(oldest: LocalDate? = null): Boolean = ChronoUnit.DAYS.between(start(oldest), end()) > 62
}

/** One point of a balance line: the balance at the end of [day]. */
data class BalancePoint(val day: LocalDate, val balanceMinor: Long)

/**
 * The balance at the end of each day from [from] to [to] (no later than today), worked back from today's balances:
 * every entry, transfer and money owed after a day is taken off again. With [accountId], that account only;
 * otherwise every account in [current] (the ones counted in the total). At most [maxPoints], evenly spread, always
 * keeping the last day.
 */
fun balanceHistory(
    current: Map<Long, Long>,
    entries: List<TransactionItem>,
    transfers: List<Transfer>,
    debts: List<Debt>,
    from: LocalDate,
    to: LocalDate,
    accountId: Long? = null,
    today: LocalDate = LocalDate.now(),
    maxPoints: Int = 120,
): List<BalancePoint> {
    val ids = if (accountId != null) setOf(accountId) else current.keys
    val last = minOf(to, today)
    if (from > last || ids.isEmpty()) return emptyList()
    // How much each day changed the balance.
    val change = HashMap<Long, Long>()
    fun add(epochDay: Long, amount: Long) { change[epochDay] = (change[epochDay] ?: 0L) + amount }
    entries.forEach { if (it.accountId in ids) add(it.epochDay, if (it.type == TxType.INCOME) it.amountMinor else -it.amountMinor) }
    transfers.forEach {
        if (it.toAccountId in ids) add(it.epochDay, it.amountMinor)
        if (it.fromAccountId in ids) add(it.epochDay, -it.amountMinor)
    }
    debts.forEach { if (it.accountId in ids) add(it.epochDay, -it.amountMinor) }
    // Today's balance includes everything, even entries dated later; take back what came after the last day shown.
    var balance = ids.sumOf { current[it] ?: 0L } - change.filterKeys { it > last.toEpochDay() }.values.sum()
    val days = ChronoUnit.DAYS.between(from, last).toInt() + 1
    val values = LongArray(days)
    for (i in days - 1 downTo 0) {
        values[i] = balance
        balance -= change[from.toEpochDay() + i] ?: 0L
    }
    val step = ((days + maxPoints - 1) / maxPoints).coerceAtLeast(1)
    val picked = (days - 1 downTo 0 step step).toList().reversed()
    return picked.map { BalancePoint(from.plusDays(it.toLong()), values[it]) }
}

/** Money in and money out for one month. */
data class MonthInOut(val month: YearMonth, val inMinor: Long, val outMinor: Long) {
    val savedMinor: Long get() = inMinor - outMinor
}

/** Income and spending for each month from [first] to [last], months with nothing included. */
fun monthlyInOut(entries: List<TransactionItem>, first: YearMonth, last: YearMonth): List<MonthInOut> {
    if (first > last) return emptyList()
    val byMonth = entries.groupBy { YearMonth.from(it.date) }
    val out = ArrayList<MonthInOut>()
    var m = first
    while (m <= last) {
        val list = byMonth[m].orEmpty()
        out += MonthInOut(m, list.totalOf(TxType.INCOME), list.totalOf(TxType.EXPENSE))
        m = m.plusMonths(1)
    }
    return out
}
