package com.leftovers.app.util

import com.leftovers.app.data.Debt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Money owed sent to a friend's Leftovers: the link carries the entries, and they arrive the other way round. */
class SharedOwedTest {
    private val lent = Debt(id = 4, person = "Asha", amountMinor = 25_000, note = "Dinner", epochDay = 20_760, createdAt = 1_700_000_000_000)
    private val paidBack = Debt(id = 5, person = "Asha", amountMinor = -5_000, note = "", epochDay = 20_762, createdAt = 1_700_000_500_000, repayment = true)

    @Test
    fun `a link carries the entries and reads back the same`() {
        val link = SharedOwed.link("pixel-8-abc234", "INR", listOf(lent, paidBack))!!
        assertTrue(link.startsWith(SharedOwed.PAGE + "#"))
        val shared = SharedOwed.parse(link)!!
        assertEquals("INR", shared.currency)
        assertEquals(2, shared.lines.size)
        assertEquals(20_000, shared.netMinor)
        assertEquals("Dinner", shared.lines[0].note)
        assertTrue(shared.lines[1].repayment)
    }

    @Test
    fun `the app's own link reads the same as the page's`() {
        val code = SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!.substringAfter('#')
        assertEquals(SharedOwed.parse("${SharedOwed.PAGE}#$code"), SharedOwed.parse("leftovers://owed?d=$code"))
    }

    @Test
    fun `what was lent arrives as borrowed, under the sender's name`() {
        val line = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!)!!.lines.single()
        val mine = SharedOwed.mirror(line, " Ravi ")
        assertEquals("Ravi", mine.person)
        assertEquals(-25_000, mine.amountMinor)
        assertEquals(20_760, mine.epochDay)
        assertNull(mine.accountId)
        assertNotNull(mine.sharedId)
    }

    @Test
    fun `an entry keeps one id however often it is sent, and the id it came with`() {
        val first = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!)!!.lines.single().id
        val again = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent, paidBack))!!)!!.lines.first().id
        assertEquals(first, again)
        // Sent back by the one who received it: still the same id, so the first phone doesn't add its own entry.
        val received = SharedOwed.mirror(SharedOwed.Line(first, 25_000, 20_760, "Dinner", false), "Ravi")
        assertEquals(first, SharedOwed.idOf("other-phone-zzz999", received))
    }

    @Test
    fun `the link opens in the app, and so does the page's button, but nothing else`() {
        assertTrue(SharedOwed.isLink("https", "saisaharsh3.github.io", "/Leftovers/add"))
        assertTrue(SharedOwed.isLink("https", "saisaharsh3.github.io", "/Leftovers/add.html"))
        assertTrue(SharedOwed.isLink("leftovers", "owed", ""))
        assertTrue(!SharedOwed.isLink("https", "saisaharsh3.github.io", "/Leftovers/"))
        assertTrue(!SharedOwed.isLink("https", "example.com", "/Leftovers/add"))
        assertTrue(!SharedOwed.isLink("http", "saisaharsh3.github.io", "/Leftovers/add"))
    }

    @Test
    fun `nothing to send and anything that isn't a link give nothing`() {
        assertNull(SharedOwed.link("pixel-8-abc234", "INR", emptyList()))
        assertNull(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent.copy(amountMinor = 0))))
        assertNull(SharedOwed.parse("https://example.com/"))
        assertNull(SharedOwed.parse("leftovers://owed?d=not-a-link"))
        assertNull(SharedOwed.parse(""))
    }
}
