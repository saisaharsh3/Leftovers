package com.leftovers.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatePaymentsTest {
    private val minute = 60_000L
    private val noon = 1_790_000_000_000L

    @Test fun sameAmountSoonAfterIsADuplicate() {
        // A bank's SMS and email for one payment, ten minutes apart.
        assertTrue(DuplicatePayments.isDuplicate(25_000, noon + 10 * minute, listOf(AmountAt(25_000, noon))))
        // Logged by hand a few minutes before the alert arrived.
        assertTrue(DuplicatePayments.isDuplicate(25_000, noon, listOf(AmountAt(25_000, noon + 3 * minute))))
    }

    @Test fun differentAmountIsNot() {
        assertFalse(DuplicatePayments.isDuplicate(25_000, noon, listOf(AmountAt(25_100, noon))))
    }

    @Test fun sameAmountHoursApartIsNot() {
        // Two coffees at 80, morning and evening, are two payments.
        assertFalse(DuplicatePayments.isDuplicate(8_000, noon + 5 * 60 * minute, listOf(AmountAt(8_000, noon))))
    }

    @Test fun nothingSeenIsNot() {
        assertFalse(DuplicatePayments.isDuplicate(8_000, noon, emptyList()))
    }
}
