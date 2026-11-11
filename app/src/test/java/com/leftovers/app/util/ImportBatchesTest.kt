package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBatchesTest {
    private var id = 0L
    private fun item(createdAt: Long, amount: Long = 100, type: TxType = TxType.EXPENSE) =
        TransactionItem(++id, amount, type, 1, 20_000, "", createdAt, "Food", "", 0, null, null)

    @Test fun anImportIsFoundAcrossASecondBoundary() {
        // An older import saved each entry a few milliseconds apart, over the turn of a second.
        val file = (0 until 30).map { item(1_000_990L + it * 3, if (it == 0) 5_000 else 100, if (it == 1) TxType.INCOME else TxType.EXPENSE) }
        val typed = listOf(item(5_000_000L), item(9_000_000L))
        val found = ImportBatches.find(file + typed)
        assertEquals(1, found.size)
        assertEquals(30, found[0].count)
        assertEquals(5_000L + 28 * 100, found[0].spent)
        assertEquals(100L, found[0].received)
        assertTrue((file + typed).count { ImportBatches.inBatch(it, found[0]) } == 30)
    }

    @Test fun entriesAddedOneByOneAreNotAnImport() {
        val typed = (0 until 40).map { item(it * 60_000L) }
        assertTrue(ImportBatches.find(typed).isEmpty())
    }

    @Test fun twoImportsNewestFirst() {
        val first = (0 until 12).map { item(1_000L + it) }
        val second = (0 until 15).map { item(500_000L + it) }
        val found = ImportBatches.find(first + second)
        assertEquals(listOf(15, 12), found.map { it.count })
    }
}
