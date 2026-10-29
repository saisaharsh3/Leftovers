package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ExchangeRatesTest {
    @Test
    fun ratesReadLikeTyped() {
        assertEquals("95.26", ExchangeRates.format(BigDecimal("95.259206")))
        assertEquals("8.19", ExchangeRates.format(BigDecimal("8.187295")))
        assertEquals("0.0123", ExchangeRates.format(BigDecimal("0.012345")))
        assertEquals("1", ExchangeRates.format(BigDecimal("1.000000")))
    }
}
