package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountSumTest {
    @Test fun addsUp() {
        assertEquals(245_00L, AmountSum.evaluate("120+85+40"))
        assertEquals(75_50L, AmountSum.evaluate("100−24.5"))
    }

    @Test fun timesAndDivideComeFirst() {
        assertEquals(210_00L, AmountSum.evaluate("120+45×2"))
        assertEquals(110_00L, AmountSum.evaluate("100+30÷3"))
    }

    @Test fun roundsToThePaisa() {
        assertEquals(33_33L, AmountSum.evaluate("100÷3"))
        assertEquals(66_67L, AmountSum.evaluate("200÷3"))
    }

    @Test fun aTrailingOperatorUsesWhatsThere() {
        assertEquals(120_00L, AmountSum.evaluate("120+"))
        assertEquals(12_00L, AmountSum.evaluate("12."))
    }

    @Test fun noAmountWhenItCantBeWorkedOut() {
        assertNull(AmountSum.evaluate("50÷0"))
        assertNull(AmountSum.evaluate("20−50"))
        assertNull(AmountSum.evaluate(""))
        assertNull(AmountSum.evaluate("999999999×10"))
    }

    @Test fun readsWithSpaces() {
        assertEquals("1,200 + 45 ×", AmountSum.pretty("1200+45×") { if (it == "1200") "1,200" else it })
    }
}
