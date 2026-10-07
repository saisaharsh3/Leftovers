package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTextTest {
    @Test fun currencyBeforeTheNumber() {
        assertEquals(25_000L, MoneyText.find("Rs.250.00 debited"))
        assertEquals(125_050L, MoneyText.find("₹ 1,250.50 spent"))
        assertEquals(1_250L, MoneyText.find("You paid $12.50 at Starbucks"))
        assertEquals(4_500L, MoneyText.find("AED 45 spent on card"))
        assertEquals(3_000L, MoneyText.find("RM30.00 debited"))
        assertEquals(99_900L, MoneyText.find("Purchase of US$999 approved"))
    }

    @Test fun currencyAfterTheNumber() {
        assertEquals(1_250L, MoneyText.find("Card charged 12.50 USD"))
        assertEquals(4_599L, MoneyText.find("Betrag 45,99 EUR abgebucht"))
        assertEquals(15_000L, MoneyText.find("150 kr dragits"))
    }

    @Test fun thousandsAndDecimalStyles() {
        assertEquals(5_000_000L, MoneyText.find("Rp 50.000 dibayar"))
        assertEquals(123_456L, MoneyText.find("€1.234,56 charged"))
        assertEquals(123_456L, MoneyText.find("£1,234.56 charged"))
    }

    @Test fun wordsThatLookLikeCodesAreNotAmounts() {
        assertNull(MoneyText.find("Please confirm 30 items"))
        assertNull(MoneyText.find("Account XX1234 updated"))
        assertNull(MoneyText.find("Please try 3 times"))
    }

    @Test fun firstAmountWinsOverTheBalance() {
        assertEquals(25_000L, MoneyText.find("Rs 250 debited. Avl bal Rs 12,000"))
    }
}
