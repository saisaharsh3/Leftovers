package com.leftovers.app.util

import com.leftovers.app.data.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Reads entries from a CSV file: Leftovers' own export, a bank statement, or another app's export.
 * Columns are found by their names, so the order doesn't matter. Nothing is saved here; see [Parsed].
 */
object CsvImporter {
    data class Row(
        val date: LocalDate,
        val amountMinor: Long,
        val type: TxType,
        val category: String?,
        val note: String,
        val account: String?,
    )

    /** What was read: the rows, and how many lines couldn't be (no date or amount). */
    data class Parsed(val rows: List<Row>, val unreadable: Int, val error: String? = null)

    private val dateNames = listOf("date", "transaction date", "txn date", "value date", "posting date", "posted", "booking date")
    private val amountNames = listOf("amount", "amt", "transaction amount", "value")
    private val debitNames = listOf("debit", "withdrawal", "withdrawals", "dr", "money out", "paid out", "debit amount", "withdrawal amt")
    private val creditNames = listOf("credit", "deposit", "deposits", "cr", "money in", "paid in", "credit amount", "deposit amt")
    private val typeNames = listOf("type", "transaction type", "dr/cr", "cr/dr")
    private val categoryNames = listOf("category", "categories")
    private val noteNames = listOf("note", "notes", "description", "narration", "details", "particulars", "memo", "payee", "remarks", "merchant", "name")
    private val accountNames = listOf("account", "account name", "wallet")

    fun parse(text: String): Parsed {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return Parsed(emptyList(), 0, "The file is empty.")
        // Bank exports often start with a few lines of account details; the header is the first line naming a date.
        val headerAt = lines.indexOfFirst { line -> split(line, delimiterOf(line)).any { cell -> cell.clean() in dateNames } }
        if (headerAt < 0) return Parsed(emptyList(), 0, "Couldn't find a header row with a Date column.")
        val delimiter = delimiterOf(lines[headerAt])
        val header = split(lines[headerAt], delimiter).map { it.clean() }
        fun column(names: List<String>) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
            ?: header.indexOfFirst { h -> names.any { n -> h.contains(n) && n.length > 3 } }.takeIf { it >= 0 }

        val dateCol = column(dateNames) ?: return Parsed(emptyList(), 0, "Couldn't find a Date column.")
        val amountCol = column(amountNames)
        val debitCol = column(debitNames)
        val creditCol = column(creditNames)
        if (amountCol == null && debitCol == null && creditCol == null) return Parsed(emptyList(), 0, "Couldn't find an Amount, Debit or Credit column.")
        val typeCol = column(typeNames)
        val categoryCol = column(categoryNames)
        val noteCol = column(noteNames)
        val accountCol = column(accountNames)

        val body = lines.drop(headerAt + 1).map { split(it, delimiter) }
        val dayFirst = dayComesFirst(body.mapNotNull { it.getOrNull(dateCol) })
        var unreadable = 0
        // With one amount column and no type, a file that has negative amounts uses the sign (minus is spending);
        // one without any is an expenses-only export.
        val signedFile = amountCol != null && body.any { cells -> cells.getOrNull(amountCol)?.let(::parseAmount)?.let { it < 0 } == true }
        val rows = body.mapNotNull { cells ->
            val date = cells.getOrNull(dateCol)?.let { parseDate(it, dayFirst) }
            val typeText = typeCol?.let { cells.getOrNull(it) }?.trim()?.lowercase().orEmpty()
            val debit = debitCol?.let { cells.getOrNull(it) }?.let(::parseAmount)?.takeIf { it != 0L }
            val credit = creditCol?.let { cells.getOrNull(it) }?.let(::parseAmount)?.takeIf { it != 0L }
            val signed: Long? = when {
                debit != null -> -kotlin.math.abs(debit)
                credit != null -> kotlin.math.abs(credit)
                amountCol != null -> cells.getOrNull(amountCol)?.let { raw ->
                    parseAmount(raw)?.let { a ->
                        // A "Dr" or "Cr" written next to the amount says which way it went.
                        when {
                            Regex("""\bdr\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> -kotlin.math.abs(a)
                            Regex("""\bcr\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> kotlin.math.abs(a)
                            else -> a
                        }
                    }
                }
                else -> null
            }
            if (date == null || signed == null || signed == 0L) {
                unreadable++
                return@mapNotNull null
            }
            val type = when {
                typeText.startsWith("inc") || typeText == "credit" || typeText == "cr" || typeText == "deposit" -> TxType.INCOME
                typeText.startsWith("exp") || typeText == "debit" || typeText == "dr" || typeText == "withdrawal" -> TxType.EXPENSE
                signed < 0 -> TxType.EXPENSE
                debitCol != null || creditCol != null || signedFile -> TxType.INCOME
                else -> TxType.EXPENSE
            }
            Row(
                date = date,
                amountMinor = kotlin.math.abs(signed),
                type = type,
                category = categoryCol?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() },
                note = noteCol?.let { cells.getOrNull(it) }?.trim()?.removePrefix("'").orEmpty().take(120),
                account = accountCol?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return Parsed(rows, unreadable)
    }

    private fun String.clean() = trim().trim('"').trim().lowercase().replace(Regex("""\s*\(.*\)$"""), "").replace(Regex("""[.:]$"""), "").trim()

    private fun delimiterOf(line: String): Char = listOf(',', ';', '\t', '|').maxBy { d -> line.count { it == d } }

    /** Splits one CSV line, honouring "quoted, cells" and doubled quotes inside them. */
    internal fun split(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(ch)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    /** "₹1,250.50", "-45.00", "(300)", "1.234,56" → minor units, signed; null when it isn't a number. */
    internal fun parseAmount(raw: String): Long? {
        var t = raw.trim()
        if (t.isEmpty()) return null
        val negative = t.startsWith("-") || t.startsWith("(") && t.endsWith(")") || t.endsWith("-")
        t = t.replace(Regex("""[^0-9.,]"""), "")
        if (t.isEmpty() || t.none(Char::isDigit)) return null
        // Decimal comma ("1.234,56" or "45,00"): the last separator is followed by exactly two digits.
        val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
        t = if (lastComma > lastDot && t.length - lastComma - 1 == 2) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        val value = t.toBigDecimalOrNull() ?: return null
        val minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        return if (negative) -minor else minor
    }

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private fun named(pattern: String) = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)
    private val monthNameFormats = listOf("d MMM yyyy", "d-MMM-yyyy", "d-MMM-yy", "d MMM yy", "d/MMM/yyyy", "MMM d, yyyy", "d MMMM yyyy").map(::named)

    /** Whether dates are day-first (10/09/2026 is 10 September), judged from every row; day-first unless a row proves otherwise. */
    private fun dayComesFirst(samples: List<String>): Boolean {
        val pairs = samples.mapNotNull { Regex("""^\s*(\d{1,2})[/.-](\d{1,2})[/.-]\d{2,4}""").find(it)?.destructured?.let { (a, b) -> a.toInt() to b.toInt() } }
        if (pairs.any { it.first > 12 }) return true
        if (pairs.any { it.second > 12 }) return false
        return true
    }

    internal fun parseDate(raw: String, dayFirst: Boolean = true): LocalDate? {
        val t = raw.trim().trim('"').substringBefore('T').substringBefore(' ').takeIf { it.isNotEmpty() } ?: return null
        runCatching { return LocalDate.parse(t, isoDate) }
        Regex("""^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$""").find(t)?.destructured?.let { (a, b, y) ->
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            val (day, month) = if (dayFirst) a.toInt() to b.toInt() else b.toInt() to a.toInt()
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        Regex("""^(\d{4})[/.](\d{1,2})[/.](\d{1,2})$""").find(t)?.destructured?.let { (y, m, d) ->
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
        // Month names ("10 Oct 2026", "10-Oct-26") need the whole cell, spaces included.
        val full = raw.trim().trim('"')
        for (f in monthNameFormats) runCatching { return LocalDate.parse(full, f) }
        return null
    }
}
