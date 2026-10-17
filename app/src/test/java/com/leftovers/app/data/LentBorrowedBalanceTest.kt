package com.leftovers.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lent and borrowed money moves an account's balance while it's open (#19), against a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LentBorrowedBalanceTest {
    private lateinit var db: AppDatabase
    private lateinit var accounts: AccountRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        accounts = AccountRepository(db.accountDao(), db.transferDao(), db.transactionDao(), db.debtDao())
        runBlocking {
            db.accountDao().upsert(Account(1, "Cash", "banknote", 0, openingMinor = 100_000))
            db.accountDao().upsert(Account(2, "Bank", "landmark", 0, openingMinor = 0))
        }
    }

    @After fun tearDown() = db.close()

    private suspend fun balance(id: Long) = accounts.accounts.first().first { it.id == id }.balanceMinor

    @Test fun lendingTakesMoneyOutUntilSettled() = runBlocking {
        db.debtDao().upsert(Debt(10, "Nick", 30_000, accountId = 1))
        assertEquals(70_000L, balance(1))
        db.debtDao().settle("Nick")
        assertEquals(100_000L, balance(1))
    }

    @Test fun borrowingPutsMoneyIn() = runBlocking {
        db.debtDao().upsert(Debt(11, "Priya", -20_000, accountId = 1))
        assertEquals(120_000L, balance(1))
    }

    @Test fun entriesWithoutAnAccountChangeNoBalance() = runBlocking {
        db.debtDao().upsert(Debt(12, "Arjun", 50_000))
        assertEquals(100_000L, balance(1))
        assertEquals(0L, balance(2))
    }

    @Test fun movingEntriesMovesLentMoneyToo() = runBlocking {
        db.debtDao().upsert(Debt(13, "Nick", 30_000, accountId = 1))
        val moved = accounts.moveEntries(1, 2)
        assertEquals(listOf(13L), moved.debts)
        assertEquals(100_000L, balance(1))
        assertEquals(-30_000L, balance(2))
        accounts.setAccount(moved, 1)
        assertEquals(70_000L, balance(1))
    }

    @Test fun deletingAnAccountKeepsItsEntriesInMoneyOwed() = runBlocking {
        db.debtDao().upsert(Debt(14, "Nick", 30_000, accountId = 2))
        assertEquals(0, accounts.deleteAccount(Account(2, "Bank", "landmark", 0)))
        assertEquals(listOf<Long?>(null), db.debtDao().getAll().map { it.accountId })
    }
}
