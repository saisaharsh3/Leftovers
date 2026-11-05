package com.leftovers.app.util

import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Statements from payment apps, laid out as PhonePe exports them. Every name, number and ID here is made up.
 * Each row has the date and time apart, the direction in a type column (or a DEBIT/CREDIT word) and the card or
 * account used in a column of its own, which must never be read as an amount.
 */
class PaymentAppStatementTest {
    private val csv = """
        Transaction Statement for 9000000001
        Duration,"01 Mar, 2026 - 31 Mar, 2026"

        Date,Time,Transaction Details,Transaction ID,UTR,Transaction Type,Credit/debit instrument,Amount
        "Mar 05, 2026","01:03 pm","Paid to GREEN LEAF STORES","T000000000000000000001","100000000001","DEBIT","Paid by XXXX123456","40"
        "Mar 05, 2026","12:02 pm","Received from Anil Kumar","T000000000000000000002","100000000002","CREDIT","Credited to XXXXXX7890","90"
        "Mar 04, 2026","02:31 pm","Paid to Court Booking","T000000000000000000003","100000000003","DEBIT","Paid by XXXX123456","449.37"
        "Mar 03, 2026","10:10 am","Paid to Online Mart","T000000000000000000004","100000000004","DEBIT","Paid by XXXX123456","3923.03"
        "Mar 02, 2026","05:37 am","Transfer to XXXXXX7890","T000000000000000000005","100000000005","DEBIT","Paid by XXXX123456","300"
        "Mar 01, 2026","02:00 pm","Payment to Cloud Storage","OX00000000000000000006","100000000006","DEBIT","Paid by XXXXXX7890","89"

        This is an automatically generated statement. Customer(s) are requested to immediately notify us in case
        of any errors in the statement.
    """.trimIndent()

    @Test fun csvReadsAmountsNotTheCardNumber() {
        val p = CsvImporter.parse(csv)
        assertEquals(listOf(4000L, 9000L, 44937L, 392303L, 8900L), p.rows.map { it.amountMinor })
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME, TxType.EXPENSE, TxType.EXPENSE, TxType.EXPENSE), p.rows.map { it.type })
        assertEquals(LocalDate.of(2026, 3, 5), p.rows[0].date)
        assertEquals("GREEN LEAF STORES", p.rows[0].note)
        assertEquals("Anil Kumar", p.rows[1].note)
        // To the user's own account: a transfer, not spending.
        assertEquals(1, p.transfers)
    }

    // The same statement as the PDF reads: date, then time, then the payment, then its IDs, per block.
    private val pdf = """
        Transaction Statement for 9000000001
        01 Mar, 2026 - 31 Mar, 2026
        Date Transaction Details Type Amount
        Mar 05, 2026
        0103 pm
        Paid to GREEN LEAF STORES DEBIT ₹40
        Transaction ID T000000000000000000001
        UTR No. 100000000001
        Paid by XXXX123456
        Mar 05, 2026
        1202 pm
        Received from Anil Kumar CREDIT ₹90
        Transaction ID T000000000000000000002
        UTR No. 100000000002
        Credited to XXXXXX7890
        Page 1 of 2
        This is a system generated statement. For any queries, contact us.
        Date Transaction Details Type Amount
        Mar 03, 2026
        1010 am
        Paid to Online Mart DEBIT ₹3,923.03
        Transaction ID T000000000000000000004
        UTR No. 100000000004
        Paid by XXXX123456
        Mar 02, 2026
        0537 am
        Transfer to XXXXXX7890 DEBIT ₹300
        Transaction ID T000000000000000000005
        UTR No. 100000000005
        Paid by XXXX123456
        Mar 01, 2026
        0200 pm
        Payment to Cloud Storage DEBIT ₹24,000
        Transaction ID OX00000000000000000006
        UTR No. 100000000006
        Paid by XXXXXX7890
        Page 2 of 2
    """.trimIndent()

    @Test fun pdfReadsEachBlock() {
        val p = PdfStatement.parse(pdf)
        assertEquals(listOf(4000L, 9000L, 392303L, 2400000L), p.rows.map { it.amountMinor })
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME, TxType.EXPENSE, TxType.EXPENSE), p.rows.map { it.type })
        assertEquals(listOf("GREEN LEAF STORES", "Anil Kumar", "Online Mart", "Cloud Storage"), p.rows.map { it.note })
        assertEquals(LocalDate.of(2026, 3, 1), p.rows.last().date)
        assertEquals(1, p.transfers)
    }
}
