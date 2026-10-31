package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetCycleTest {
    @Test
    fun startingOnTheFirstIsTheCalendarMonth() {
        val c = BudgetPlan().cycleOf(LocalDate.of(2026, 10, 29))
        assertEquals(YearMonth.of(2026, 10), c.month)
        assertEquals(LocalDate.of(2026, 10, 1), c.start)
        assertEquals(LocalDate.of(2026, 10, 31), c.end)
        assertEquals(3, c.daysLeft(LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun paydayToPayday() {
        val plan = BudgetPlan(startDay = 25)
        // The 29th is after payday: October's budget month, 25 Oct to 24 Nov.
        val late = plan.cycleOf(LocalDate.of(2026, 10, 29))
        assertEquals(YearMonth.of(2026, 10), late.month)
        assertEquals(LocalDate.of(2026, 11, 24), late.end)
        assertEquals(27, late.daysLeft(LocalDate.of(2026, 10, 29)))
        // The 10th is before payday: still September's, 25 Sep to 24 Oct.
        val early = plan.cycleOf(LocalDate.of(2026, 10, 10))
        assertEquals(YearMonth.of(2026, 9), early.month)
        assertTrue(LocalDate.of(2026, 9, 25) in early)
        assertFalse(LocalDate.of(2026, 10, 25) in early)
    }

    @Test
    fun spendingCountsInItsBudgetMonth() {
        val plan = BudgetPlan(monthlyMinor = 10_000, startDay = 25)
        fun spend(amount: Long, day: LocalDate) = TransactionItem(
            id = amount, amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, epochDay = day.toEpochDay(), note = "", createdAt = 0,
            categoryName = "Food", categoryEmoji = "food", categoryColor = 0, accountId = null, receiptPath = null,
        )
        // Spent on 26 Sep and 20 Oct: both in September's budget month (25 Sep to 24 Oct).
        val items = listOf(spend(3_000, LocalDate.of(2026, 9, 26)), spend(2_000, LocalDate.of(2026, 10, 20)))
        assertEquals(5_000, plan.leftoverOf(YearMonth.of(2026, 9), items))
        assertEquals(10_000, plan.leftoverOf(YearMonth.of(2026, 10), items))
    }

    @Test
    fun subscriptionsStillDueCountOnceEach() {
        fun sub(day: Int, amount: Long, lastPosted: String?) = RecurringItem(
            id = day.toLong(), name = "Bill", amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, dayOfMonth = day,
            startMonth = "2026-01", lastPostedMonth = lastPosted, active = true, everyMonths = 1, accountId = null,
            categoryName = "Bills", categoryEmoji = "bills", categoryColor = 0,
        )
        val cycle = BudgetPlan(startDay = 25).cycleOf(LocalDate.of(2026, 10, 29)) // 25 Oct to 24 Nov
        // Rent on the 5th (Nov, once), a bill on the 30th (Oct, not logged yet), one on the 26th already logged in October.
        val subs = listOf(sub(5, 18_000, "2026-10"), sub(30, 500, "2026-09"), sub(26, 100, "2026-10"))
        assertEquals(18_500, subs.pendingExpenses(cycle, LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun aBudgetForDatesOfYourOwn() {
        val today = LocalDate.now()
        val plan = BudgetPlan(mode = BudgetMode.RANGE, rangeMinor = 7_000, rangeFrom = today.minusDays(3), rangeTo = today.plusDays(10))
        val c = plan.cycleOf(today)
        assertEquals(today.minusDays(3), c.start)
        assertEquals(11, c.daysLeft(today))
        fun spend(amount: Long, day: LocalDate) = TransactionItem(
            id = amount, amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, epochDay = day.toEpochDay(), note = "", createdAt = 0,
            categoryName = "Food", categoryEmoji = "food", categoryColor = 0, accountId = null, receiptPath = null,
        )
        // Spending before the dates doesn't count against them.
        val items = listOf(spend(1_000, today.minusDays(1)), spend(500, today.minusDays(5)))
        assertEquals(7_000, plan.budgetFor(c.month, items))
        assertEquals(6_000, plan.leftoverOf(c.month, items))
        // Once the dates are over, there's no budget until new ones are set.
        assertFalse(plan.copy(rangeTo = today.minusDays(1)).isSet)
    }
}
