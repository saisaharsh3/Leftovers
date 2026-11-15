package com.leftovers.desktop.data

import com.leftovers.app.data.Debt
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import com.leftovers.app.util.BackupCrypto
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop's data, saved and read back, and in the phone's backup format. Made-up data only. */
class StoreTest {
    private fun tempStore() = Store(Files.createTempDirectory("leftovers-test").toFile())
    private val nov10 = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun Store.demo() {
        save(Book.starter("EUR").copy(prefs = Prefs(defaultAccountId = 1, onboarded = true)))
        saveTransaction(Transaction(amountMinor = 1250, type = TxType.EXPENSE, categoryId = 1, epochDay = nov10, note = "Lunch", accountId = 1, createdAt = 1))
        saveTransaction(Transaction(amountMinor = 300_000, type = TxType.INCOME, categoryId = 13, epochDay = nov10, note = "Salary", accountId = 2, createdAt = 2, details = "November"))
        saveTransfer(Transfer(fromAccountId = 2, toAccountId = 1, amountMinor = 5_000, epochDay = nov10, note = "Cash out", createdAt = 3))
        saveDebt(Debt(person = "Sam", amountMinor = 2_000, note = "Movie", epochDay = nov10, accountId = 1, createdAt = 4))
    }

    @Test
    fun balancesFollowEntriesTransfersAndMoneyLent() {
        val s = tempStore().apply { demo() }
        val v = s.view.value
        // Cash: -12.50 lunch, +50 transfer, -20 lent to Sam.
        assertEquals(-1250L + 5_000 - 2_000, v.account(1)!!.balanceMinor)
        assertEquals(300_000L - 5_000, v.account(2)!!.balanceMinor)
        assertEquals(listOf("Lunch", "Salary").sorted(), v.entries.map { it.note }.sorted())
    }

    @Test
    fun savedAndOpenedAgainIsTheSame() {
        val s = tempStore().apply { demo() }
        val again = Store(s.dir)
        assertEquals(s.book, again.book)
    }

    @Test
    fun aBackupKeepsEverythingButThisComputersPrefs() {
        val s = tempStore().apply { demo() }
        val json = s.exportJson(withPhotos = false)
        assertEquals("leftovers", json.getString("app"))
        assertTrue(!json.has("prefs") && !json.has("deleted"))
        val other = tempStore()
        other.replaceWith(Codec.open(json.toString(), null))
        assertEquals(s.book.transactions, other.book.transactions)
        assertEquals(s.book.debts, other.book.debts)
        assertEquals("EUR", other.book.currency)
    }

    @Test
    fun anEncryptedBackupNeedsItsPassword() {
        val s = tempStore().apply { demo() }
        val locked = BackupCrypto.encrypt(s.exportJson(false).toString(), "correct horse").toString()
        assertFailsWith<PasswordNeeded> { Codec.open(locked, null) }
        val wrong = assertFailsWith<PasswordNeeded> { Codec.open(locked, "nope") }
        assertTrue(wrong.wrong)
        assertEquals(2, Codec.decode(Codec.open(locked, "correct horse"), File("unused")).transactions.size)
    }

    @Test
    fun deletedEntriesComeBack() {
        val s = tempStore().apply { demo() }
        val lunch = s.book.transactions.first { it.note == "Lunch" }
        s.deleteTransaction(lunch)
        assertNull(s.book.transactions.firstOrNull { it.id == lunch.id })
        assertEquals(1, s.view.value.deleted.size)
        // Sync is told it's gone.
        assertTrue(s.book.removed.any { it.sameEntry(lunch) })
        s.restoreDeleted(listOf(lunch.id))
        assertEquals(lunch, s.book.transactions.first { it.id == lunch.id })
        assertTrue(s.book.removed.none { it.sameEntry(lunch) })
    }

    @Test
    fun paidBackInFullSettlesThePerson() {
        val s = tempStore().apply { demo() }
        s.settleUp("sam ")
        assertTrue(s.book.debts.all { it.settled })
        // The money came back into Cash.
        assertEquals(-1250L + 5_000, s.view.value.account(1)!!.balanceMinor)
    }

    @Test
    fun subscriptionsLogThemselvesOnce() {
        val s = tempStore().apply { demo() }
        s.saveRecurring(Recurring(name = "Gym", amountMinor = 4_000, type = TxType.EXPENSE, categoryId = 8, dayOfMonth = 5, startMonth = "2026-10", lastPostedMonth = null))
        val posted = s.postDueRecurring(LocalDate.of(2026, 11, 12))
        assertEquals(2, posted.size)
        assertEquals("2026-11", s.book.recurring.single().lastPostedMonth)
        assertTrue(s.postDueRecurring(LocalDate.of(2026, 11, 12)).isEmpty())
    }

    @Test
    fun mergingAddsOnlyWhatsNew() {
        val s = tempStore().apply { demo() }
        val other = tempStore().apply { demo() }
        other.saveTransaction(Transaction(amountMinor = 800, type = TxType.EXPENSE, categoryId = 1, epochDay = nov10, note = "Coffee", accountId = 1, createdAt = 9))
        val plan = s.mergeWith(other.exportJson(false))
        assertEquals(1, plan.add.transactions.size)
        assertEquals(3, s.book.transactions.size)
    }
}
