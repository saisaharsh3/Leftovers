package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** A month on its own reads as one, in any language (#44). */
class MonthLabelTest {
    @Test fun english() = assertEquals("November 2026", YearMonth.of(2026, 11).label(Locale.ENGLISH))

    @Test fun russianIsNominativeAndCapitalised() = assertEquals("Ноябрь 2026", YearMonth.of(2026, 11).label(Locale.forLanguageTag("ru")))
}
