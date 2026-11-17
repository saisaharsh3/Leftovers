package com.leftovers.desktop.data

import com.leftovers.app.data.Category
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import com.leftovers.app.util.CategoryGuess
import com.leftovers.app.util.CsvImporter
import com.leftovers.app.util.MerchantMemory
import com.leftovers.app.util.PdfLines
import com.leftovers.app.util.StatementFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper

/** A statement read and ready to add: nothing is saved until [Store.importStatement]. */
data class ImportPreview(
    val rows: List<Row>,
    val duplicates: Int,
    val unreadable: Int,
    val transfers: Int,
    /** Categories named in the file that aren't here yet; they're made on import. */
    val newCategories: List<Pair<TxType, String>>,
) {
    /**
     * One entry to add. [categoryId] is set when the category is known; [newCategory] when it's one to make;
     * [guessed] when the file had none and the category came from past entries at the same place.
     */
    data class Row(
        val entry: Transaction,
        val categoryName: String,
        val newCategory: Pair<TxType, String>? = null,
        val guessed: Boolean = false,
        val include: Boolean = true,
    )

    val guessedCount get() = rows.count { it.guessed }
}

/** The statement's file can't be read as it is. */
class ImportProblem(message: String) : Exception(message)

/** A PDF statement with a password; [wrong] when the one tried didn't open it. */
class PdfPasswordNeeded(val wrong: Boolean) : Exception()

object Statements {
    /** The file's transactions, whatever it is: CSV, Excel, a bank's HTML or JSON, or a PDF statement. */
    fun read(bytes: ByteArray, password: String? = null): CsvImporter.Parsed {
        val parsed = when (val file = StatementFile.read(bytes)) {
            is StatementFile.Result.Unsupported -> throw ImportProblem(file.message)
            is StatementFile.Result.Text -> CsvImporter.parse(file.text)
            is StatementFile.Result.Table -> CsvImporter.parseTable(file.rows)
            is StatementFile.Result.Pdf -> PdfLines.parse(pdfText(file.bytes, password))
        }
        parsed.error?.let { throw ImportProblem(it) }
        return parsed
    }

    fun pdfText(bytes: ByteArray, password: String?): String {
        val doc = try {
            PDDocument.load(bytes, password.orEmpty())
        } catch (_: InvalidPasswordException) {
            throw PdfPasswordNeeded(wrong = password != null)
        } catch (e: Exception) {
            throw ImportProblem("Couldn't read this PDF.")
        }
        return doc.use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
    }
}

/**
 * Works out what a statement adds, as the phone does: rows already here are skipped (counted, so two real payments
 * alike both stay), categories are matched by name or made, and a row with no category goes to the one used before
 * at the same place, else to Other.
 */
fun Store.previewImport(parsed: CsvImporter.Parsed, now: Long = System.currentTimeMillis()): ImportPreview {
    val view = view.value
    val cats = book.categories
    val history = view.entries.map { MerchantMemory.Past(it.note, it.type, it.categoryId, it.epochDay, it.createdAt) }
    fun fallback(type: TxType) = cats.filter { it.type == type }.let { list ->
        list.firstOrNull { it.name.lowercase() in setOf("other", "others", "miscellaneous", "misc", "general") } ?: list.firstOrNull()
    }
    val existing = view.entries.groupingBy { Triple(it.epochDay, it.amountMinor, it.type to it.note.trim().lowercase()) }.eachCount().toMutableMap()
    var duplicates = 0
    val newCategories = linkedMapOf<Pair<TxType, String>, String>()
    val rows = mutableListOf<ImportPreview.Row>()
    val defaultAccount = book.prefs.defaultAccountId.takeIf { id -> book.accounts.any { it.id == id } }
    parsed.rows.forEach { row ->
        val key = Triple(row.date.toEpochDay(), row.amountMinor, row.type to row.note.trim().lowercase())
        val left = existing[key] ?: 0
        if (left > 0) { existing[key] = left - 1; duplicates++; return@forEach }
        val wanted = row.category?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
        val match = wanted?.let { name -> cats.firstOrNull { it.type == row.type && it.name.equals(name, ignoreCase = true) } }
        val toMake = if (match == null && wanted != null) (row.type to wanted.lowercase()).also { newCategories.putIfAbsent(it, wanted) } else null
        val remembered = if (wanted == null) MerchantMemory.categoryFor(row.note, row.type, history)?.let { id -> cats.firstOrNull { it.id == id } } else null
        val category = match ?: remembered ?: fallback(row.type) ?: return@forEach
        val account = row.account?.let { name -> book.accounts.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id } ?: defaultAccount
        rows += ImportPreview.Row(
            Transaction(
                amountMinor = row.amountMinor, type = row.type, categoryId = category.id, epochDay = row.date.toEpochDay(),
                note = row.note, details = row.details, accountId = account, createdAt = now,
            ),
            categoryName = toMake?.let { newCategories.getValue(it) } ?: category.name,
            newCategory = toMake?.let { it.first to newCategories.getValue(it) },
            guessed = remembered != null,
        )
    }
    return ImportPreview(rows, duplicates, parsed.unreadable, parsed.transfers, newCategories.map { (k, name) -> k.first to name })
}

/** Adds the rows picked, making the new categories they need, as one step for undo. Returns how many were added. */
fun Store.importStatement(preview: ImportPreview): Int = batch {
    val picked = preview.rows.filter { it.include }
    val needed = picked.mapNotNull { it.newCategory }.distinctBy { it.first to it.second.lowercase() }
    needed.forEachIndexed { i, (type, name) ->
        if (book.categories.none { it.type == type && it.name.equals(name, ignoreCase = true) }) {
            saveCategory(
                Category(
                    name = name, emoji = CategoryGuess.icon(name, type), color = Palette.all[(book.categories.size + i) % Palette.all.size],
                    type = type, addsToBudget = type == TxType.INCOME,
                ),
            )
        }
    }
    val cats = book.categories
    val entries = picked.map { r ->
        r.newCategory?.let { (type, name) -> cats.firstOrNull { it.type == type && it.name.equals(name, ignoreCase = true) }?.let { r.entry.copy(categoryId = it.id) } } ?: r.entry
    }
    addTransactions(entries)
    entries.size
}
