package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A subscription's real payment, late or early, found against the entry it already logged. Made-up data. */
class SubscriptionMatchTest {
    private fun sub(id: Long, name: String, amount: Long, type: TxType = TxType.EXPENSE, category: Long = 1, manual: Boolean = false) =
        RecurringItem(id, name, amount, type, category, 15, "2026-01", "2026-11", true, 1, null, "Cat", "", 0, manual = manual)

    private fun entry(id: Long, note: String, amount: Long, day: Long, type: TxType = TxType.EXPENSE, category: Long = 1) =
        TransactionItem(id, amount, type, category, day, note, 0, "Cat", "", 0, null, null)

    private val gym = sub(1, "Gym", 150_000)
    private val salary = sub(2, "Salary", 7_500_000, TxType.INCOME, category = 9)
    private val nov15 = 20_772L

    @Test
    fun `the gym paid on the 30th belongs to the entry logged on the 15th`() {
        val logged = entry(10, "Gym", 150_000, nov15)
        val m = SubscriptionMatch.find(150_000, false, "UPI-FITZONE GYM-fitzone@okaxis", nov15 + 15, listOf(gym), listOf(logged))!!
        assertEquals(10, m.entry.id)
        assertEquals("Gym", m.subscription.name)
    }

    @Test
    fun `a salary a few days late, and a little different, still matches`() {
        val logged = entry(11, "Salary", 7_500_000, nov15, TxType.INCOME, category = 9)
        assertEquals(11L, SubscriptionMatch.find(7_480_000, true, "NEFT ACME TECH SALARY", nov15 + 3, listOf(salary), listOf(logged))?.entry?.id)
    }

    @Test
    fun `nothing matches a different name at a different amount, or the wrong way round`() {
        val logged = entry(10, "Gym", 150_000, nov15)
        assertNull(SubscriptionMatch.find(149_000, false, "Swiggy", nov15 + 5, listOf(gym), listOf(logged)))
        // Money in, not out.
        assertNull(SubscriptionMatch.find(150_000, true, "Gym", nov15 + 5, listOf(gym), listOf(logged)))
        // Too far from its day.
        assertNull(SubscriptionMatch.find(150_000, false, "Gym", nov15 + 40, listOf(gym), listOf(logged)))
        // The same day it was logged: that's the entry itself, not a second payment.
        assertNull(SubscriptionMatch.find(150_000, false, "Gym", nov15, listOf(gym), listOf(logged)))
    }

    @Test
    fun `not when it was already paid some other way in between`() {
        val logged = entry(10, "Gym", 150_000, nov15)
        val paid = entry(12, "Gym membership", 150_000, nov15 + 4)
        assertNull(SubscriptionMatch.find(150_000, false, "Gym", nov15 + 10, listOf(gym), listOf(logged, paid)))
    }

    @Test
    fun `one marked paid by hand is never logged early, so never moved`() {
        val logged = entry(10, "Gym", 150_000, nov15)
        assertNull(SubscriptionMatch.find(150_000, false, "Gym", nov15 + 5, listOf(sub(1, "Gym", 150_000, manual = true)), listOf(logged)))
    }

    @Test
    fun `about the same amount means within a tenth`() {
        assertTrue(SubscriptionMatch.closeAmount(7_480_000, 7_500_000))
        assertTrue(SubscriptionMatch.closeAmount(160_000, 150_000))
        assertFalse(SubscriptionMatch.closeAmount(200_000, 150_000))
        assertFalse(SubscriptionMatch.closeAmount(0, 150_000))
    }

    @Test
    fun `a subscription paid by hand is due until it's paid`() {
        val rent = Recurring(name = "Rent", amountMinor = 1_800_000, type = TxType.EXPENSE, categoryId = 1, dayOfMonth = 1, startMonth = "2026-10", lastPostedMonth = "2026-10", manual = true)
        assertEquals("2026-11", rent.dueMonth(java.time.LocalDate.of(2026, 11, 13)).toString())
        assertNull(rent.dueMonth(java.time.LocalDate.of(2026, 10, 30)))
        assertNull(rent.copy(lastPostedMonth = "2026-11").dueMonth(java.time.LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun `the rent's bank text pays the month it's for, early or late`() {
        val rent = RecurringItem(5, "Rent", 1_800_000, TxType.EXPENSE, 1, 1, "2026-10", "2026-10", true, 1, null, "Home", "", 0, manual = true)
        // Paid on 3 Nov for November's rent (due the 1st).
        val w = SubscriptionMatch.waiting(1_800_000, false, "IMPS to SHARMA RENT", java.time.LocalDate.of(2026, 11, 3).toEpochDay(), listOf(rent))!!
        assertEquals("2026-11", w.month.toString())
        // Paid a few days early, on 28 Oct: still November's, as October is paid.
        assertEquals("2026-11", SubscriptionMatch.waiting(1_800_000, false, "Rent", java.time.LocalDate.of(2026, 10, 28).toEpochDay(), listOf(rent))?.month.toString())
        // Not one that logs itself, nor a different payment.
        assertNull(SubscriptionMatch.waiting(1_800_000, false, "Rent", java.time.LocalDate.of(2026, 11, 3).toEpochDay(), listOf(rent.copy(manual = false))))
        assertNull(SubscriptionMatch.waiting(45_000, false, "Swiggy", java.time.LocalDate.of(2026, 11, 3).toEpochDay(), listOf(rent)))
        // Already paid for November: nothing waits.
        assertNull(SubscriptionMatch.waiting(1_800_000, false, "Rent", java.time.LocalDate.of(2026, 11, 3).toEpochDay(), listOf(rent.copy(lastPostedMonth = "2026-11"))))
    }
}
