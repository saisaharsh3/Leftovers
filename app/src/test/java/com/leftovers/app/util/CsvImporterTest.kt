package com.leftovers.app.util

import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvImporterTest {
    @Test fun leftoversOwnExport() {
        val p = CsvImporter.parse(
            """
            Date,Type,Category,Amount,Note
            2026-10-06,Expense,Food & Dining,150.00,"Lunch, with team"
            2026-10-01,Income,Salary,75000,Salary
            """.trimIndent(),
        )
        assertEquals(2, p.rows.size)
        assertEquals(CsvImporter.Row(LocalDate.of(2026, 10, 6), 15_000, TxType.EXPENSE, "Food & Dining", "Lunch, with team", null), p.rows[0])
        assertEquals(TxType.INCOME, p.rows[1].type)
        assertEquals(7_500_000L, p.rows[1].amountMinor)
    }

    @Test fun indianBankStatementWithDebitAndCredit() {
        val p = CsvImporter.parse(
            """
            Account Statement for XX1234
            Txn Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance
            05/10/26,UPI-SWIGGY-swiggy@icici,"1,250.00",,45000.00
            07/10/26,SALARY ACME,,"75,000.00",120000.00
            """.trimIndent(),
        )
        assertEquals(2, p.rows.size)
        assertEquals(LocalDate.of(2026, 10, 5), p.rows[0].date)
        assertEquals(125_000L, p.rows[0].amountMinor)
        assertEquals(TxType.EXPENSE, p.rows[0].type)
        assertEquals("UPI-SWIGGY-swiggy@icici", p.rows[0].note)
        assertEquals(TxType.INCOME, p.rows[1].type)
    }

    @Test fun signedAmountsAndSemicolons() {
        val p = CsvImporter.parse(
            """
            Date;Description;Amount
            10.10.2026;Coffee;-3,50
            11.10.2026;Refund;12,00
            """.trimIndent(),
        )
        assertEquals(350L, p.rows[0].amountMinor)
        assertEquals(TxType.EXPENSE, p.rows[0].type)
        assertEquals(TxType.INCOME, p.rows[1].type)
        assertEquals(LocalDate.of(2026, 10, 11), p.rows[1].date)
    }

    @Test fun expensesOnlyExportWithPositiveAmounts() {
        val p = CsvImporter.parse("date,category,amount,note\n2026-09-01,Groceries,420,Milk\n2026-09-02,Transport,90,Metro")
        assertEquals(listOf(TxType.EXPENSE, TxType.EXPENSE), p.rows.map { it.type })
    }

    @Test fun usDatesAndMonthNames() {
        assertEquals(LocalDate.of(2026, 10, 25), CsvImporter.parse("Date,Amount\n10/25/2026,-5\n10/03/2026,-6").rows[0].date)
        assertEquals(LocalDate.of(2026, 10, 10), CsvImporter.parseDate("10 Oct 2026"))
        assertEquals(LocalDate.of(2026, 10, 10), CsvImporter.parseDate("10-Oct-26"))
    }

    @Test fun amounts() {
        assertEquals(125_050L, CsvImporter.parseAmount("₹1,250.50"))
        assertEquals(-30_000L, CsvImporter.parseAmount("(300)"))
        assertEquals(123_456L, CsvImporter.parseAmount("1.234,56"))
        assertNull(CsvImporter.parseAmount("—"))
    }

    @Test fun unreadableRowsAreCounted() {
        val p = CsvImporter.parse("Date,Amount\n2026-10-01,-50\nnot a date,-20\n2026-10-02,")
        assertEquals(1, p.rows.size)
        assertEquals(2, p.unreadable)
    }

    @Test fun noDateColumnExplainsWhy() = assertNotNull(CsvImporter.parse("Name,Amount\nFoo,1").error)
}
