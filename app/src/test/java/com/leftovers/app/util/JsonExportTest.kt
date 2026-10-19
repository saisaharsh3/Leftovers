package com.leftovers.app.util

import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Other apps' JSON backups and exports (#21), read without knowing which app made them. */
class JsonExportTest {
    private fun import(bytes: ByteArray): CsvImporter.Parsed = when (val r = StatementFile.read(bytes)) {
        is StatementFile.Result.Table -> CsvImporter.parseTable(r.rows)
        is StatementFile.Result.Text -> CsvImporter.parse(r.text)
        is StatementFile.Result.Unsupported -> error(r.message)
        is StatementFile.Result.Pdf -> error("not a PDF")
    }

    // Shaped like a wallet app's backup: entries point at categories and accounts by id.
    private val walletBackup = """
        {
          "accounts": [{"id": "a1", "name": "Cash"}, {"id": "a2", "name": "HDFC"}],
          "categories": [{"id": "c1", "name": "Food"}, {"id": "c2", "name": "Salary"}],
          "transactions": [
            {"id": "t1", "accountId": "a1", "categoryId": "c1", "amount": 250.5, "type": "EXPENSE", "title": "Lunch", "dateTime": "2026-10-05T13:20:00"},
            {"id": "t2", "accountId": "a2", "categoryId": "c2", "amount": 75000, "type": "INCOME", "title": "October salary", "dateTime": "2026-10-01T09:00:00"},
            {"id": "t3", "accountId": "a1", "toAccountId": "a2", "amount": 1000, "type": "TRANSFER", "dateTime": "2026-10-03T10:00:00"}
          ],
          "settings": [{"key": "currency", "value": "INR"}]
        }
    """.trimIndent()

    @Test fun entriesKeepTheirCategoryAndAccount() {
        val p = import(walletBackup.toByteArray())
        assertEquals(2, p.rows.size)
        assertEquals(1, p.transfers)
        val lunch = p.rows.first { it.note == "Lunch" }
        assertEquals(25_050L, lunch.amountMinor)
        assertEquals(TxType.EXPENSE, lunch.type)
        assertEquals("Food", lunch.category)
        assertEquals("Cash", lunch.account)
        assertEquals(LocalDate.of(2026, 10, 5), lunch.date)
        val salary = p.rows.first { it.type == TxType.INCOME }
        assertEquals("Salary", salary.category)
        assertEquals("HDFC", salary.account)
    }

    @Test fun aPlainListWithSignedAmountsAndMillisecondDates() {
        val ms = java.time.LocalDate.of(2026, 10, 7).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val json = """[{"date": $ms, "amount": -120, "note": "Coffee"}, {"date": $ms, "amount": 500, "note": "Refund"}]"""
        val p = import(json.toByteArray())
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME), p.rows.map { it.type })
        assertEquals(LocalDate.of(2026, 10, 7), p.rows[0].date)
    }

    @Test fun nestedCategoryObjects() {
        val json = """{"data": {"records": [{"created": "2026-10-02", "value": 99, "category": {"name": "Transport"}, "description": "Metro"}]}}"""
        val p = import(json.toByteArray())
        assertEquals("Transport", p.rows.single().category)
        assertEquals("Metro", p.rows.single().note)
    }

    @Test fun aZipBackupWithJsonInside() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("info.json")); z.write("""{"version": 3}""".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("backup.json")); z.write(walletBackup.toByteArray()); z.closeEntry()
        }
        assertEquals(2, import(out.toByteArray()).rows.size)
    }

    @Test fun jsonWithoutEntriesSaysSo() {
        assertTrue(StatementFile.read("""{"theme": "dark", "items": [{"id": 1, "name": "x"}]}""".toByteArray()) is StatementFile.Result.Unsupported)
        assertNull(JsonExport.rows("not json"))
    }

    // Asked for in #21: the title, the description, the tags and the category all come across.
    @Test fun titleDescriptionAndTagsBecomeTheNote() {
        val json = """
            {
              "tags": [{"id": 7, "name": "trip"}, {"id": 8, "name": "goa"}],
              "categories": [{"id": 3, "name": "Pets"}],
              "expenses": [
                {"date": "2026-10-04", "amount": 640, "title": "Vet visit", "description": "Annual shots", "categoryId": 3, "tagIds": [7, 8]},
                {"date": "2026-10-05", "amount": 90, "title": "Chai", "tags": ["snacks", "work friends"]},
                {"date": "2026-10-06", "amount": 50, "title": "Bus", "tags": ["client work"]}
              ]
            }
        """.trimIndent()
        val p = import(json.toByteArray())
        assertEquals(3, p.rows.size)
        assertEquals("Bus #client-work", p.rows.first { it.amountMinor == 5_000L }.note)
        val vet = p.rows.first { it.amountMinor == 64_000L }
        assertEquals("Vet visit · Annual shots #trip #goa", vet.note)
        assertEquals("Pets", vet.category)
        assertEquals("Chai #snacks #work-friends", p.rows.first { it.amountMinor == 9_000L }.note)
    }
}
