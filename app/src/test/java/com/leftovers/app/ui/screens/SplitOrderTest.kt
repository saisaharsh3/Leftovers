package com.leftovers.app.ui.screens

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitOrderTest {
    private fun tx(id: Long, splitOf: Long? = null) = TransactionItem(
        id = id, amountMinor = 100, type = TxType.EXPENSE, categoryId = 1, epochDay = 0, note = "", createdAt = 0,
        categoryName = "", categoryEmoji = "", categoryColor = 0, accountId = null, receiptPath = null, splitOf = splitOf,
    )

    @Test
    fun partsFollowTheEntryTheyCameFrom() {
        // Newest first, as Activity lists them: the parts were saved after their entry.
        val items = listOf(tx(5), tx(4, splitOf = 2), tx(3, splitOf = 2), tx(2), tx(1))
        assertEquals(listOf(5L, 2L, 4L, 3L, 1L), splitOrder(items).map { it.id })
        // A part whose entry isn't in the list (deleted, another day) stays where it is.
        assertEquals(listOf(4L, 1L), splitOrder(listOf(tx(4, splitOf = 9), tx(1))).map { it.id })
    }
}
