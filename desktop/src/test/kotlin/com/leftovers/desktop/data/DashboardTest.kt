package com.leftovers.desktop.data

import com.leftovers.app.data.Transaction
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dashboard's numbers. Made-up data only. */
class DashboardTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun store() = Store(Files.createTempDirectory("leftovers-test").toFile()).apply {
        save(Book.starter("EUR").let { it.copy(accounts = it.accounts.map { a -> if (a.id == 1L) a.copy(openingMinor = 10_000) else a }) })
        fun add(day: LocalDate, amount: Long, type: TxType, note: String, cat: Long, account: Long = 1) =
            saveTransaction(Transaction(amountMinor = amount, type = type, categoryId = cat, epochDay = day.toEpochDay(), note = note, accountId = account, createdAt = day.toEpochDay()))
        add(LocalDate.of(2026, 10, 1), 200_000, TxType.INCOME, "Salary", 13)
        add(LocalDate.of(2026, 10, 5), 50_000, TxType.EXPENSE, "Rent", 6)
        add(LocalDate.of(2026, 10, 9), 1_200, TxType.EXPENSE, "Bakery", 1)
        add(LocalDate.of(2026, 11, 1), 200_000, TxType.INCOME, "Salary", 13)
        add(LocalDate.of(2026, 11, 2), 1_500, TxType.EXPENSE, "Bakery", 1)
        add(LocalDate.of(2026, 11, 3), 80_000, TxType.EXPENSE, "Laptop", 4)
        saveTransfer(Transfer(fromAccountId = 1, toAccountId = 2, amountMinor = 30_000, epochDay = LocalDate.of(2026, 11, 4).toEpochDay(), note = ""))
    }

    @Test
    fun cashFlowAndSavingsRateByMonth() {
        val d = DashData(store().view.value, Widget(WidgetKind.CASH_FLOW), DashPeriod.QUARTER, today)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), d.months)
        val oct = d.cashFlow.single { it.month == YearMonth.of(2026, 10) }
        assertEquals(200_000L, oct.income)
        assertEquals(51_200L, oct.spent)
        assertEquals(74, d.savingsRate.single { it.first == YearMonth.of(2026, 10) }.second)
        assertEquals(null, d.savingsRate.single { it.first == YearMonth.of(2026, 9) }.second)
    }

    @Test
    fun netWorthWalksBackFromToday() {
        val d = DashData(store().view.value, Widget(WidgetKind.CASH_FLOW), DashPeriod.QUARTER, today)
        // Opening 100, then +2000 −512 in October, +2000 −815 in November; the transfer stays within the total.
        assertEquals(listOf(10_000L, 158_800L, 277_300L), d.netWorth.map { it.second })
    }

    @Test
    fun placesAndCategoriesRankBySpending() {
        val d = DashData(store().view.value, Widget(WidgetKind.CASH_FLOW), DashPeriod.QUARTER, today)
        assertEquals("Laptop", d.topPlaces.first().label)
        assertEquals(2, d.topPlaces.single { it.label == "Bakery" }.count)
        assertEquals("Shopping", d.byCategory.first().label)
    }

    @Test
    fun thisMonthAddsUpDayByDay() {
        val (now, last) = DashData(store().view.value, Widget(WidgetKind.THIS_VS_LAST), DashPeriod.MONTH, today).thisVsLast
        assertEquals(15, now.size)
        assertEquals(81_500L, now.last())
        assertEquals(31, last.size)
        assertEquals(51_200L, last.last())
        assertTrue(DashData(store().view.value, Widget(WidgetKind.THIS_VS_LAST), DashPeriod.MONTH, today).months.size == 6)
    }

    @Test
    fun yourOwnWidgetMeasuresGroupsAndFilters() {
        val v = store().view.value
        val count = DashData(v, Widget(WidgetKind.CUSTOM, measure = Measure.COUNT, groupBy = GroupBy.PLACE), DashPeriod.QUARTER, today)
        assertEquals(2L, count.group().single { it.label == "Bakery" }.value)
        val kept = DashData(v, Widget(WidgetKind.CUSTOM, measure = Measure.NET, groupBy = GroupBy.MONTH), DashPeriod.QUARTER, today)
        assertEquals(listOf(0L, 148_800L, 118_500L), kept.group().map { it.value })
        // Only Food & Dining: the bakery, twice.
        val food = DashData(v, Widget(WidgetKind.CUSTOM, measure = Measure.SPENT, groupBy = GroupBy.NONE, categoryIds = setOf(1)), DashPeriod.QUARTER, today)
        assertEquals(2_700L, food.total())
        // Its own period: this month only.
        assertEquals(81_500L, DashData(v, Widget(WidgetKind.CUSTOM, groupBy = GroupBy.NONE, period = DashPeriod.MONTH), DashPeriod.YEAR, today).total())
    }

    @Test
    fun dashboardsAreSavedWithTheSettings() {
        val s = store()
        s.setPrefs { copy(dashboards = listOf(Dashboard("Money", listOf(Widget(WidgetKind.CUSTOM, id = 7, title = "Coffee", span = 2, height = WidgetHeight.TALL, period = DashPeriod.YEAR, accountId = 1, categoryIds = setOf(1, 2), tag = "work", color = 0xFF7DB7F5, measure = Measure.COUNT, groupBy = GroupBy.PLACE, chart = ChartStyle.LIST)), DashPeriod.YEAR))) }
        val again = Store(s.dir)
        assertEquals(s.book.prefs.dashboards, again.book.prefs.dashboards)
    }
}
