package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Messages written the way Indian banks send them (numbers changed). Each one is a real payment. */
class RealBankMessagesTest {
    private fun amount(sms: String) = SmsParser.parse(sms)?.amountMinor

    @Test fun hdfcUpi() = assertEquals(50_000L, amount(
        "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 07/10/26\nRef 612345678901\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
    ))

    @Test fun sbiWithoutCurrency() = assertEquals(50_000L, amount(
        "Dear UPI user A/C X1234 debited by 500.0 on date 07Oct26 trf to SWIGGY Refno 612345678901. If not u? call 1800111109. -SBI",
    ))

    @Test fun iciciDebitedForRs() = assertEquals(50_000L, amount(
        "ICICI Bank Acct XX234 debited for Rs 500.00 on 07-Oct-26; SWIGGY credited. UPI:612345678901. Call 18002662 for dispute. SMS BLOCK 234 to 9215676766.",
    ))

    @Test fun axisInr() = assertEquals(50_000L, amount(
        "INR 500.00 debited\nA/c no. XX1234\n07-10-26, 10:00:00\nUPI/P2M/612345678901/SWIGGY\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
    ))

    @Test fun cardThankYouForUsing() = assertEquals(50_000L, amount(
        "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 500.00 at SWIGGY on 2026-10-07:10:00:00. Authorization code:- 123456",
    ))

    @Test fun footerMentioningOtpStillCounts() = assertEquals(50_000L, amount(
        "Rs.500.00 debited from A/c XX1234 to SWIGGY on 07-10-26. Never share your OTP, PIN or CVV with anyone.",
    ))

    @Test fun realOtpIsSkipped() {
        assertNull(amount("123456 is your OTP for a transaction of Rs 500.00 at SWIGGY. Do not share it with anyone."))
        assertNull(amount("Your one time password for txn of Rs 500 is 4321"))
    }

    @Test fun collectRequestIsSkipped() =
        assertNull(amount("SWIGGY has requested money from you on Google Pay. Rs 500.00 will be debited on approval."))

    @Test fun emailFooterSayingRequestedStillCounts() {
        val p = EmailParser.parse(
            "Alert: Account debited", "HDFC Bank InstaAlerts",
            "Dear Customer, Rs.500.00 has been debited from account **1234 to VPA swiggy@icici SWIGGY on 07-10-26. " +
                "If you have not requested this transaction, please call 18002586161. Never share your OTP or PIN.",
        )
        assertEquals(50_000L, p?.amountMinor)
    }

    @Test fun accountDigits() {
        assertEquals("1234", SmsParser.accountDigits("Sent Rs.500.00 From HDFC Bank A/C *1234 To SWIGGY"))
        assertEquals("1234", SmsParser.accountDigits("Dear UPI user A/C X1234 debited by 500.0"))
        assertEquals("234", SmsParser.accountDigits("ICICI Bank Acct XX234 debited for Rs 500.00"))
        assertEquals("1234", SmsParser.accountDigits("HDFC Bank Credit Card ending 1234 for Rs 500.00"))
        assertNull(SmsParser.accountDigits("Rs 500 spent at Swiggy"))
    }

    private fun merchant(sms: String) = SmsParser.parse(sms)?.merchant

    @Test fun merchantStopsAtRefno() = assertEquals("ZOMATO", merchant(
        "Dear UPI user A/C X4321 debited by 500.0 on date 08Oct26 trf to ZOMATO Refno 123456789. If not u? call 1800111109. -SBI",
    ))

    @Test fun merchantStopsAtBracket() = assertEquals("Swiggy", merchant(
        "Rs.250.00 debited from a/c **1234 on 08-10-26 to VPA swiggy@icici (UPI Ref No 4321). Not you? Call 18002586161",
    ))
}
