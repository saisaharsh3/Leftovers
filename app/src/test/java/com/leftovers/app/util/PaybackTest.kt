package com.leftovers.app.util

import com.leftovers.app.data.Debt
import com.leftovers.app.data.PersonBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A payment that pays back money owed, matched by name (short in the app, full in the message) or by amount. Made-up names. */
class PaybackTest {
    private fun owes(person: String, vararg amounts: Long) =
        PersonBalance(person, amounts.sum(), amounts.map { Debt(person = person, amountMinor = it) })

    @Test
    fun `a short saved name matches the full one the bank writes`() {
        assertTrue(Payback.sameName("Ravi", "RAVI KUMAR SHARMA"))
        assertTrue(Payback.sameName("ravi sharma", "Mr. RAVI KUMAR SHARMA"))
        assertTrue(Payback.sameName("Ravi K", "RAVI KUMAR"))
        assertTrue(Payback.sameName("Priya", "SHRI PRIYA NAIR"))
        assertTrue(Payback.sameName("Priya N", "priya.nair@okaxis"))
        assertTrue(Payback.sameName("Priya Nair", "priyanair@ybl"))
        // Fewer words in the message than saved: the bank wrote only the first name.
        assertTrue(Payback.sameName("Priya Das", "PRIYA"))
        assertTrue(Payback.sameName("arjun", "ARJUN"))
    }

    @Test
    fun `different people never match`() {
        assertFalse(Payback.sameName("Ravi", "RAVINDRA SINGH"))
        assertFalse(Payback.sameName("Ravi Sharma", "RAVI VERMA"))
        assertFalse(Payback.sameName("Priya", "PRIYANKA DAS"))
        assertFalse(Payback.sameName("Arjun", "ARJUNA RAO"))
        assertFalse(Payback.sameName("Nick", "NIKHIL"))
        assertFalse(Payback.sameName("R", "RAVI KUMAR"))
        assertFalse(Payback.sameName("Ravi", ""))
        assertFalse(Payback.sameName("Ravi", "UPI/IMPS/123456789"))
        assertFalse(Payback.sameName("Priya", "priyankadas@okaxis"))
    }

    @Test
    fun `money in from someone who owes you is them paying you back`() {
        val owed = listOf(owes("Ravi", 25_000), owes("Priya", 40_000), owes("Rahul", -45_000))
        val m = Payback.find("RAVI KUMAR SHARMA", 25_000, incoming = true, owed = owed)!!
        assertEquals("Ravi", m.person)
        assertTrue(m.byName)
        // A part payment still counts.
        assertEquals("Ravi", Payback.find("RAVI KUMAR SHARMA", 10_000, incoming = true, owed = owed)?.person)
    }

    @Test
    fun `money out to someone you owe is you paying them back`() {
        val owed = listOf(owes("Ravi", 25_000), owes("Rahul", -45_000))
        assertEquals("Rahul", Payback.find("rahul.mehta@okhdfcbank", 45_000, incoming = false, owed = owed)?.person)
        // Nobody you owe is called Ravi: paying him isn't paying anyone back.
        assertNull(Payback.find("RAVI KUMAR", 25_000, incoming = false, owed = owed))
    }

    @Test
    fun `two people who could fit are told apart by the amount, or not at all`() {
        val owed = listOf(owes("Priya S", 30_000), owes("Priya N", 50_000))
        assertEquals("Priya N", Payback.find("PRIYA NAIR", 50_000, incoming = true, owed = owed)?.person)
        val bothPriya = listOf(owes("Priya", 30_000), owes("priya", 0L).copy(person = "Priya K", netMinor = 50_000, entries = listOf(Debt(person = "Priya K", amountMinor = 50_000))))
        assertEquals("Priya K", Payback.find("PRIYA KAPOOR", 50_000, incoming = true, owed = bothPriya)?.person)
        val sameAmounts = listOf(owes("Priya", 30_000), owes("Priya", 30_000).copy(person = "Priya Das"))
        assertNull(Payback.find("PRIYA", 12_345, incoming = true, owed = sameAmounts))
    }

    @Test
    fun `a UPI ID that starts with a saved first name counts only when the amount agrees`() {
        val owed = listOf(owes("Priya", 40_000), owes("Ravi", 25_000))
        // "priyanair" might be Priya, or might be a Priyanka: the exact amount settles it.
        assertFalse(Payback.sameName("Priya", "priyanair92@okicici"))
        assertEquals("Priya", Payback.find("priyanair92@okicici", 40_000, incoming = true, owed = owed)?.person)
        assertFalse(Payback.find("priyanair92@okicici", 40_000, incoming = true, owed = owed)!!.byName)
        assertNull(Payback.find("priyanair92@okicici", 12_000, incoming = true, owed = owed))
        // And never for someone whose name only looks like the start: "ravindra" isn't Ravi, without the amount.
        assertNull(Payback.find("ravindra@ybl", 10_000, incoming = true, owed = owed))
        // A phone-number UPI ID has no name at all, so the amount alone may tell.
        assertEquals("Ravi", Payback.find("9876543210@ybl", 25_000, incoming = true, owed = owed)?.person)
    }

    @Test
    fun `with no name, only an amount that fits exactly one person`() {
        val owed = listOf(owes("Ravi", 25_000), owes("Priya", 40_000, 15_000))
        assertEquals("Ravi", Payback.find("", 25_000, incoming = true, owed = owed)?.person)
        // One of Priya's entries.
        assertEquals("Priya", Payback.find("", 15_000, incoming = true, owed = owed)?.person)
        assertFalse(Payback.find("", 25_000, incoming = true, owed = owed)!!.byName)
        assertNull(Payback.find("", 99_900, incoming = true, owed = owed))
    }

    @Test
    fun `a name that is nobody here isn't pinned on whoever owes that amount`() {
        val owed = listOf(owes("Ravi", 25_000))
        assertNull(Payback.find("SALARY ACME LTD", 25_000, incoming = true, owed = owed))
        assertNull(Payback.find("ANJALI MEHTA", 25_000, incoming = true, owed = owed))
    }

    @Test
    fun `nothing owed, nothing to match`() {
        assertNull(Payback.find("RAVI", 25_000, incoming = true, owed = emptyList()))
        assertNull(Payback.find("RAVI", 0, incoming = true, owed = listOf(owes("Ravi", 25_000))))
    }
}
