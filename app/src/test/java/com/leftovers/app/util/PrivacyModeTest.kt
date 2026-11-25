package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyModeTest {
    @Test fun hiddenAmountsShowNoNumbers() {
        val shown = Money("INR")
        val hidden = Money("INR", hidden = true)
        assertTrue(shown.format(45_000).any { it.isDigit() })
        assertEquals("₹••••", hidden.format(45_000))
        assertEquals("₹••••", hidden.formatWhole(1_99_99_900))
        assertEquals("-₹••••", hidden.format(-500))
        assertEquals("••", hidden.compact(1_000_000))
        assertTrue(hidden.format(0).none { it.isDigit() })
    }

    @Test fun lettersForASymbolKeepTheirSpace() {
        assertEquals("CHF ••••", Money("CHF", hidden = true).format(1234))
    }
}
