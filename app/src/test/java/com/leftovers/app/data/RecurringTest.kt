package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringTest {
    private fun sub(start: String, every: Int, day: Int = 12, lastPosted: String? = null) =
        Recurring(name = "Prime", amountMinor = 149_900, type = TxType.EXPENSE, categoryId = 1, dayOfMonth = day, startMonth = start, lastPostedMonth = lastPosted, everyMonths = every)

    @Test fun monthlyIsDueEveryMonthFromItsStart() {
        val s = sub("2026-08", every = 1)
        assertFalse(s.isDueIn(YearMonth.of(2026, 7)))
        assertTrue(s.isDueIn(YearMonth.of(2026, 8)))
        assertTrue(s.isDueIn(YearMonth.of(2026, 11)))
    }

    @Test fun yearlyIsDueOnlyInItsMonth() {
        val s = sub("2026-03", every = 12)
        assertTrue(s.isDueIn(YearMonth.of(2026, 3)))
        assertFalse(s.isDueIn(YearMonth.of(2026, 4)))
        assertFalse(s.isDueIn(YearMonth.of(2027, 2)))
        assertTrue(s.isDueIn(YearMonth.of(2027, 3)))
        assertTrue(s.isDueIn(YearMonth.of(2030, 3)))
    }

    @Test fun yearlyNextChargeSkipsToNextYearOnceLogged() {
        val s = sub("2026-10", every = 12, day = 20)
        assertEquals(LocalDate.of(2026, 10, 20), s.nextChargeDate(LocalDate.of(2026, 10, 6)))
        val logged = s.copy(lastPostedMonth = "2026-10")
        assertEquals(LocalDate.of(2027, 10, 20), logged.nextChargeDate(LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2027, 10, 20), logged.nextChargeDate(LocalDate.of(2027, 2, 1)))
    }

    @Test fun pendingCountsOnlyInTheBillingMonth() {
        val s = sub("2026-10", every = 12)
        assertTrue(s.isPendingIn(YearMonth.of(2026, 10)))
        assertFalse(s.isPendingIn(YearMonth.of(2026, 11)))
        assertFalse(s.copy(lastPostedMonth = "2026-10").isPendingIn(YearMonth.of(2026, 10)))
        assertFalse(s.copy(active = false).isPendingIn(YearMonth.of(2026, 10)))
    }

    @Test fun monthlyShareSpreadsAYearlyBill() {
        assertEquals(149_900L / 12, sub("2026-10", every = 12).monthlyShareMinor)
        assertEquals(149_900L, sub("2026-10", every = 1).monthlyShareMinor)
    }
}
