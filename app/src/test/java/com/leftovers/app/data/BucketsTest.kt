package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BucketsTest {
    private fun cat(id: Long, name: String, bucket: String? = null) =
        Category(id = id, name = name, emoji = "x", color = 0, type = TxType.EXPENSE, bucket = bucket)

    private fun spend(amount: Long, categoryId: Long) = TransactionItem(
        id = amount, amountMinor = amount, type = TxType.EXPENSE, categoryId = categoryId, epochDay = 0, note = "", createdAt = 0,
        categoryName = "", categoryEmoji = "", categoryColor = 0, accountId = null, receiptPath = null,
    )

    @Test
    fun usualBucketsForUsualNames() {
        assertEquals(Bucket.NEED, Bucket.of(cat(1, "Rent")))
        assertEquals(Bucket.NEED, Bucket.of(cat(2, "Groceries")))
        assertEquals(Bucket.WANT, Bucket.of(cat(3, "Food & Dining")))
        assertEquals(Bucket.SAVE, Bucket.of(cat(4, "Investments")))
        // Moved by the user.
        assertEquals(Bucket.NEED, Bucket.of(cat(3, "Food & Dining", bucket = "NEED")))
    }

    @Test
    fun incomeSplitsFiftyThirtyTwenty() {
        val categories = listOf(cat(1, "Rent"), cat(2, "Shopping"))
        val use = bucketUse(60_000, listOf(spend(18_000, 1), spend(20_000, 2)), categories, savedToGoals = 5_000)
        assertEquals(listOf(30_000L, 18_000L, 12_000L), use.map { it.allowed })
        assertEquals(listOf(18_000L, 20_000L, 5_000L), use.map { it.used })
    }
}
