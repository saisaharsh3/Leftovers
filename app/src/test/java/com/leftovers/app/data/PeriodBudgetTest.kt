package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A budget for every so many days, from a payday (#45). */
class PeriodBudgetTest {
    private val biweekly = BudgetPlan(mode = BudgetMode.PERIOD, periodMinor = 20_000_00, periodDays = 14, periodFrom = LocalDate.of(2026, 11, 6))

    @Test fun theFirstPeriod() {
        val c = biweekly.cycleOf(LocalDate.of(2026, 11, 10))
        assertEquals(LocalDate.of(2026, 11, 6), c.start)
        assertEquals(LocalDate.of(2026, 11, 19), c.end)
    }

    @Test fun laterPeriodsFollowOnByThemselves() {
        val c = biweekly.cycleOf(LocalDate.of(2026, 12, 4))
        assertEquals(LocalDate.of(2026, 12, 4), c.start)
        assertEquals(LocalDate.of(2026, 12, 17), c.end)
    }

    @Test fun daysBeforeThePaydayChosenFallInThePeriodBefore() {
        val c = biweekly.cycleOf(LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 10, 23), c.start)
        assertEquals(LocalDate.of(2026, 11, 5), c.end)
    }

    @Test fun itsAmountIsThePeriodsBudget() {
        assertTrue(biweekly.isSet)
        assertEquals(20_000_00L, biweekly.budgetFor(java.time.YearMonth.now(), emptyList()))
        assertFalse(biweekly.supportsCarry)
        assertFalse(biweekly.copy(periodFrom = null).isSet)
    }

    @Test fun names() {
        assertEquals("every week", periodName(7))
        assertEquals("every 2 weeks", periodName(14))
        assertEquals("every 10 days", periodName(10))
    }
}
