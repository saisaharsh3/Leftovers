package com.leftovers.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A subscription already logged some other way (by hand, or from its SMS) isn't logged a second time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionTwiceTest {
    private lateinit var db: AppDatabase
    private lateinit var planning: PlanningRepository
    private val today = LocalDate.of(2026, 11, 10)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        planning = PlanningRepository(db)
        runBlocking {
            db.categoryDao().upsert(Category(id = 7, name = "Subscriptions", emoji = "tv", color = 0, type = TxType.EXPENSE))
            db.recurringDao().upsert(Recurring(name = "Claude", amountMinor = 1_700_00, type = TxType.EXPENSE, categoryId = 7, dayOfMonth = 9, startMonth = "2026-11"))
        }
    }

    @After fun tearDown() = db.close()

    private suspend fun entries() = db.transactionDao().getAll()

    @Test fun loggedByHandTheDayBefore() = runBlocking {
        db.transactionDao().upsert(Transaction(amountMinor = 1_700_00, type = TxType.EXPENSE, categoryId = 7, epochDay = today.minusDays(2).toEpochDay(), note = "Anthropic"))
        planning.postDueRecurring(null, today)
        assertEquals(1, entries().size)
        // And it counts as this month's: it isn't logged on the next run either.
        planning.postDueRecurring(null, today)
        assertEquals(1, entries().size)
    }

    @Test fun anotherPaymentDoesntStopIt() = runBlocking {
        db.transactionDao().upsert(Transaction(amountMinor = 250_00, type = TxType.EXPENSE, categoryId = 7, epochDay = today.minusDays(1).toEpochDay(), note = "Lunch"))
        planning.postDueRecurring(null, today)
        assertEquals(2, entries().size)
    }
}
