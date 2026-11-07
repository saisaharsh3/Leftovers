package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up messages in the ways banks, cards, wallets and shops write them. */
class MessageFormatsTest {
    private fun sms(body: String) = SmsParser.parse(body)!!

    @Test fun theBalanceIsNotWhatWasPaid() {
        assertEquals(500_00L, sms("Avl Bal Rs.12,000.00 in A/c XX1234 after debit of Rs.500.00 to SWIGGY on 07-10-26").amountMinor)
        assertEquals(450_00L, sms("Your A/c XX1234 has a balance of Rs 10,000.00. Rs 450.00 spent at DMART on 07-10-26").amountMinor)
        assertEquals(2_000_00L, sms("Transaction of INR 2,000.00 on your Axis Bank Credit Card no. XX1234 at AMAZON on 07-10-26. Available limit INR 50,000.00").amountMinor)
        assertEquals(120_00L, sms("Paid Rs.120 to Chai Point from Paytm Balance. Updated Balance: Rs 1,880").amountMinor)
        assertEquals(1_00_000_00L, sms("Rs.1,00,000.00 debited from A/c XX1234 on 07-10-26 to RAHUL KUMAR. Avl Bal Rs.2,00,000.00").amountMinor)
    }

    @Test fun moreWaysOfSayingPaid() {
        assertEquals("Amazon", sms("Transaction of INR 2,000.00 on your Axis Bank Credit Card no. XX1234 at AMAZON on 07-10-26").merchant)
        assertEquals(1_234_00L, sms("Payment of Rs 1,234.00 towards your AIRTEL bill is successful. Ref 123456789012").amountMinor)
        assertEquals(5_000_00L, sms("Rs.5,000.00 transferred from A/c XX1234 to A/c XX5678 on 07-10-26 via IMPS").amountMinor)
        val card = sms("Your card ending 1234 was charged USD 12.50 at NETFLIX.COM")
        assertEquals(12_50L, card.amountMinor)
        assertEquals("Netflix", card.merchant)
        val upi = sms("You have done a UPI txn. Check details! Amt: Rs.80.00 to LAVANYA on 03-11-26. Ref 612345678901")
        assertEquals(80_00L, upi.amountMinor)
        assertEquals("Lavanya", upi.merchant)
        assertEquals("Netflix", sms("Rs 649.00 auto-debited from A/c XX1234 for NETFLIX mandate on 07-10-26").merchant)
    }

    @Test fun bKash() {
        // Made-up messages in bKash's format (#51).
        val cashOut = sms("Cash Out Tk 500.0 to 01XXXXXXXXX successful. Fee Tk 9.25. Balance Tk 100. TrxID AB12CD34EF at 05/11/2026 23:07.")
        assertEquals(500_00L, cashOut.amountMinor)
        assertEquals(false, cashOut.isIncome)
        assertEquals("", cashOut.merchant)
        val received = sms("You have received Tk 200.00 from 01XXXXXXXXX. Ref 0. Fee Tk 0.00. Balance Tk 212.74. TrxID AB12CD34EF at 05/11/2026 22:56")
        assertEquals(200_00L, received.amountMinor)
        assertEquals(true, received.isIncome)
        val payment = sms("Payment of Tk. 100to Merchant is successful. Balance Tk 46.72. TrxID AB12CD34EF at 23/10/2026 22:14")
        assertEquals(100_00L, payment.amountMinor)
        assertEquals("Merchant", payment.merchant)
        val sent = sms("Send Money Tk 200.00 to 01XXXXXXXXX successful. Ref 1. Fee Tk 0.00. Balance Tk 3.72. TrxID AB12CD34EF at 21/10/2026 20:58")
        assertEquals(200_00L, sent.amountMinor)
        assertEquals(false, sent.isIncome)
    }

    @Test fun offersAreNotPayments() {
        assertNull(SmsParser.parse("Get up to Rs 500 cashback on your next recharge! Use code SAVE. T&C apply"))
        assertTrue(SmsParser.check("Flat 50% off on orders above Rs 299. Order now").reason.startsWith("Skipped"))
        // Money that moved still counts, offer words or not.
        assertEquals(true, SmsParser.parse("Rs 100 cashback credited to your wallet for the offer")?.isIncome)
    }

    @Test fun receiptsTakeTheTotalPaid() {
        val order = EmailParser.parse("Your order", "Shop Orders", "Item total: ₹1,000.00\nSubtotal ₹1,000.00\nDiscount -₹200.00\nTotal ₹800.00\nPaid via UPI")!!
        assertEquals(800_00L, order.amountMinor)
        val ride = EmailParser.parse("Your receipt from Ride Co", "Ride Co", "Trip fare ₹230.00 Taxes ₹15.50 Total ₹245.50. Charged to UPI")!!
        assertEquals(245_50L, ride.amountMinor)
        // "Charged to UPI" names how it was paid, not who: the sender is.
        assertEquals("Ride Co", ride.merchant)
        assertEquals(129_00L, EmailParser.parse("Payment receipt", "Play", "Pro plan ₹129.00/month. Total: ₹129.00")!!.amountMinor)
        assertEquals(560_00L, EmailParser.parse("Order delivered", "Food", "Item total ₹600.00 Total savings ₹40.00 Grand Total ₹560.00 paid")!!.amountMinor)
    }
}
