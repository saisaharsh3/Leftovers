package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GreetingsTest {
    @Test fun festivalsWhereTheUserLives() {
        assertEquals(Festivals.Festival.DIWALI, Festivals.on(LocalDate.of(2026, 11, 8), "INR"))
        assertEquals(Festivals.Festival.HOLI, Festivals.on(LocalDate.of(2027, 3, 22), "INR"))
        // Diwali isn't a main festival for someone in the US; Thanksgiving is, on the fourth Thursday of November.
        assertNull(Festivals.on(LocalDate.of(2026, 11, 8), "USD"))
        assertEquals(Festivals.Festival.THANKSGIVING, Festivals.on(LocalDate.of(2026, 11, 26), "USD"))
        assertEquals(Festivals.Festival.THANKSGIVING, Festivals.on(LocalDate.of(2026, 10, 12), "CAD"))
        assertEquals(Festivals.Festival.EID_FITR, Festivals.on(LocalDate.of(2027, 3, 10), "AED"))
        assertEquals(Festivals.Festival.LUNAR_NEW_YEAR, Festivals.on(LocalDate.of(2027, 2, 6), "SGD"))
        assertEquals(Festivals.Festival.CHRISTMAS, Festivals.on(LocalDate.of(2026, 12, 25), "EUR"))
        assertEquals(Festivals.Festival.NEW_YEAR, Festivals.on(LocalDate.of(2027, 1, 1), "JPY"))
        assertNull(Festivals.on(LocalDate.of(2026, 12, 25), "JPY"))
        assertNull(Festivals.on(LocalDate.of(2026, 7, 14), "INR"))
    }

    private var id = 0L
    private fun spend(day: LocalDate, amount: Long, category: String = "Food") =
        TransactionItem(++id, amount, TxType.EXPENSE, 1, day.toEpochDay(), "", 0, category, "", 0, null, null)

    private val sunday = LocalDate.of(2026, 11, 8)
    private fun note(items: List<TransactionItem>) = WeeklyNote.note(items, sunday) { "₹${it / 100}" }

    @Test fun aWeekThatWentWell() {
        val last = (7..13).map { spend(sunday.minusDays(it.toLong()), 1_000_00) }
        val week = (0..6).map { spend(sunday.minusDays(it.toLong()), 600_00) }
        assertEquals("You spent 40% less this week than last (₹4200 vs ₹7000). Nice going 🎉", note(last + week))
    }

    @Test fun noSpendDaysAndQuietWeeks() {
        val last = (7..13).map { spend(sunday.minusDays(it.toLong()), 500_00) }
        val week = listOf(0, 1, 2, 4, 6).map { spend(sunday.minusDays(it.toLong()), 700_00) }
        assertTrue(note(last + week)!!.startsWith("2 no-spend days"))
        assertNull(note(emptyList()))
    }

    @Test fun aWeekThatCostMore() {
        val last = (7..13).map { spend(sunday.minusDays(it.toLong()), 500_00) }
        val week = (0..6).map { spend(sunday.minusDays(it.toLong()), 800_00) }
        assertTrue(note(last + week)!!.startsWith("This week came to ₹5600, 60% more"))
    }

    @Test fun aBigRiseFromLittleIsToldAsTwoAmounts() {
        // Sports went from 50 to 4,113 (+8,126%): a percentage like that means little.
        // The week as a whole about the same, so it's Sports that stands out.
        val last = (7..13).map { spend(sunday.minusDays(it.toLong()), 3_000_00) } + spend(sunday.minusDays(8), 50_00, "Sports")
        val week = (0..6).map { spend(sunday.minusDays(it.toLong()), 2_500_00) } + spend(sunday.minusDays(1), 4_113_00, "Sports")
        val text = note(last + week)!!
        assertTrue(text, !text.contains("%"))
        assertTrue(text, text.contains("₹4113") && text.contains("₹50"))
    }

    @Test fun onlySundayEvening() {
        assertTrue(WeeklyNote.due(sunday, java.time.LocalTime.of(19, 0)))
        assertTrue(!WeeklyNote.due(sunday, java.time.LocalTime.of(9, 0)))
        assertTrue(!WeeklyNote.due(sunday.minusDays(2), java.time.LocalTime.of(19, 0)))
    }
}
