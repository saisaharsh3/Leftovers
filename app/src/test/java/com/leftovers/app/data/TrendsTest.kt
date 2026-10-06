package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TrendsTest {
    private val oct = YearMonth.of(2026, 10)
    private var nextId = 1L
    private fun tx(amount: Long, date: LocalDate, category: String = "Food") = TransactionItem(
        id = nextId++, amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, epochDay = date.toEpochDay(),
        note = "", createdAt = 0, categoryName = category, categoryEmoji = "", categoryColor = 0, accountId = null, receiptPath = null,
    )
    private val fmt: (Long) -> String = { "₹${it / 100}" }

    @Test fun categoryIncreaseIsReported() {
        val items = listOf(tx(10_000, LocalDate.of(2026, 9, 3)), tx(13_000, LocalDate.of(2026, 10, 3)))
        val trends = spendingTrends(items, oct, TxType.EXPENSE, fmt, today = LocalDate.of(2026, 10, 31))
        assertEquals(Trend("Food is up 30% vs last month", false), trends.first())
    }

    @Test fun smallChangesAreIgnored() {
        val items = listOf(tx(10_000, LocalDate.of(2026, 9, 3)), tx(11_000, LocalDate.of(2026, 10, 3)))
        assertTrue(spendingTrends(items, oct, TxType.EXPENSE, fmt, today = LocalDate.of(2026, 10, 31)).isEmpty())
    }

    @Test fun weekendPatternAndPace() {
        // 3 & 4 Oct 2026 are a weekend; 5–9 are weekdays.
        val items = listOf(3, 4).map { tx(10_000, LocalDate.of(2026, 10, it)) } +
            (5..9).map { tx(1_000, LocalDate.of(2026, 10, it)) }
        val trends = spendingTrends(items, oct, TxType.EXPENSE, fmt, today = LocalDate.of(2026, 10, 10)).map { it.text }
        assertTrue(trends.any { it.startsWith("You spend") && it.endsWith("more per day on weekends") })
        // 25,000 over 10 days → 77,500 over 31 days.
        assertTrue(trends.any { it.startsWith("At this pace you'll spend about ₹775") })
    }
}
