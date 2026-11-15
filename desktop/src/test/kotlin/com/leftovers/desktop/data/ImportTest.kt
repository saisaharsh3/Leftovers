package com.leftovers.desktop.data

import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Importing a statement on the desktop. Made-up data only. */
class ImportTest {
    private val csv = """
        Date,Description,Amount,Category
        2026-11-02,Corner Bakery,-4.20,
        2026-11-03,City Gym,-35.00,Fitness
        2026-11-04,Acme Payroll,2500.00,Salary
        2026-11-05,Corner Bakery,-3.80,
    """.trimIndent()

    private fun store() = Store(Files.createTempDirectory("leftovers-test").toFile()).apply {
        save(Book.starter("EUR"))
        // Logged before: the bakery as Food & Dining, and one bakery visit that's in the file too.
        saveTransaction(Transaction(amountMinor = 450, type = TxType.EXPENSE, categoryId = 1, epochDay = LocalDate.of(2026, 10, 20).toEpochDay(), note = "Corner Bakery", createdAt = 1))
        saveTransaction(Transaction(amountMinor = 420, type = TxType.EXPENSE, categoryId = 1, epochDay = LocalDate.of(2026, 11, 2).toEpochDay(), note = "Corner Bakery", createdAt = 2))
    }

    @Test
    fun aStatementSkipsWhatsHereAndGuessesFromThePast() {
        val s = store()
        val p = s.previewImport(Statements.read(csv.toByteArray()))
        assertEquals(1, p.duplicates)
        assertEquals(3, p.rows.size)
        val bakery = p.rows.single { it.entry.amountMinor == 380L }
        assertTrue(bakery.guessed)
        assertEquals(1L, bakery.entry.categoryId)
        assertEquals(listOf(TxType.EXPENSE to "Fitness"), p.newCategories)
        assertEquals(13L, p.rows.single { it.entry.type == TxType.INCOME }.entry.categoryId)
    }

    @Test
    fun importingMakesTheNewCategoryAndIsOneUndo() {
        val s = store()
        val p = s.previewImport(Statements.read(csv.toByteArray()))
        assertEquals(3, s.importStatement(p))
        val fitness = s.book.categories.single { it.name == "Fitness" }
        assertEquals(fitness.id, s.book.transactions.single { it.note == "City Gym" }.categoryId)
        s.undo()
        assertEquals(2, s.book.transactions.size)
        assertTrue(s.book.categories.none { it.name == "Fitness" })
    }

    @Test
    fun rowsLeftOutAreNotAdded() {
        val s = store()
        val p = s.previewImport(Statements.read(csv.toByteArray()))
        val picked = p.copy(rows = p.rows.map { if (it.entry.type == TxType.INCOME) it.copy(include = false) else it })
        assertEquals(2, s.importStatement(picked))
        assertTrue(s.book.transactions.none { it.type == TxType.INCOME })
    }
}
