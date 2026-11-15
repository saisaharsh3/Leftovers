package com.leftovers.desktop.data

import com.leftovers.app.data.TxType
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The add panel's sums and saving. Made-up data only. */
class EntryDraftTest {
    @Test
    fun sumsWorkOutTheAmount() {
        assertEquals(1650L, Sum.minor("12+4.50"))
        assertEquals(75000L, Sum.minor("3*250"))
        assertEquals(2500L, Sum.minor("100/4"))
        assertEquals(112500L, Sum.minor("1,200 - 75"))
        assertEquals(1000L, Sum.minor("(2+3)×2"))
        assertNull(Sum.minor("12+"))
        assertNull(Sum.minor("abc"))
        assertNull(Sum.minor("5-10"))
    }

    private fun store() = Store(Files.createTempDirectory("leftovers-test").toFile()).apply { save(Book.starter("EUR")) }

    @Test
    fun aSplitBillIsYourShareAndTheyOweTheirs() {
        val s = store()
        val msg = s.saveEntry(EntryDraft(amountMinor = 9_000, categoryId = 1, note = "Dinner", accountId = 1, people = listOf("Ana", "Ben")), { "€" + it / 100 })
        assertEquals("€90 added · Ana and Ben owe you €30 each", msg)
        val entry = s.book.transactions.single()
        assertEquals(3_000L, entry.amountMinor)
        assertEquals(listOf(3_000L, 3_000L), s.book.debts.map { it.amountMinor })
        assertTrue(s.book.debts.all { it.splitOf == entry.id })
        // Opened again, it's the whole bill with its people.
        val again = s.draftOf(entry.id)!!
        assertEquals(9_000L, again.amountMinor)
        assertEquals(listOf("Ana", "Ben"), again.people)
        // Ben taken off: Ana owes half, and Ben owes nothing.
        s.saveEntry(again.copy(people = listOf("Ana")), { "" })
        assertEquals(4_500L, s.book.transactions.single().amountMinor)
        assertEquals(listOf("Ana" to 4_500L), s.book.debts.map { it.person to it.amountMinor })
    }

    @Test
    fun deletingTakesItsSharesAndUndoBringsThemBack() {
        val s = store()
        s.saveEntry(EntryDraft(amountMinor = 4_000, categoryId = 1, people = listOf("Ana")), { "" })
        val undo = s.deleteEntry(s.book.transactions.single().id)
        assertTrue(s.book.transactions.isEmpty() && s.book.debts.isEmpty())
        undo()
        assertEquals(1, s.book.transactions.size)
        assertEquals(1, s.book.debts.size)
    }

    @Test
    fun repeatingMakesASubscriptionAlreadyPaidThisMonth() {
        val s = store()
        val day = LocalDate.of(2026, 11, 3)
        assertEquals("€9 added · repeats every 3rd", s.saveEntry(EntryDraft(amountMinor = 999, categoryId = 7, note = "Music", day = day, repeatMonthly = true), { "€" + it / 100 }))
        val r = s.book.recurring.single()
        assertEquals("2026-11", r.lastPostedMonth)
        assertEquals(3, r.dayOfMonth)
        assertEquals(TxType.EXPENSE, r.type)
    }

    @Test
    fun noCategoryNoSave() {
        assertNull(store().saveEntry(EntryDraft(amountMinor = 100), { "" }))
    }
}
