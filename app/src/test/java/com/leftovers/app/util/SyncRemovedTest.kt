package com.leftovers.app.util

import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class SyncRemovedTest {
    private fun store() = SyncRemoved(File.createTempFile("removed", ".json").apply { delete(); deleteOnExit() })
    private fun entry(note: String = "Coffee", createdAt: Long = 1_000) =
        Transaction(id = 7, amountMinor = 5000, type = TxType.EXPENSE, categoryId = 1, epochDay = 20_000, note = note, createdAt = createdAt)

    @Test
    fun removedEntryMatchesOnlyEntriesMadeBeforeIt() {
        val s = store()
        s.add(entry(), now = 2_000)
        val r = s.all(now = 3_000).single()
        assertTrue(r.matches(entry(" coffee ", createdAt = 1_500)))
        // Logged again later with the same day, amount and note: a new entry, so it stays.
        assertFalse(r.matches(entry(createdAt = 2_500)))
        assertFalse(r.matches(entry("Tea")))
    }

    @Test
    fun forgetTakesItOffTheList() {
        val s = store()
        s.add(entry(), now = 2_000)
        s.forget(entry(), now = 2_500)
        assertTrue(s.all(now = 3_000).isEmpty())
    }

    @Test
    fun oldRemovalsLapse() {
        val s = store()
        s.add(entry(), now = 2_000)
        assertEquals(1, s.all(now = 2_000 + TimeUnit.DAYS.toMillis(89)).size)
        assertTrue(s.all(now = 2_000 + TimeUnit.DAYS.toMillis(91)).isEmpty())
    }

    @Test
    fun roundTripsThroughJson() {
        val list = listOf(SyncRemoved.Removed(20_000, 5000, TxType.INCOME, "Salary", 9))
        assertEquals(list, SyncRemoved.decode(SyncRemoved.encode(list)))
    }
}
