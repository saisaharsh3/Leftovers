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
}
