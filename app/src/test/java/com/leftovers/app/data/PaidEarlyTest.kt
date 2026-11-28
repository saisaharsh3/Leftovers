package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** A bill paid before its day (#85): that month is done, and the next charge is the one after. */
class PaidEarlyTest {
    private val today = LocalDate.of(2026, 11, 28)
    private val electricity = Recurring(
        id = 1, name = "Electricity", amountMinor = 1_800_00, type = TxType.EXPENSE, categoryId = 1,
        dayOfMonth = 5, startMonth = "2026-01", lastPostedMonth = "2026-11",
    )

    @Test fun payingEarlyMovesTheNextChargeOn() {
        assertEquals(LocalDate.of(2026, 12, 5), electricity.nextChargeDate(today))
        // What paying early stores: the month of the next charge as logged.
        val paid = electricity.copy(lastPostedMonth = YearMonth.from(electricity.nextChargeDate(today)).toString())
        assertFalse(paid.isPendingIn(YearMonth.of(2026, 12)))
        assertEquals(LocalDate.of(2027, 1, 5), paid.nextChargeDate(today))
        // Its day of the month doesn't change.
        assertEquals(5, paid.dayOfMonth)
    }

    @Test fun itIsNoLongerSetAsideForThatMonth() {
        fun item(r: Recurring) = RecurringItem(r.id, r.name, r.amountMinor, r.type, r.categoryId, r.dayOfMonth, r.startMonth, r.lastPostedMonth, r.active, r.everyMonths, r.accountId, "Bills", "", 0)
        val december = YearMonth.of(2026, 12)
        assertEquals(1_800_00L, listOf(item(electricity)).pendingExpenses(december))
        val paid = electricity.copy(lastPostedMonth = "2026-12")
        assertEquals(0L, listOf(item(paid)).pendingExpenses(december))
        assertTrue(paid.isPendingIn(YearMonth.of(2027, 1)))
    }
}
