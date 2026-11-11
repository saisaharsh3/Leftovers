package com.leftovers.app.ui.screens

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchesTest {
    private val lunch = TransactionItem(1, 25_000, TxType.EXPENSE, 1, 20_000, "Lunch with Sam #trip", 0, "Food", "", 0, 2, null)

    @Test fun wordsInAnyOrderAcrossNoteCategoryAndAccount() {
        assertTrue(searchMatches(lunch, "sam lunch", "Card"))
        assertTrue(searchMatches(lunch, "food  card", "Card"))
        assertTrue(searchMatches(lunch, "#trip", null))
        assertFalse(searchMatches(lunch, "lunch dinner", "Card"))
    }

    @Test fun aNumberFindsTheAmount() {
        assertTrue(searchMatches(lunch, "250", null))
        assertTrue(searchMatches(lunch, "food 250.00", null))
        assertFalse(searchMatches(lunch, "251", null))
    }
}
