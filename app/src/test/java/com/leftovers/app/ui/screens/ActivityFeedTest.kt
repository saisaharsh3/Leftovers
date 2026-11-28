package com.leftovers.app.ui.screens

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ActivityFeedTest {
    @Test
    fun transfersAreInterleavedWithEntriesByEntryTime() {
        val day = LocalDate.of(2026, 11, 26).toEpochDay()
        val entries = listOf(
            TransactionItem(1, 100, TxType.EXPENSE, 1, day, "First", 100, "Food", "", 0, 1, null),
            TransactionItem(2, 200, TxType.EXPENSE, 1, day, "Last", 300, "Food", "", 0, 1, null),
        )
        val transfer = TransferLine(
            Transfer(1, 1, 2, 150, day, createdAt = 200),
            "Cash",
            "Bank",
            0,
        )

        val feed = activityFeed(entries, emptyMap(), oneBill = false, transfers = listOf(transfer))

        assertEquals(listOf(300L, 200L, 100L), feed.map { it.createdAt })
        assertEquals(ActivityFeedRow.Transfer(transfer), feed[1])
    }
}
