package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * More banks' messages (from #7), written the way each bank sends them, with the numbers changed.
 * Each test checks the amount and which way the money went; some check who was paid.
 */
class MoreBankMessagesTest {
    private fun parse(sms: String) = SmsParser.parse(sms)

    private fun spent(minor: Long, sms: String) {
        val p = parse(sms)
        assertEquals(minor, p?.amountMinor)
        assertEquals(false, p?.isIncome)
    }

    private fun received(minor: Long, sms: String) {
        val p = parse(sms)
        assertEquals(minor, p?.amountMinor)
        assertEquals(true, p?.isIncome)
    }

    // Spending.

    @Test fun kotakUpiSent() = spent(25_000L,
        "Sent Rs.250.00 from Kotak Bank AC X1234 to swiggy@ybl on 07-10-26.UPI Ref 612345678901. Not you, https://kotak.com/KBANKT/Fraud",
    )

    @Test fun kotakDebitCard() = spent(1_49_900L,
        "Rs.1499.00 spent on Kotak Debit Card XX1234 at AMAZON on 07/10/2026. Avl bal Rs.12000.00. Not you? Call 18602662666",
    )

    @Test fun bankOfBarodaDebit() = spent(60_000L,
        "Rs.600.00 Dr. from A/C XXXXXX1234 and Cr. to swiggy@icici. Ref:612345678901. AvlBal:Rs12000.00(2026:10:07 10:00:00). Not you? Call 18005700-BOB",
    )

    @Test fun pnbDebit() = spent(35_000L,
        "Your A/c XX1234 debited INR 350.00 on 07-10-26 for UPI txn to ZOMATO. Avl Bal INR 12000.00. Not you? Call 18001800-PNB",
    )

    @Test fun canaraDebit() = spent(1_00_000L,
        "An amount of INR 1,000.00 has been DEBITED to your account XXX1234 on 07/10/2026 towards UPI/DMART. Total Avail.bal INR 12,000.00. -Canara Bank",
    )

    @Test fun unionBankDebit() = spent(20_000L,
        "A/c *1234 Debited for Rs:200.00 on 07-10-2026 10:00:00 by Mob Bk ref no 612345678901 Avl Bal Rs:12000.00.If not you, Call 1800222243 -Union Bank of India",
    )

    @Test fun idfcFirstDebit() = spent(45_000L,
        "Your A/C XXXXXXX1234 is debited by INR 450.00 on 07/10/26 10:00. New Bal :INR 12000.00. UPI ref no 612345678901. Not you? Call 18001080 - IDFC FIRST Bank",
    )

    @Test fun yesBankDebit() = spent(75_000L,
        "INR 750.00 debited from YES BANK A/c XX1234 on 07-OCT-26 10:00:00 for UPI to swiggy@yesbank. Avl Bal INR 12000.00. Not you? Call 18001200",
    )

    @Test fun indusIndDebit() = spent(30_000L,
        "Your IndusInd Bank A/C 2xxxx1234 is debited for INR 300.00 towards UPI/612345678901/ZOMATO on 07/10/2026. Avl Bal: INR 12000.00",
    )

    @Test fun federalBankDebit() = spent(80_000L,
        "Rs 800.00 debited from your A/c XX1234 to VPA swiggy@icici on 07-10-2026 10:00:00. Bal:Rs 12000.00. Not you? Call 18004251199 -Federal Bank",
    )

    @Test fun auBankDebit() = spent(55_000L,
        "Debit INR 550.00 A/c no. XX1234 07-10-2026 10:00:00 UPI/DR/612345678901/BLINKIT. Avl Bal INR 12000.00 -AU Small Finance Bank",
    )

    @Test fun sbiCardSpend() = spent(2_49_900L,
        "Rs.2,499.00 spent on your SBI Credit Card ending 1234 at FLIPKART on 07/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute",
    )

    @Test fun iciciCreditCardSpend() = spent(89_900L,
        "INR 899.00 spent using ICICI Bank Card XX1234 on 07-Oct-26 on MYNTRA. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766",
    )

    @Test fun atmWithdrawal() = spent(5_00_000L,
        "Rs.5000.00 withdrawn at ATM S1AN1234 from A/c XX1234 on 07Oct26. Avl Bal Rs.12000.00. Not you? Call 1800111109 -SBI",
    )

    @Test fun nachEmiAutoDebit() = spent(12_50_000L,
        "Your A/c XX1234 has been debited with INR 12,500.00 on 07-10-2026 towards NACH-DR-HDFC LOAN EMI. Avl Bal: INR 12000.00",
    )

    // Money coming in.

    @Test fun salaryByNeft() = received(75_00_000L,
        "Your A/c XX1234 is credited with INR 75,000.00 on 01-10-2026 by NEFT from ACME TECH PVT LTD. Avl Bal INR 87000.00 -Kotak Bank",
    )

    @Test fun upiReceived() = received(50_000L,
        "Dear UPI user A/C X1234 credited by Rs.500.00 on date 07Oct26 by A/c linked to mobile 9XXXXXX123 (UPI Ref No 612345678901). -SBI",
    )

    @Test fun refundCredited() = received(1_49_900L,
        "Rs.1499.00 credited to your A/c XX1234 on 08-10-26 as refund from AMAZON. Avl Bal Rs.13499.00",
    )

    @Test fun interestCredited() = received(12_345L,
        "Interest of Rs.123.45 has been credited to your A/c XX1234 on 30-09-2026. -Bank of Baroda",
    )

    // Not payments: nothing should be suggested.

    @Test fun declinedIsSkipped() = assertNull(parse(
        "Your transaction of Rs.500.00 at SWIGGY on HDFC Bank Card XX1234 was declined due to insufficient balance.",
    ))

    @Test fun billDueReminderIsSkipped() = assertNull(parse(
        "Your SBI Card statement is generated. Total amount due Rs.12,345.00, minimum due Rs.620.00, payment due by 25-10-2026.",
    ))

    @Test fun balanceOnlyIsSkipped() = assertNull(parse(
        "Avl Bal in your A/c XX1234 as on 07-10-26 is Rs.12000.00. -Canara Bank",
    ))

    // Who was paid.

    @Test fun payeeFromUpiNarration() = assertEquals("Zomato", parse(
        "Your IndusInd Bank A/C 2xxxx1234 is debited for INR 300.00 towards UPI/612345678901/ZOMATO on 07/10/2026. Avl Bal: INR 12000.00",
    )?.merchant)

    @Test fun payeeAfterAt() = assertEquals("Myntra", parse(
        "INR 899.00 spent using ICICI Bank Card XX1234 on 07-Oct-26 on MYNTRA. Avl Limit: INR 1,00,000.00",
    )?.merchant)
}
