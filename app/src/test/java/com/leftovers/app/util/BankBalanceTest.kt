package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The balance a bank message reports, in many banks' wordings. Made-up messages and numbers. */
class BankBalanceTest {
    @Test
    fun `the usual ways banks write the balance`() {
        assertEquals(1_200_050L, BankBalance.read("Rs.500.00 debited from A/c XX1234 on 11-11-26. Avl Bal Rs.12,000.50 -HDFC Bank"))
        assertEquals(12_345_678L, BankBalance.read("INR 2,000.00 credited to A/c no. XX9876. Available Balance: INR 1,23,456.78"))
        assertEquals(200_000L, BankBalance.read("Dear Customer, your A/c bal is Rs 2,000.00 as on 11-Nov-26"))
        assertEquals(450_000L, BankBalance.read("A/c *4321 debited Rs 99.00. Avbl Bal INR 4,500.00. Not you? Call 1800"))
        assertEquals(87_550L, BankBalance.read("Your a/c XX1111 is credited with Rs.250. Total Bal:Rs.875.50 CR"))
        assertEquals(1_000_000L, BankBalance.read("Clr Bal Rs 10,000.00 in your account XX55"))
        assertEquals(300_000L, BankBalance.read("Sent Rs.40 from Kotak Bank AC X1234. Bal: Rs 3,000.00"))
        assertEquals(51_000L, BankBalance.read("txn of ₹120 at Swiggy. Avl bal ₹510"))
    }

    @Test
    fun `an overdrawn balance is negative`() {
        assertEquals(-25_000L, BankBalance.read("Avl Bal Rs.250.00 DR"))
        assertEquals(-25_000L, BankBalance.read("Available balance: Rs -250.00"))
    }

    @Test
    fun `a card's limit or what's owed on it isn't a balance`() {
        assertNull(BankBalance.read("Rs 1,200 spent on Credit Card XX4321. Avl Limit Rs 48,800.00"))
        assertNull(BankBalance.read("Total outstanding bal Rs 12,000.00, min amount due Rs 600 by 05-Dec"))
        assertNull(BankBalance.read("Your statement balance Rs 9,876.00 is due on 20 Nov"))
    }

    @Test
    fun `messages without a balance give none`() {
        assertNull(BankBalance.read("Rs.500.00 debited from A/c XX1234 on 11-11-26 to VPA swiggy@icici. UPI Ref 412345678901"))
        assertNull(BankBalance.read("123456 is your OTP for login. Do not share it."))
        assertNull(BankBalance.read("Balance enquiry service is down tonight, sorry"))
        assertNull(BankBalance.read(""))
    }
}
