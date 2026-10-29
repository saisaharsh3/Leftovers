package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterestTest {
    private fun interest(rate: String?, every: Int = 1) = Recurring(
        name = "Interest", amountMinor = 0, type = TxType.INCOME, categoryId = 1, dayOfMonth = 1, startMonth = "2026-01",
        everyMonths = every, ratePercent = rate,
    )

    @Test
    fun aYearlyRateOnTheBalanceForTheMonthsItCovers() {
        // ₹1,00,000 at 3.5% a year: ₹291.67 a month, ₹875 a quarter.
        assertEquals(29_167L, interest("3.5").interestOn(1_00_000_00))
        assertEquals(87_500L, interest("3.5", every = 3).interestOn(1_00_000_00))
    }

    @Test
    fun noBalanceNoInterest() {
        assertEquals(0L, interest("3.5").interestOn(-5_000_00))
        assertNull(interest(null).interestOn(1_00_000_00))
    }
}
