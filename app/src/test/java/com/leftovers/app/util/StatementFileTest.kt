package com.leftovers.app.util

import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Statement files in the shapes banks and other apps really save them (#21). */
class StatementFileTest {
    private fun resource(name: String) = javaClass.getResourceAsStream("/statements/$name")!!.readBytes()

    private fun import(bytes: ByteArray): CsvImporter.Parsed = when (val r = StatementFile.read(bytes)) {
        is StatementFile.Result.Text -> CsvImporter.parse(r.text)
        is StatementFile.Result.Table -> CsvImporter.parseTable(r.rows)
        is StatementFile.Result.Unsupported -> error(r.message)
    }

    private val csv = "Date,Description,Amount\n2026-10-01,Coffee,-120.50\n2026-10-02,Salary,50000\n"

    @Test fun utf16WithByteOrderMark() {
        // What Excel's "Unicode text" and some apps save; read as UTF-8 it starts with "��Date" and nothing matched.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + csv.toByteArray(Charsets.UTF_16LE)
        val p = import(bytes)
        assertEquals(2, p.rows.size)
        assertEquals(12_050L, p.rows[0].amountMinor)
        assertEquals(TxType.INCOME, p.rows[1].type)
    }

    @Test fun utf16WithoutMarkAndTabs() {
        val p = import(csv.replace(',', '\t').toByteArray(Charsets.UTF_16LE))
        assertEquals(2, p.rows.size)
    }

    @Test fun utf8WithMark() {
        val p = import(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray())
        assertEquals(2, p.rows.size)
    }

    @Test fun htmlTableSavedAsXls() {
        val html = """
            <html><body><table>
            <tr><td colspan="3">Account statement</td></tr>
            <tr><th>Txn Date</th><th>Particulars</th><th>Debit</th><th>Credit</th></tr>
            <tr><td>05/10/2026</td><td>ATM&nbsp;WDL &amp; fee</td><td>2,000.00</td><td></td></tr>
            <tr><td>06/10/2026</td><td>NEFT salary</td><td></td><td>75,000.00</td></tr>
            </table></body></html>
        """.trimIndent()
        val p = import(html.toByteArray())
        assertEquals(2, p.rows.size)
        assertEquals("ATM WDL & fee", p.rows[0].note)
        assertEquals(TxType.EXPENSE, p.rows[0].type)
        assertEquals(7_500_000L, p.rows[1].amountMinor)
        assertEquals(TxType.INCOME, p.rows[1].type)
    }

    @Test fun xlsxWithDateCellsAndHeaderAfterAccountDetails() {
        val p = import(resource("statement.xlsx"))
        assertEquals(20, p.rows.size)
        assertEquals(LocalDate.of(2026, 9, 1), p.rows[0].date)
        assertEquals(TxType.INCOME, p.rows[0].type)
        assertEquals(500_000L, p.rows[0].amountMinor)
        assertEquals(10_125L, p.rows[1].amountMinor)
        assertEquals(TxType.EXPENSE, p.rows[1].type)
        assertTrue(p.rows[1].note.startsWith("UPI/612345000001/SHOP NUMBER 1"))
    }

    @Test fun oldExcelXlsWithManyRows() {
        // 400 rows: the string table no longer fits one record and goes on in CONTINUE records.
        val p = import(resource("hdfc-like.xls"))
        assertEquals(400, p.rows.size)
        assertEquals(0, p.unreadable)
        assertEquals(LocalDate.of(2026, 9, 1), p.rows[0].date)
        assertEquals(500_000L, p.rows[0].amountMinor)
        assertEquals(TxType.EXPENSE, p.rows[1].type)
        assertEquals("UPI/612345000399/SHOP NUMBER 399 PVT LTD/okaxis", p.rows[399].note)
        // Every third row is a deposit of 5000 + its number; the rest withdraw 100 + 1.25 × its number.
        assertEquals(539_900L, p.rows[399].amountMinor)
        assertEquals(TxType.INCOME, p.rows[399].type)
        assertEquals(59_750L, p.rows[398].amountMinor)
        assertEquals(TxType.EXPENSE, p.rows[398].type)
    }

    @Test fun transfersFromAnyAppAreLeftOut() {
        val p = CsvImporter.parse("Date,Title,Amount,Type\n2026-10-01,Groceries,500,Expense\n2026-10-02,To savings,1000,Transfer\n")
        assertEquals(1, p.rows.size)
        assertEquals(1, p.transfers)
        assertEquals("Groceries", p.rows[0].note)
    }

    @Test fun pdfSaysWhatToDo() {
        val r = StatementFile.read("%PDF-1.7 ...".toByteArray())
        assertTrue(r is StatementFile.Result.Unsupported)
    }
}
