package com.leftovers.app.util

import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantMemoryTest {
    private fun tx(note: String, category: Long, day: Long, type: TxType = TxType.EXPENSE) =
        MerchantMemory.Past(note, type, category, day, day)

    @Test fun samePlaceWhateverTheSpelling() {
        val history = listOf(tx("Swiggy dinner", 3, 10), tx("Metro", 5, 11), tx("SWIGGY", 4, 12))
        // The latest Swiggy entry wins.
        assertEquals(4L, MerchantMemory.categoryFor("SWIGGY*ORDER 8812", TxType.EXPENSE, history))
        assertEquals(5L, MerchantMemory.categoryFor("metro", TxType.EXPENSE, history))
    }

    @Test fun onlyTheSameKindAndRealMatches() {
        val history = listOf(tx("Salary", 9, 1, TxType.INCOME), tx("Amazon", 2, 2))
        assertNull(MerchantMemory.categoryFor("Salary", TxType.EXPENSE, history))
        assertNull(MerchantMemory.categoryFor("Zomato", TxType.EXPENSE, history))
        assertNull(MerchantMemory.categoryFor("AB", TxType.EXPENSE, listOf(tx("AB", 1, 1))))
        assertEquals(9L, MerchantMemory.categoryFor("SALARY CREDIT", TxType.INCOME, history))
    }
}
