package com.leftovers.app.util

import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatementPdfTest {
    private val oct = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 20)
    private var nextId = 1L

    private fun tx(type: TxType, amount: Long, date: LocalDate, category: Long = 1, name: String = "Food") = TransactionItem(
        id = nextId++, amountMinor = amount, type = type, categoryId = category, epochDay = date.toEpochDay(),
        note = "secret", createdAt = 0, categoryName = name, categoryEmoji = "", categoryColor = 0, accountId = null, receiptPath = null,
    )

    private fun sub(name: String, amount: Long, day: Int, start: String = "2026-01", active: Boolean = true) = RecurringItem(
        id = nextId++, name = name, amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, dayOfMonth = day, startMonth = start,
        lastPostedMonth = null, active = active, everyMonths = 1, accountId = null, categoryName = "Bills", categoryEmoji = "", categoryColor = 0,
    )

    private val items = listOf(
        tx(TxType.INCOME, 75_000_00, LocalDate.of(2026, 10, 1), category = 9, name = "Salary"),
        tx(TxType.EXPENSE, 500_00, LocalDate.of(2026, 10, 3)),
        tx(TxType.EXPENSE, 2_000_00, LocalDate.of(2026, 10, 5), category = 2, name = "Rent"),
        tx(TxType.EXPENSE, 300_00, LocalDate.of(2026, 10, 7)),
        tx(TxType.EXPENSE, 1_000_00, LocalDate.of(2026, 9, 7)),
    )

    @Test fun monthTotalsAndComparison() {
        val s = StatementPdf.build(oct, 2026, items, BudgetPlan(monthlyMinor = 5_000_00), emptyList(), today)
        assertEquals(75_000_00L, s.incomeMinor)
        assertEquals(2_800_00L, s.spentMinor)
        assertEquals(5_000_00L, s.budgetMinor)
        assertEquals(2_200_00L, s.leftMinor)
        assertEquals(1_000_00L, s.previousSpentMinor)
        assertEquals(180, s.changePercent)
        assertEquals("October 2026", s.title)
    }

    @Test fun categoriesAndBiggestExpenses() {
        val s = StatementPdf.build(oct, 2026, items, BudgetPlan(), emptyList(), today)
        assertEquals(listOf("Rent", "Food"), s.categories.map { it.name })
        assertEquals(listOf(2_000_00L, 500_00L, 300_00L), s.biggest.map { it.amountMinor })
    }

    @Test fun noBudgetSetMeansNone() {
        val s = StatementPdf.build(oct, 2026, items, BudgetPlan(), emptyList(), today)
        assertNull(s.budgetMinor)
        assertNull(s.leftMinor)
    }

    @Test fun subscriptionsChargedSoFarOnly() {
        val subs = listOf(sub("Spotify", 119_00, 15), sub("Rent", 20_000_00, 28), sub("Paused", 99_00, 2, active = false), sub("Later", 50_00, 1, start = "2026-11"))
        val s = StatementPdf.build(oct, 2026, items, BudgetPlan(), subs, today)
        // The 28th hasn't come yet on the 20th; paused and not-yet-started ones aren't charged.
        assertEquals(listOf("Spotify"), s.charges.map { it.name })
    }

    @Test fun yearHasEachMonthAndTheYearBefore() {
        val s = StatementPdf.build(null, 2026, items + tx(TxType.EXPENSE, 400_00, LocalDate.of(2025, 6, 1)), BudgetPlan(), emptyList(), today)
        assertEquals(12, s.months.size)
        assertEquals(1_000_00L, s.months[8].spentMinor)
        assertEquals(2_800_00L, s.months[9].spentMinor)
        assertEquals(3_800_00L, s.spentMinor)
        assertEquals(400_00L, s.previousSpentMinor)
        assertEquals("2026", s.title)
    }

    @Test fun emptyMonthHasNothingToCompare() {
        val s = StatementPdf.build(YearMonth.of(2026, 3), 2026, items, BudgetPlan(), emptyList(), today)
        assertEquals(0L, s.spentMinor)
        assertTrue(s.categories.isEmpty())
        assertTrue(s.biggest.isEmpty())
        assertNull(s.changePercent)
    }
}
