package com.leftovers.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import java.time.YearMonth

/** A one-off scheduled for a later day (#68): set aside until then, added on its day, then gone. Made-up data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduledEntryTest {
    private lateinit var db: AppDatabase
    private lateinit var planning: PlanningRepository
    private val dec5 = LocalDate.of(2026, 12, 5)
    private val tyres = Recurring(name = "New tyres", amountMinor = 240_00, type = TxType.EXPENSE, categoryId = 3, dayOfMonth = 5, startMonth = "2026-12", everyMonths = 0)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        planning = PlanningRepository(db)
        runBlocking {
            db.categoryDao().upsert(Category(id = 3, name = "Transport", emoji = "car", color = 0, type = TxType.EXPENSE))
            db.recurringDao().upsert(tyres)
        }
    }

    @After fun tearDown() = db.close()

    @Test fun itsDueOnlyInItsMonth() {
        assertTrue(tyres.once)
        assertTrue(tyres.isDueIn(YearMonth.of(2026, 12)))
        assertFalse(tyres.isDueIn(YearMonth.of(2027, 1)))
        assertFalse(tyres.isDueIn(YearMonth.of(2026, 11)))
        assertEquals(dec5, tyres.nextChargeDate(LocalDate.of(2026, 11, 20)))
    }

    @Test fun itsSetAsideFromThatMonthsBudget() {
        val item = RecurringItem(1, "New tyres", 240_00, TxType.EXPENSE, 3, 5, "2026-12", null, true, 0, null, "Transport", "car", 0)
        assertEquals(240_00L, listOf(item).pendingExpenses(YearMonth.of(2026, 12)))
        assertEquals(0L, listOf(item).pendingExpenses(YearMonth.of(2026, 11)))
    }

    @Test fun nothingBeforeItsDayThenAddedOnceAndGone() = runBlocking {
        planning.postDueRecurring(null, dec5.minusDays(1))
        assertTrue(db.transactionDao().getAll().isEmpty())
        planning.postDueRecurring(null, dec5)
        val added = db.transactionDao().getAll().single()
        assertEquals(240_00L, added.amountMinor)
        assertEquals(dec5.toEpochDay(), added.epochDay)
        assertTrue(db.recurringDao().getActive().isEmpty())
        // Later runs add nothing more.
        planning.postDueRecurring(null, dec5.plusMonths(2))
        assertEquals(1, db.transactionDao().getAll().size)
    }

    @Test fun addedNowInsteadOfOnItsDay() = runBlocking {
        val r = db.recurringDao().getActive().single()
        planning.addScheduledNow(r, LocalDate.of(2026, 11, 20))
        assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), db.transactionDao().getAll().single().epochDay)
        assertTrue(db.recurringDao().getActive().isEmpty())
    }
}
