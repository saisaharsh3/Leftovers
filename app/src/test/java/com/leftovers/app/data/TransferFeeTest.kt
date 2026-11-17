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

/** A transfer fee leaves the From account with the amount, and never reaches the To account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransferFeeTest {
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

    @Test fun feeLeavesTheFromAccountOnly() = runBlocking {
        db.transferDao().upsert(Transfer(fromAccountId = 1, toAccountId = 2, amountMinor = 25_000, feeMinor = 500, epochDay = 20_000, note = "Top up"))
        assertEquals(74_500L, balance(1))
        assertEquals(25_000L, balance(2))
    }

    @Test fun changingOrDeletingTheFeeMovesTheBalance() = runBlocking {
        db.transferDao().upsert(Transfer(fromAccountId = 1, toAccountId = 2, amountMinor = 25_000, feeMinor = 500, epochDay = 20_000))
        val saved = db.transferDao().getAll().single()
        db.transferDao().upsert(saved.copy(feeMinor = 1_000))
        assertEquals(74_000L, balance(1))
        db.transferDao().delete(saved.copy(feeMinor = 1_000))
        assertEquals(100_000L, balance(1))
        assertEquals(0L, balance(2))
    }
}
