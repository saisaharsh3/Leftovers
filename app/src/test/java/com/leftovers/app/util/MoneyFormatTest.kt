package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class MoneyFormatTest {
    @Test fun letterSymbolGetsASpace() {
        Locale.setDefault(Locale.US)
        val aed = Money("AED")
        assertEquals("AED 250", aed.format(25_000))
        assertEquals("AED", aed.label)
    }

    @Test fun signSymbolStaysAttached() {
        Locale.setDefault(Locale.US)
        assertEquals("$12.50", Money("USD").format(1_250))
        assertFalse(Money("INR").format(10_000_000).contains("₹ "))
        assertEquals("₹ INR", Money("INR").label)
    }
}
