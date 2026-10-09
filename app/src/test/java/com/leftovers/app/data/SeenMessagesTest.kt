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

/** Email checks look back two days, so a message must not come back once its suggestion is added or dismissed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SeenMessagesTest {
    private lateinit var db: AppDatabase
    private lateinit var sms: SmsRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        sms = SmsRepository(db.smsDao(), db.transactionDao())
    }

    @After fun tearDown() = db.close()

    private fun suggestion(hash: String, amount: Long = 25_000, at: Long = 1_000_000L, income: Boolean = false) =
        SmsSuggestion(amountMinor = amount, merchant = "Swiggy", sender = "HDFC", epochDay = 20_000, body = hash, receivedAt = at, isIncome = income)

    @Test fun dismissedMessageDoesNotComeBack() = runBlocking {
        val hash = "a".repeat(64)
        assertTrue(sms.add(suggestion(hash)))
        sms.dismiss(sms.suggestions.first().single().id)
        assertFalse(sms.add(suggestion(hash)))
        assertTrue(sms.suggestions.first().isEmpty())
    }

    @Test fun incomeAndExpenseOfTheSameAmountAreBothKept() = runBlocking {
        assertTrue(sms.add(suggestion("b".repeat(64))))
        assertTrue(sms.add(suggestion("c".repeat(64), income = true)))
        assertEquals(2, sms.suggestions.first().size)
        assertTrue(sms.isIncome(sms.suggestions.first().first { it.body == "c".repeat(64) }.id))
    }
}
