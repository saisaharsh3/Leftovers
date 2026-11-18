package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class OfficeFilesTest {
    private var id = 1L
    private fun entry(day: String, amount: Long, category: String, type: TxType = TxType.EXPENSE) = TransactionItem(
        id = id++, amountMinor = amount, type = type, categoryId = category.hashCode().toLong(), epochDay = LocalDate.parse(day).toEpochDay(),
        note = "a note", createdAt = 0, categoryName = category, categoryEmoji = "", categoryColor = 0, accountId = 1, receiptPath = null,
    )

    @Test fun workbookReadsBackAsTheSameTable() {
        val bytes = XlsxWriter.write(listOf(Sheet("Entries", listOf("Date", "Amount"), listOf(
            listOf(Cell.Text("2026-11-03"), Cell.Number(BigDecimal("-12.50"))),
            listOf(Cell.Text("Tea & <biscuits>"), Cell.Number(BigDecimal("2500.00"))),
        ))))
        val rows = StatementFile.readXlsx(bytes)
        assertEquals(listOf("Date", "Amount"), rows[0])
        assertEquals("2026-11-03", rows[1][0])
        assertEquals(BigDecimal("-12.5"), BigDecimal(rows[1][1]).stripTrailingZeros())
        assertEquals("Tea & <biscuits>", rows[2][0])
    }

    @Test fun workbookHasItsThreeSheets() {
        val items = listOf(entry("2026-10-02", 1_000, "Food"), entry("2026-11-05", 2_500, "Food"), entry("2026-11-06", 300_000, "Salary", TxType.INCOME))
        val bytes = Reports.workbook(items, mapOf(1L to "Bank"), notes = false)
        val names = java.util.zip.ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertTrue("xl/worksheets/sheet3.xml" in names)
        // Notes stay out unless asked for.
        assertTrue(!String(bytes, Charsets.ISO_8859_1).contains("a note"))
    }

    @Test fun columnsAndSheetNames() {
        assertEquals("A", XlsxWriter.column(0))
        assertEquals("Z", XlsxWriter.column(25))
        assertEquals("AA", XlsxWriter.column(26))
        assertEquals("Oct  Nov", XlsxWriter.sheetName("Oct/[Nov]", 0))
        assertEquals("Sheet2", XlsxWriter.sheetName("  ", 1))
    }

    @Test fun documentReadsBackAsItsText() {
        val bytes = DocxWriter.write("Report", listOf(
            DocBlock.Heading("Where it went"),
            DocBlock.Paragraph("Food was up.\n• Cook twice a week"),
            DocBlock.Table(listOf("Category", "Total"), listOf(listOf("Food", "$35"))),
        ))
        val text = StatementFile.readDocx(bytes)
        assertTrue(text, text.startsWith("Report"))
        assertTrue(text.contains("Where it went"))
        assertTrue(text.contains("• Cook twice a week"))
        assertTrue(text, text.contains("Food | $35"))
    }

    @Test fun aWordFileIsReadAsText() {
        val bytes = DocxWriter.write("Notes", listOf(DocBlock.Paragraph("Rent 1200")))
        val result = StatementFile.read(bytes)
        assertTrue(result is StatementFile.Result.Text)
        assertTrue((result as StatementFile.Result.Text).text.contains("Rent 1200"))
    }

    @Test fun theWriteUpBecomesHeadingsBulletsAndTables() {
        val items = listOf(entry("2026-10-02", 1_000, "Food"), entry("2026-11-05", 2_500, "Travel"), entry("2026-11-06", 300_000, "Salary", TxType.INCOME))
        val blocks = Reports.document("# Summary\nYou saved **a lot**.\n\n- Keep it up\n- Book trains early", items, Money("USD"), null, null)
        assertEquals(DocBlock.Heading("Summary", 1), blocks[0])
        assertEquals(DocBlock.Paragraph("You saved a lot."), blocks[1])
        assertEquals(DocBlock.Paragraph("• Keep it up\n• Book trains early"), blocks[2])
        val tables = blocks.filterIsInstance<DocBlock.Table>()
        assertEquals(3, tables.size) // totals, categories, months
        assertEquals(listOf("Money in", "$3,000"), tables[0].rows[0])
    }

    @Test fun fileNamesStaySafe() {
        assertEquals("Oct 2026 a b.pdf", Exports.fileName("Oct 2026: a/b", Exports.Kind.PDF))
        assertEquals("Leftovers.csv", Exports.fileName("  ", Exports.Kind.CSV))
    }
}
