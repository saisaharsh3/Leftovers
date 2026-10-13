package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {
    @Test fun paymentsGoingOut() {
        assertEquals(SmsParser.Parsed(250_00, "Swiggy"), NotificationParser.parse("₹250 paid to Swiggy", "Payment successful"))
        assertEquals(SmsParser.Parsed(1_200_00, "Arjun Kumar"), NotificationParser.parse("Payment successful", "Paid ₹1,200 to Arjun Kumar"))
        assertEquals(SmsParser.Parsed(99_50, "Blinkit"), NotificationParser.parse("FamApp", "You paid ₹99.50 to Blinkit using FamApp"))
    }

    @Test fun moneyComingIn() {
        assertEquals(SmsParser.Parsed(500_00, "Priya", isIncome = true), NotificationParser.parse("Priya sent you ₹500", "Tap to view"))
        assertEquals(SmsParser.Parsed(300_00, "Rahul S", isIncome = true), NotificationParser.parse("Money received", "Rahul S paid you ₹300"))
        assertEquals(SmsParser.Parsed(2_000_00, "Nick", isIncome = true), NotificationParser.parse("Received ₹2,000", "from Nick on UPI"))
    }

    @Test fun offersRemindersAndRequestsAreSkipped() {
        assertNull(NotificationParser.parse("Get ₹100 cashback!", "On your first payment with Paytm"))
        assertNull(NotificationParser.parse("Your electricity bill of ₹1,240 is due", "Pay now to avoid late fees"))
        assertNull(NotificationParser.parse("Arjun requested ₹200", "Tap to pay"))
        assertNull(NotificationParser.parse("Payment failed", "₹250 to Swiggy could not be completed"))
        assertNull(NotificationParser.parse("Win up to ₹500", "Scratch card waiting"))
        assertNull(NotificationParser.parse("Recharge now", "Plans from ₹199"))
    }

    @Test fun noAmountNoPayment() {
        assertNull(NotificationParser.parse("Security update", "Your app is now on the latest version"))
        assertNull(NotificationParser.parse("", ""))
    }
}
