package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which account a bank message goes to: by its last digits, else by the account's name used as an account. */
class AccountMatchTest {
    private fun account(id: Long, name: String, digits: String? = null) =
        AccountWithBalance(id, name, "wallet", 0, 0, 0, digits, 0)

    private val cash = account(1, "Cash")
    private val hbl = account(2, "HBL")
    private val hdfc = account(3, "HDFC Bank", digits = "1234")
    private val accounts = listOf(cash, hbl, hdfc)

    // From #6.
    @Test fun bankNameBeforeAc() = assertEquals(hbl, accounts.matchFor(
        "PKR 442543.15 received from AL RAJHI B MCB in your HBL A/C on 19/09/2026 07:54:42",
    ))

    @Test fun digitsComeFirst() = assertEquals(hdfc, accounts.matchFor("Rs 250 debited from HBL card XX1234"))

    @Test fun nameAloneIsNotAnAccount() = assertNull(accounts.matchFor("Cash withdrawal of Rs 500 at ATM"))

    @Test fun inYourName() = assertEquals(hbl, accounts.matchFor("Rs 5,000 credited to your HBL on 09-10-26"))

    @Test fun bankWordAfterName() = assertEquals(hdfc, accounts.matchFor("Rs 90 spent on HDFC Bank Credit Card at Uber"))

    // From #6: an account called "HBL Bank" should also catch "your HBL A/C".
    @Test fun extraWordsInTheAccountName() = assertEquals(account(5, "HBL Bank"), listOf(cash, account(5, "HBL Bank")).matchFor(
        "PKR 442543.15 received from AL RAJHI B MCB in your HBL A/C on 19/09/2026",
    ))

    @Test fun savingsAccountName() = assertEquals(account(6, "Meezan Savings"), listOf(account(6, "Meezan Savings")).matchFor("Rs 100 credited to your Meezan account"))

    @Test fun hintNamesTheMessagesAccount() {
        assertEquals("HBL", namedAccountIn("PKR 442543.15 received from AL RAJHI B MCB in your HBL A/C on 19/09/2026"))
        assertNull(namedAccountIn("Rs 250 debited from A/c XX1234 to VPA swiggy@icici"))
    }

    @Test fun twoNamesIsUnclear() = assertNull(listOf(cash, hbl, account(4, "Meezan")).matchFor("Rs 100 from your HBL A/C to your Meezan account"))
}
