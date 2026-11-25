package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SharedEntriesTest {
    private var id = 1L
    private fun entry(amount: Long, category: String, note: String = "", type: TxType = TxType.EXPENSE, day: String = "2026-11-22") = TransactionItem(
        id = id++, amountMinor = amount, type = type, categoryId = 1, epochDay = LocalDate.parse(day).toEpochDay(),
        note = note, createdAt = id, categoryName = category, categoryEmoji = "", categoryColor = 0, accountId = 7, receiptPath = null,
    )

    @Test fun aLinkReadsBackAsTheSameEntries() {
        val items = listOf(entry(85000, "Food & Dining", "Dinner"), entry(300000, "Salary", type = TxType.INCOME))
        val link = SharedEntries.link("INR", items)!!
        assertTrue(link.startsWith(SharedOwed.PAGE + "#"))
        val back = SharedEntries.parse(link)!!
        assertEquals("INR", back.currency)
        assertEquals(listOf(85000L, 300000L), back.lines.map { it.amountMinor })
        assertEquals(listOf(false, true), back.lines.map { it.income })
        assertEquals("Dinner", back.lines[0].note)
        assertEquals("Food & Dining", back.lines[0].category)
        assertEquals(LocalDate.of(2026, 11, 22), back.lines[0].date)
        // The app's own link form works too.
        assertEquals(2, SharedEntries.parse("leftovers://owed?d=" + link.substringAfter('#'))!!.lines.size)
    }

    @Test fun moneyOwedLinksAreNotEntries() {
        val owed = SharedOwed.PAGE + "#" + SharedOwed.encode("""{"v":1,"c":"INR","e":[["a.1",500,20000,"Lunch",0]]}""")
        assertNull(SharedEntries.parse(owed))
        val entries = SharedEntries.link("INR", listOf(entry(500, "Food")))!!
        assertNull(SharedOwed.parse(entries))
    }

    @Test fun nothingAboutAccountsTravels() {
        val json = SharedOwed.decode(SharedEntries.link("INR", listOf(entry(500, "Food")))!!.substringAfter('#'))
        // Only amount, kind, day, category and note: five values, no account id.
        assertEquals("""[500,0,20779,"Food",""]""", org.json.JSONObject(json).getJSONArray("x").getJSONArray(0).toString())
    }

    @Test fun atMostFortyInALink() {
        val many = (1..55).map { entry(100L * it, "Food") }
        assertEquals(SharedEntries.MAX, SharedEntries.parse(SharedEntries.link("USD", many)!!)!!.lines.size)
        assertTrue(SharedEntries.text(many, Money("USD"), "x").contains("Add the first 40"))
    }

    @Test fun readableText() {
        val one = SharedEntries.text(listOf(entry(85000, "Food & Dining", "Dinner")), Money("INR"), null)
        assertEquals("Dinner · ₹850 · 22 Nov (Food & Dining)", one)
        val two = SharedEntries.text(listOf(entry(85000, "Food"), entry(15000, "Travel")), Money("INR"), "L")
        assertTrue(two, two.startsWith("2 entries from Leftovers:"))
        assertTrue(two, two.contains("Spent ₹1,000"))
        assertTrue(two, two.endsWith("Add them to your Leftovers: L"))
    }

    @Test fun badLinksAreIgnored() {
        assertNull(SharedEntries.parse("https://example.com/#nope"))
        val huge = SharedOwed.PAGE + "#" + SharedOwed.encode("""{"v":1,"t":"x","c":"INR","x":[[-5,0,1,"A",""]]}""")
        assertNull(SharedEntries.parse(huge))
    }
}
