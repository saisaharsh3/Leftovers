package com.leftovers.app.ui.components

import com.leftovers.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {
    @Test fun ticksAreRoundAndCoverTheValues() {
        // $0 to $587: steps of $200.
        assertEquals(listOf(0L, 20_000L, 40_000L, 60_000L), niceTicks(0, 58_700))
    }

    @Test fun ticksReachBelowZero() {
        val ticks = niceTicks(-19_830, 210_000)
        assertTrue(ticks.first() <= -19_830)
        assertTrue(ticks.last() >= 210_000)
        assertTrue(0L in ticks)
    }

    @Test fun flatValuesStillGetAScale() {
        assertTrue(niceTicks(0, 0).size >= 2)
    }

    @Test fun shortAmounts() {
        val usd = Money("USD")
        assertEquals("$480", usd.compact(48_000))
        assertEquals("$1.2k", usd.compact(120_000))
        assertEquals("$1k", usd.compact(100_000))
        assertEquals("$15k", usd.compact(1_500_000))
        assertEquals("-$1k", usd.compact(-100_000))
        assertEquals("$2.4M", usd.compact(240_000_000))
    }
}
