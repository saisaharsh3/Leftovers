package com.leftovers.app.util

import com.leftovers.app.ui.components.noSpendDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchQueryTest {
    // A Wednesday.
    private val today = LocalDate.of(2026, 11, 25)
    private val fmt: (Long) -> String = { "₹${it / 100}" }

    @Test fun wordsAmountAndMonth() {
        val q = SearchQuery.parse("food >500 last month", today)
        assertEquals(listOf("food"), q.words)
        assertEquals(LocalDate.of(2026, 10, 1), q.from)
        assertEquals(LocalDate.of(2026, 10, 31), q.to)
        assertTrue(q.allows(501_00, LocalDate.of(2026, 10, 5)))
        assertFalse(q.allows(500_00, LocalDate.of(2026, 10, 5)))
        assertFalse(q.allows(900_00, LocalDate.of(2026, 11, 1)))
        assertEquals("Over ₹500 · last month", q.describe(fmt))
    }

    @Test fun amountsInWords() {
        val q = SearchQuery.parse("over 1,000 under 5000", today)
        assertTrue(q.words.isEmpty())
        assertTrue(q.allows(1_500_00, today))
        assertFalse(q.allows(1_000_00, today))
        assertFalse(q.allows(5_000_00, today))
        assertEquals("Over ₹1000 · under ₹5000", q.describe(fmt))
        val inclusive = SearchQuery.parse("<= 200", today)
        assertTrue(inclusive.allows(200_00, today))
        assertTrue(SearchQuery.parse("more than 50", today).allows(51_00, today))
        assertFalse(SearchQuery.parse("less than 50", today).allows(50_00, today))
    }

    @Test fun dates() {
        assertEquals(today.minusDays(1), SearchQuery.parse("yesterday", today).from)
        val week = SearchQuery.parse("this week", today)
        assertEquals(LocalDate.of(2026, 11, 23), week.from)
        assertEquals(today, week.to)
        val last = SearchQuery.parse("last week", today)
        assertEquals(LocalDate.of(2026, 11, 16), last.from)
        assertEquals(LocalDate.of(2026, 11, 22), last.to)
        assertEquals(today.minusDays(6), SearchQuery.parse("last 7 days", today).from)
        assertEquals(LocalDate.of(2025, 1, 1), SearchQuery.parse("last year", today).from)
    }

    @Test fun monthNames() {
        // March has begun this year; December hasn't, so it's last year's.
        assertEquals(LocalDate.of(2026, 3, 1), SearchQuery.parse("rent march", today).from)
        assertEquals(LocalDate.of(2025, 12, 1), SearchQuery.parse("dec", today).from)
        assertEquals(LocalDate.of(2024, 6, 1), SearchQuery.parse("june 2024", today).from)
        assertEquals(listOf("rent"), SearchQuery.parse("rent march", today).words)
    }

    @Test fun plainWordsStayWords() {
        val q = SearchQuery.parse("coffee 430 last", today)
        assertEquals(listOf("coffee", "430", "last"), q.words)
        assertFalse(q.hasFilters)
        assertNull(q.describe(fmt))
    }

    @Test fun noSpendDaysAreOverAndAfterTheFirstEntry() {
        val first = LocalDate.of(2026, 11, 3)
        assertTrue(noSpendDay(LocalDate.of(2026, 11, 10), 0, first, today))
        assertFalse(noSpendDay(LocalDate.of(2026, 11, 10), 250_00, first, today))
        // Before anything was logged, and today, which isn't over, don't count.
        assertFalse(noSpendDay(LocalDate.of(2026, 11, 2), 0, first, today))
        assertFalse(noSpendDay(today, 0, first, today))
        assertFalse(noSpendDay(LocalDate.of(2026, 11, 10), 0, null, today))
    }
}
