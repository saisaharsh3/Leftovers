package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyBudgetTest {
    private val today = LocalDate.of(2026, 10, 22) // 10 days left, today included

    private fun spend(amount: Long, day: LocalDate) = TransactionItem(
        id = day.toEpochDay() * 1000 + amount, amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, epochDay = day.toEpochDay(),
        note = "", createdAt = 0, categoryName = "Food", categoryEmoji = "food", categoryColor = 0, accountId = null, receiptPath = null,
    )

    @Test
    fun leftoverSpreadsOverTheDaysLeft() {
        val b = dailyBudget(10_000_00, listOf(spend(5_000_00, today.minusDays(3)), spend(100_00, today)), today = today)!!
        assertEquals(500_00, b.dailyLimit)
        assertEquals(400_00, b.leftToday)
        assertFalse(b.monthUsedUp)
    }

    @Test
    fun usedUpMonthSaysByHowMuch() {
        val b = dailyBudget(10_000_00, listOf(spend(12_000_00, today.minusDays(3)), spend(300_00, today)), today = today)!!
        assertEquals(0, b.dailyLimit)
        assertTrue(b.monthUsedUp)
        assertEquals(-2_300_00, b.monthLeft)
    }

    @Test
    fun aFewPaiseLeftIsNotUsedUp() {
        val b = dailyBudget(10_000_00, listOf(spend(10_000_00 - 5, today.minusDays(3))), today = today)!!
        assertEquals(0, b.dailyLimit)
        assertFalse(b.monthUsedUp)
    }

    @Test
    fun overToday_theNewLimitSpreadsWhatsLeftOverTheDaysAfter() {
        // ₹10,000 for the month, ₹5,000 spent before today: ₹500 a day for the 10 days left. Today ₹1,400 went.
        val b = dailyBudget(10_000_00, listOf(spend(5_000_00, today.minusDays(3)), spend(1_400_00, today)), today = today)!!
        assertEquals(-900_00, b.leftToday)
        // ₹3,600 left, over the 9 days after today: ₹400 a day from tomorrow.
        assertEquals(400_00L, b.newDailyLimit(10))
        // The last day of the budget: nothing to spread over.
        assertEquals(null, b.newDailyLimit(1))
    }

    @Test
    fun aBillPaidTodayDoesntUseTodaysAllowance() {
        // #74: 10,000 a month; 5,000 spent; Netflix 1,000 set aside while it was due, then charged today.
        val netflix = RecurringItem(
            id = 1, name = "Netflix", amountMinor = 1_000_00, type = TxType.EXPENSE, categoryId = 2, dayOfMonth = 22, startMonth = "2026-01",
            lastPostedMonth = "2026-10", active = true, everyMonths = 1, accountId = null, categoryName = "Fun", categoryEmoji = "", categoryColor = 0,
        )
        val before = listOf(spend(5_000_00, today.minusDays(3)))
        // Yesterday's view, with the bill still to come: (10,000 - 5,000 - 1,000) / 11 days.
        val dueYesterday = dailyBudget(10_000_00, before, reservedMinor = 1_000_00, today = today.minusDays(1))!!
        // Today it's charged, and a 100 coffee.
        val charged = spend(1_000_00, today).copy(id = 99, categoryId = 2, note = "Netflix", categoryName = "Fun")
        val items = before + charged + spend(100_00, today)
        val b = dailyBudget(10_000_00, items, today = today, billsToday = items.billsOn(today, listOf(netflix)))!!
        assertEquals(1_000_00, items.billsOn(today, listOf(netflix)))
        // The same allowance as when it was set aside (one day fewer to spread over), and only the coffee used it.
        assertEquals((10_000_00L - 5_000_00 - 1_000_00) / 10, b.dailyLimit)
        assertEquals(100_00, b.spentToday)
        assertTrue(b.leftToday > 0)
        assertTrue(dueYesterday.dailyLimit > 0)
        // The month still counts it.
        assertEquals(10_000_00L - 5_000_00 - 1_000_00 - 100_00, b.monthLeft)
    }

    @Test
    fun withoutBillsNothingChanges() {
        val items = listOf(spend(5_000_00, today.minusDays(3)), spend(100_00, today))
        assertEquals(0L, items.billsOn(today, emptyList()))
        assertEquals(dailyBudget(10_000_00, items, today = today), dailyBudget(10_000_00, items, today = today, billsToday = 0))
    }
}
