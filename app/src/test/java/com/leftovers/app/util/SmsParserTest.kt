package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    @Test fun builtInWords() {
        val p = SmsParser.parse("Rs.250.00 debited from A/c XX1234 to VPA swiggy@icici on 05-10-26")!!
        assertEquals(25_000L, p.amountMinor)
        assertEquals("Swiggy", p.merchant)
    }

    @Test fun ownKeywordCatchesAnUnusualBank() {
        val msg = "INR 1,499.00 used for txn at CROMA on 07-10-26. Avl bal INR 12,000"
        assertNull(SmsParser.parse(msg))
        val p = SmsParser.parse(msg, listOf("used for"))
        assertNotNull(p)
        assertEquals(149_900L, p!!.amountMinor)
        assertTrue(SmsParser.check(msg, listOf("used for")).reason.contains("used for"))
    }

    @Test fun keywordsDontOverrideSafetyChecks() {
        // An OTP or money coming in is never suggested, whatever the keywords.
        assertNull(SmsParser.parse("OTP for txn of Rs 500 is 123456", listOf("txn")))
        assertNull(SmsParser.parse("Rs 2,000 credited to your account", listOf("account")))
    }

    @Test fun explainsWhyNot() {
        assertTrue(SmsParser.check("Your OTP is 4321").reason.startsWith("Skipped"))
        assertTrue(SmsParser.check("Hello, your parcel is on the way").reason.startsWith("Not a payment"))
        assertTrue(SmsParser.check("Amount debited from your account").reason.startsWith("No amount"))
        assertEquals("Payment", SmsParser.check("Rs 99 spent at Cafe").reason)
    }
}
