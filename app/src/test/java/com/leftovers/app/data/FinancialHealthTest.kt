package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialHealthTest {
    private val fmt: (Long) -> String = { "₹${it / 100}" }

    @Test fun healthyMonth() {
        // Kept 30%, within budget, owes nothing, 8 months of cushion, steady pay.
        val h = FinancialHealth.of(100_000_00, 70_000_00, 1.0, 80_000_00, 0, 560_000_00, listOf(100_000_00, 100_000_00, 100_000_00), fmt)!!
        assertEquals(100, h.score)
        assertEquals("Great", h.label)
        assertEquals("You kept 30% of what came in", h.parts[0].detail)
    }

    @Test fun overspendingAndOwing() {
        // Spent more than came in, 20% over budget, owes half a month's pay, no cushion.
        val h = FinancialHealth.of(50_000_00, 60_000_00, 1.0, 50_000_00, 25_000_00, 0, listOf(50_000_00, 50_000_00, 50_000_00), fmt)!!
        assertTrue(h.score < 40)
        assertEquals("Needs attention", h.label)
        assertEquals("You spent 20% more than came in", h.parts[0].detail)
        assertEquals("You owe ₹25000, 50% of a month's income", h.parts[2].detail)
        assertEquals("Nothing put by for a rainy day yet", h.parts[3].detail)
    }

    @Test fun whatItCantTellIsLeftOut() {
        // No income logged: saving and steady income are left out, and the rest still make a score.
        val h = FinancialHealth.of(0, 30_000_00, 1.0, 40_000_00, 0, 90_000_00, emptyList(), fmt)!!
        assertNull(h.parts[0].score)
        assertNull(h.parts[4].score)
        // Spending 75% of the budget is full marks, owing nothing is too, three months' cushion is half.
        assertEquals(Math.round((25 * 1f + 20 * 1f + 20 * 0.5f) / 65 * 100), h.score)
    }

    @Test fun nothingLoggedIsNoScore() {
        assertNull(FinancialHealth.of(0, 0, 1.0, null, 0, 0, emptyList(), fmt))
    }

    @Test fun unsteadyIncomeCountsLess() {
        val steady = FinancialHealth.of(90_000_00, 60_000_00, 3.0, null, 0, 0, listOf(30_000_00, 30_000_00, 30_000_00), fmt)!!
        val jumpy = FinancialHealth.of(90_000_00, 60_000_00, 3.0, null, 0, 0, listOf(5_000_00, 70_000_00, 15_000_00), fmt)!!
        assertEquals(1f, steady.parts[4].score)
        assertTrue(jumpy.parts[4].score!! < 0.2f)
        assertTrue(jumpy.score < steady.score)
    }
}
