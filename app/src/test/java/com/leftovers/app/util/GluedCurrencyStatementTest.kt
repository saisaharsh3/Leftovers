package com.leftovers.app.util

import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A bank statement PDF that prints the currency glued to the amount, with a sign before it ("+ PKR1,000.00",
 * "- PKR90.00") and a running balance. Names and numbers are made up; the layout is the point.
 */
class GluedCurrencyStatementTest {
    private val text = """
        Account Statement
        Booking Date Description Credit Debit Available Balance
        16 Sep 2025 Cash Deposit 1234567 + PKR1,000.00 PKR1,000.00
        24 Sep 2025 Money Received from + PKR129,000.00 PKR130,000.00
        Test Person One
        24 Sep 2025 ATM Cash Withdrawal - PKR100,000.00 PKR30,000.00
        Main Branch
        24 Sep 2025 POS Purchase - PKR90.00 PKR29,910.00
        Corner Shop
        1 23 Sep 2026, 13:18
        28 Sep 2025 Money Transferred to - PKR11,500.00 PKR18,410.00
        Test Person Two
    """.trimIndent()

    @Test fun readsAmountsWithTheCurrencyStuckToThem() {
        val p = PdfStatement.parse(text)
        assertEquals(listOf(1_000_00L, 129_000_00L, 100_000_00L, 90_00L, 11_500_00L), p.rows.map { it.amountMinor })
        assertEquals(listOf(TxType.INCOME, TxType.INCOME, TxType.EXPENSE, TxType.EXPENSE, TxType.EXPENSE), p.rows.map { it.type })
        assertEquals(LocalDate.of(2025, 9, 16), p.rows.first().date)
    }
}
