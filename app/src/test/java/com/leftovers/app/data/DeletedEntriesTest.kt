package com.leftovers.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DeletedFilterTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test fun datePresets() {
        assertTrue(DeletedFilter.Today.matches(today, today))
        assertFalse(DeletedFilter.Today.matches(today.minusDays(1), today))
        assertTrue(DeletedFilter.Week.matches(today.minusDays(6), today))
        assertFalse(DeletedFilter.Week.matches(today.minusDays(7), today))
        assertTrue(DeletedFilter.Month.matches(today.minusDays(29), today))
        assertTrue(DeletedFilter.All.matches(today.minusYears(3), today))
    }

    @Test fun customRangeIncludesBothEnds() {
        val range = DeletedFilter.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))
        assertTrue(range.matches(LocalDate.of(2026, 10, 1), today))
        assertTrue(range.matches(LocalDate.of(2026, 10, 3), today))
        assertFalse(range.matches(LocalDate.of(2026, 10, 4), today))
    }
}

/** Delete, restore and clean-up against a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeletedEntriesTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.categoryDao(), db.recurringDao(), db.deletedDao())
        db.categoryDao().upsert(Category(1, "Food", "utensils", 0, TxType.EXPENSE, null))
        db.categoryDao().upsert(Category(2, "Other", "package", 0, TxType.EXPENSE, null))
    }

    @After fun tearDown() = db.close()

    private fun tx(id: Long, category: Long = 1) = Transaction(id, 25_000, TxType.EXPENSE, category, 20_000, "Lunch")

    @Test fun deletingKeepsACopyAndRestoringBringsItBack() = runBlocking {
        repo.saveTransaction(tx(10))
        repo.deleteTransaction(tx(10))
        assertTrue(repo.getAllTransactions().isEmpty())
        assertEquals(listOf(10L), repo.deleted.first().map { it.entry.id })

        repo.restoreDeleted(listOf(10L))
        assertEquals(listOf("Lunch"), repo.getAllTransactions().map { it.note })
        assertTrue(repo.deleted.first().isEmpty())
    }

    @Test fun undoTakesItOutOfDeletedEntries() = runBlocking {
        repo.saveTransaction(tx(11))
        repo.deleteTransaction(tx(11))
        repo.saveTransaction(tx(11)) // what Undo does
        assertEquals(0, repo.deletedCount.first())
    }

    @Test fun restoringWhenTheCategoryIsGoneUsesOther() = runBlocking {
        db.categoryDao().upsert(Category(3, "Hobbies", "gamepad-2", 0, TxType.EXPENSE, null))
        repo.saveTransaction(tx(12, category = 3))
        repo.deleteTransaction(tx(12, category = 3))
        db.categoryDao().delete(Category(3, "Hobbies", "gamepad-2", 0, TxType.EXPENSE, null))

        repo.restoreDeleted(listOf(12L))
        assertEquals("Other", repo.getAllTransactions().single().categoryName)
    }

    @Test fun oldDeletionsAreClearedUnlessKeptForever() = runBlocking {
        val now = System.currentTimeMillis()
        db.deletedDao().upsert(DeletedTransaction.from(tx(13), deletedAt = now - TimeUnit.DAYS.toMillis(40)))
        db.deletedDao().upsert(DeletedTransaction.from(tx(14), deletedAt = now - TimeUnit.DAYS.toMillis(5)))

        repo.purgeDeleted(0, now)
        assertEquals(2, repo.deletedCount.first())

        repo.purgeDeleted(30, now)
        assertEquals(listOf(14L), repo.deleted.first().map { it.entry.id })
    }

    @Test fun deleteForeverRemovesIt() = runBlocking {
        repo.saveTransaction(tx(15))
        repo.deleteTransaction(tx(15))
        repo.deleteForever(listOf(15L))
        assertEquals(0, repo.deletedCount.first())
        assertTrue(repo.getAllTransactions().isEmpty())
    }
}
