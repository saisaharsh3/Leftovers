package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InsightsDataTest {
    private var nextId = 1L
    private fun entry(day: LocalDate, amount: Long, type: TxType = TxType.EXPENSE, account: Long? = 1) = TransactionItem(
        id = nextId++, amountMinor = amount, type = type, categoryId = 1, epochDay = day.toEpochDay(), note = "", createdAt = 0,
        categoryName = "", categoryEmoji = "", categoryColor = 0, accountId = account, receiptPath = null,
    )

    private val today = LocalDate.of(2026, 11, 18)

    @Test fun monthPeriodStepsAndLabels() {
        val p = InsightsPeriod(PeriodKind.MONTH, YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 11, 1), p.start())
        assertEquals(LocalDate.of(2026, 11, 30), p.end())
        assertEquals(YearMonth.of(2026, 10), p.shift(-1).anchor)
        assertFalse(p.hasNext(today))
        assertTrue(p.shift(-1).hasNext(today))
        assertEquals(18L, p.daysSoFar(today))
    }

    @Test fun threeMonthsEndAtTheAnchor() {
        val p = InsightsPeriod(PeriodKind.THREE_MONTHS, YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2025, 12, 1), p.start())
        assertEquals(LocalDate.of(2026, 2, 28), p.end())
        assertEquals(YearMonth.of(2025, 11), p.previous()!!.anchor)
        assertTrue(p.byMonth())
    }

    @Test fun pickedDatesMoveByTheirLength() {
        val p = InsightsPeriod(PeriodKind.DATES, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9))
        val before = p.previous()!!
        assertEquals(LocalDate.of(2026, 10, 27), before.start())
        assertEquals(LocalDate.of(2026, 11, 2), before.end())
        assertEquals("vs the 7 days before", p.versus())
        assertFalse(p.byMonth())
    }

    @Test fun allTimeHasNothingBefore() {
        val p = InsightsPeriod(PeriodKind.ALL)
        assertNull(p.previous())
        assertEquals(LocalDate.of(2024, 5, 2), p.start(LocalDate.of(2024, 5, 2)))
    }

    @Test fun balanceWorksBackFromToday() {
        // Today 1 has 500; it got 1000 income on the 10th and spent 200 on the 15th and 300 today.
        val entries = listOf(
            entry(LocalDate.of(2026, 11, 10), 1000, TxType.INCOME),
            entry(LocalDate.of(2026, 11, 15), 200),
            entry(today, 300),
        )
        val points = balanceHistory(mapOf(1L to 500L), entries, emptyList(), emptyList(), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 30), today = today)
        assertEquals(10, points.size) // the 9th to today, not the days to come
        assertEquals(0L, points.first().balanceMinor)
        assertEquals(1000L, points[1].balanceMinor)
        assertEquals(800L, points.first { it.day == LocalDate.of(2026, 11, 15) }.balanceMinor)
        assertEquals(500L, points.last().balanceMinor)
    }

    @Test fun balanceCountsTransfersAndMoneyLent() {
        val transfers = listOf(Transfer(fromAccountId = 2, toAccountId = 1, amountMinor = 100, epochDay = today.toEpochDay()))
        val debts = listOf(Debt(person = "Sam", amountMinor = 50, epochDay = today.toEpochDay(), accountId = 1))
        val one = balanceHistory(mapOf(1L to 1000L, 2L to 400L), emptyList(), transfers, debts, today.minusDays(1), today, accountId = 1, today = today)
        // Yesterday: 1000 - 100 transfer in + 50 lent out.
        assertEquals(listOf(950L, 1000L), one.map { it.balanceMinor })
        val both = balanceHistory(mapOf(1L to 1000L, 2L to 400L), emptyList(), transfers, debts, today.minusDays(1), today, today = today)
        // A transfer between them doesn't change the total; the money lent does.
        assertEquals(listOf(1450L, 1400L), both.map { it.balanceMinor })
    }

    @Test fun balanceLeavesOutEntriesAfterTheLastDay() {
        val entries = listOf(entry(LocalDate.of(2026, 11, 5), 100))
        val points = balanceHistory(mapOf(1L to 900L), entries, emptyList(), emptyList(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), today = today)
        assertEquals(1000L, points.last().balanceMinor)
    }

    @Test fun longHistoriesAreThinnedButKeepToday() {
        val points = balanceHistory(mapOf(1L to 0L), emptyList(), emptyList(), emptyList(), today.minusDays(999), today, today = today, maxPoints = 100)
        assertTrue(points.size <= 100)
        assertEquals(today, points.last().day)
    }

    @Test fun inAndOutByMonthIncludesEmptyMonths() {
        val entries = listOf(
            entry(LocalDate.of(2026, 9, 3), 2500, TxType.INCOME),
            entry(LocalDate.of(2026, 9, 4), 700),
            entry(LocalDate.of(2026, 11, 1), 50),
        )
        val months = monthlyInOut(entries, YearMonth.of(2026, 9), YearMonth.of(2026, 11))
        assertEquals(3, months.size)
        assertEquals(1800L, months[0].savedMinor)
        assertEquals(0L, months[1].outMinor)
        assertEquals(-50L, months[2].savedMinor)
    }
}
