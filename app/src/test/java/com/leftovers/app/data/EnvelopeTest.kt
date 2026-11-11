package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A Dates budget as an envelope: only the entries chosen for it use it up (#42). */
class EnvelopeTest {
    private val from = LocalDate.of(2026, 11, 1)
    private fun item(amount: Long, note: String, account: Long? = 1) = TransactionItem(
        id = amount, amountMinor = amount, type = TxType.EXPENSE, categoryId = 1, epochDay = from.plusDays(2).toEpochDay(), note = note,
        createdAt = 0, categoryName = "Food", categoryEmoji = "utensils", categoryColor = 0, accountId = account, receiptPath = null,
    )
    private val trip = BudgetPlan(mode = BudgetMode.RANGE, rangeMinor = 200_00, rangeFrom = from, rangeTo = from.plusDays(29), rangeTag = "trip")

    @Test fun onlyTaggedEntriesCount() {
        assertTrue(trip.envelope)
        assertTrue(trip.counts(item(50_00, "Bus #trip")))
        assertFalse(trip.counts(item(1_000_00, "Rent")))
    }

    @Test fun anAccountNarrowsItToo() {
        val p = trip.copy(rangeAccount = 2)
        assertFalse(p.counts(item(50_00, "Bus #trip", account = 1)))
        assertTrue(p.counts(item(50_00, "Bus #trip", account = 2)))
    }

    @Test fun withoutATagOrAccountEverythingCounts() {
        val p = trip.copy(rangeTag = null)
        assertFalse(p.envelope)
        assertTrue(p.counts(item(1_000_00, "Rent")))
        assertEquals(false, BudgetPlan(rangeTag = "trip").envelope)
    }
}
