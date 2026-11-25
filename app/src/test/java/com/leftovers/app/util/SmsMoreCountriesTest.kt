package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bank messages from #77: Belarusian rubles, and Italian banks. Personal details were already removed by the reporters. */
class SmsMoreCountriesTest {
    @Test fun belarusianRublesAreFound() {
        val p = SmsParser.parse("Karta 4***4242 12-12-26 12:12:12. Oplata 11.97 BYN. BLR SHOP. Balance: 111.89 BYN Tel. 0000000")!!
        assertEquals(1197L, p.amountMinor)
        assertTrue(!p.isIncome)
        assertEquals(115L, SmsParser.parse("Karta 4***4242 12-12-26 12:12:12. Oplata 1.15 BYN. BLR TRANSPORT. Balance: 111.85 BYN Tel. 0000000")!!.amountMinor)
        assertEquals(1350L, SmsParser.parse("Karta 4***4242 12-12-26 12:12:12. Oplata 13.50 BYN. BLR PT KOFEJNYA. Balance: 111.00 BYN Tel. 0000000")!!.amountMinor)
        assertEquals(1197L, MoneyText.find("11.97 Br"))
    }

    @Test fun italianPurchaseTransferAndDeposit() {
        val shop = SmsParser.parse("Deutsche Bank - La informiamo che e' stato eseguito un acquisto di EUR 22,31 il 10/11/26 ore 17:01 con la sua carta *****123")!!
        assertEquals(2231L, shop.amountMinor)
        assertTrue(!shop.isIncome)
        val transfer = SmsParser.parse("Deutsche Bank - La informiamo che in data 21/11/26 alle ore 21:05 e' stato effettuato un bonifico di 153,55 euro tramite online banking")!!
        assertEquals(15355L, transfer.amountMinor)
        assertTrue(!transfer.isIncome)
        val cash = SmsParser.parse("DEUTSCHE BANK S.P.A. VERSAMENTO CONTANTE C/C 092/00123456 IMPORTO 250,00 EURO DATA VALUTA 21/11/2026")!!
        assertEquals(25000L, cash.amountMinor)
        assertTrue(cash.isIncome)
    }

    @Test fun otherCurrenciesByTheirCode() {
        assertEquals(4500L, MoneyText.find("Paid 45.00 KZT at shop"))
        // Georgian lari: "GEL" is also a word, so it's found by its symbol.
        assertEquals(1999L, MoneyText.find("₾19.99 charged"))
        assertEquals(1250L, MoneyText.find("Amount: 12.50 HUF"))
    }

    @Test fun everydayWordsAreNotMoney() {
        // "try 3", "all 5", "top 10" read like a currency code next to a number, but aren't.
        assertNull(MoneyText.find("Please try 3 more times later"))
        assertNull(MoneyText.find("all 5 items shipped"))
        assertNull(MoneyText.find("6 ft cable"))
    }
}
