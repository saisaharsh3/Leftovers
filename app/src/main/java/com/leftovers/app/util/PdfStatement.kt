package com.leftovers.app.util

import android.content.Context
import com.leftovers.app.data.TxType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlin.math.abs

/**
 * Reads a bank statement PDF on the phone. A PDF has no table, only lines of text, so each transaction is found
 * by the date that starts its line and the amounts that end it. The running balance says which way the money
 * went: when it drops, it's spending, whichever column the amount was printed in. Lines without a date carry
 * on the description above them.
 */
object PdfStatement {
    /** The statement is protected; [wrong] when a password was tried and didn't open it. */
    class PasswordNeeded(val wrong: Boolean) : Exception("This PDF has a password")

    /** The statement's text, line by line in reading order. */
    fun text(context: Context, bytes: ByteArray, password: String? = null): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = try {
            PDDocument.load(bytes, password.orEmpty())
        } catch (_: InvalidPasswordException) {
            throw PasswordNeeded(wrong = password != null)
        }
        return doc.use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
    }

    private const val DATE = """(\d{1,2}[/.\-](?:\d{1,2}|[A-Za-z]{3})[/.\-]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2} [A-Za-z]{3,9},? \d{2,4}|[A-Za-z]{3,9} \d{1,2}, \d{4})"""
    private val startsWithDate = Regex("""^\s*$DATE\b""")
    private val anyDate = Regex("""\b$DATE\b""")
    /** Cheque, UPI and reference numbers: eight digits or more. */
    private val longNumber = Regex("""\b\d{8,}\b""")

    /** Money as statements print it: always two decimals, so reference and cheque numbers aren't mistaken for it. */
    private val amount = Regex("""(?<![\w.,])(-?(?:\d{1,3}(?:,\d{2,3})+|\d+)\.\d{2})(?:\s*\(?(Cr|Dr|CR|DR|cr|dr)\)?\.?)?(?![\w.])""")

    private val opening = Regex("""opening\s+balance""", RegexOption.IGNORE_CASE)
    /** Lines that end a transaction's description: page furniture and totals. */
    private val furniture = Regex("""^(page \d|closing balance|opening balance|statement (summary|of)|total|grand total|\*|generated on|this is a)""", RegexOption.IGNORE_CASE)
    private val outWords = Regex("""\b(dr|debit|wdl|withdrawal|atm|pos|purchase|paid|upi/dr|to transfer|bill ?pay)\b""", RegexOption.IGNORE_CASE)
    private val inWords = Regex("""\b(cr|credit|salary|refund|cashback|interest|deposit|received|upi/cr|by transfer|reversal)\b""", RegexOption.IGNORE_CASE)

    private data class Line(val date: String, val text: String, val amounts: List<Pair<Long, String?>>, val extra: MutableList<String> = mutableListOf())

    /** Currencies printed against their number ("PKR1,000.00", "Rs.250"), split off so the amount reads on its own. */
    private val gluedCurrency = Regex(
        """(?<![A-Za-z])(PKR|INR|Rs\.?|USD|EUR|GBP|AED|SAR|QAR|KWD|OMR|BHD|BDT|Tk\.?|LKR|NPR|MYR|SGD|IDR|PHP|THB|NGN|KES|ZAR|₹|\$|€|£|৳|₨)(?=\d)""",
    )

    fun parse(text: String): CsvImporter.Parsed {
        val lines = text.lines().map { gluedCurrency.replace(it.trim(), "$1 ") }.filter { it.isNotEmpty() }
        // Payment apps (PhonePe, Paytm, Google Pay...) print each payment as a block, with no running balance;
        // banks print a line each with one. Both are read, and whichever finds more is the one.
        val blocks = parseBlocks(lines)
        val lined = parseLines(lines)
        return if (blocks.rows.size > lined.rows.size) blocks else lined
    }

    private fun parseLines(lines: List<String>): CsvImporter.Parsed {
        var previousBalance: Long? = null
        val rows = mutableListOf<Line>()
        var current: Line? = null
        for (raw in lines) {
            if (opening.containsMatchIn(raw) && current == null) {
                amount.findAll(raw).lastOrNull()?.let { previousBalance = money(it) }
                continue
            }
            val date = startsWithDate.find(raw)
            if (date != null) {
                current = Line(date.groupValues[1], raw.substring(date.range.last + 1), amount.findAll(raw.substring(date.range.last + 1)).map { money(it) to it.groupValues[2].ifEmpty { null } }.toList())
                rows += current
            } else if (current != null) {
                if (furniture.containsMatchIn(raw)) current = null
                else current.extra += raw
            }
        }
        if (rows.isEmpty()) return CsvImporter.Parsed(emptyList(), 0, "Couldn't find any transactions in this PDF. If it's a scanned image, download the statement as Excel or CSV instead.")

        val dayFirst = CsvImporter.dayComesFirst(rows.map { it.date })
        var unreadable = 0
        val out = mutableListOf<CsvImporter.Row>()
        for (row in rows) {
            // A row whose amounts wrapped onto the next line.
            val amounts = row.amounts.ifEmpty {
                row.extra.firstNotNullOfOrNull { e -> amount.findAll(e).map { money(it) to it.groupValues[2].ifEmpty { null } }.toList().takeIf { it.isNotEmpty() } }.orEmpty()
            }
            val date = CsvImporter.parseDate(row.date, dayFirst)
            if (date == null || amounts.isEmpty()) {
                unreadable++
                continue
            }
            val note = (listOf(firstText(row.text)) + row.extra.filter { amount.find(it) == null }).joinToString(" ")
                // Dates and long reference numbers are noise in a note.
                .replace(anyDate, " ").replace(longNumber, " ").replace(Regex("""\s+"""), " ").trim().take(120)
            var value: Long
            var spent: Boolean? = null
            if (amounts.size >= 2) {
                val balance = amounts.last().first
                value = abs(amounts[amounts.size - 2].first)
                previousBalance?.let { before ->
                    val change = balance - before
                    // Trust the balance: take whichever amount matches its change, else the change itself.
                    value = amounts.dropLast(1).map { abs(it.first) }.firstOrNull { it == abs(change) } ?: abs(change).takeIf { it != 0L } ?: value
                    if (change != 0L) spent = change < 0
                }
                previousBalance = balance
                if (spent == null) spent = marker(amounts[amounts.size - 2].second) ?: guess(note)
            } else {
                value = abs(amounts[0].first)
                spent = marker(amounts[0].second) ?: if (amounts[0].first < 0) true else guess(note)
            }
            if (value == 0L || spent == null) {
                unreadable++
                continue
            }
            out += CsvImporter.Row(date, value, if (spent == true) TxType.EXPENSE else TxType.INCOME, null, note, null)
        }
        return CsvImporter.Parsed(out, unreadable)
    }

    /** An amount with its currency, as payment apps print it: "₹40", "₹3,923.03", "Rs. 250", "INR 1,200". */
    private val currencyAmount = Regex("""(?:₹|৳|₨|\$|€|£|\bRs\.?|\b(?:INR|PKR|BDT|Tk|LKR|NPR|USD|EUR|GBP|AED|SAR))\s*(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    private val blockOut = Regex("""\b(debit|debited|dr|paid to|payment to|sent to|paid for|bill paid|recharge)\b""", RegexOption.IGNORE_CASE)
    private val blockIn = Regex("""\b(credit|credited|cr|received from|refund|cashback|reversal)\b""", RegexOption.IGNORE_CASE)
    /** Lines in a block that aren't its description: times, IDs, the card or account used, page furniture. */
    private val blockNoise = Regex("""^(\d{1,4}:?\d{2}\s*[ap]m|transaction id|utr|ref|reference|order id|paid by|credited to|debited from|page \d|this is a|date\b)""", RegexOption.IGNORE_CASE)

    /** One payment per block: from a line that starts with a date up to the next one. */
    private fun parseBlocks(lines: List<String>): CsvImporter.Parsed {
        val starts = lines.indices.filter { startsWithDate.find(lines[it]) != null }
        if (starts.isEmpty()) return CsvImporter.Parsed(emptyList(), 0)
        val dateText = starts.map { startsWithDate.find(lines[it])!!.groupValues[1] }
        val dayFirst = CsvImporter.dayComesFirst(dateText)
        var unreadable = 0
        var transfers = 0
        val out = mutableListOf<CsvImporter.Row>()
        starts.forEachIndexed { n, at ->
            val block = lines.subList(at, starts.getOrElse(n + 1) { lines.size })
            val date = CsvImporter.parseDate(dateText[n], dayFirst)
            val withAmount = block.firstOrNull { currencyAmount.containsMatchIn(it) }
            val value = withAmount?.let { line -> currencyAmount.find(line)?.let { CsvImporter.parseAmount(it.groupValues[1]) } }
            if (date == null || value == null || value == 0L) {
                unreadable++
                return@forEachIndexed
            }
            // What it was: the first line with words that isn't a time, an ID or the account used.
            val described = block.map { it.replace(startsWithDate, "").trim() }
                .firstOrNull { l -> l.any(Char::isLetter) && blockNoise.find(l) == null } ?: ""
            if (CsvImporter.ownTransfer.containsMatchIn(described)) {
                transfers++
                return@forEachIndexed
            }
            val all = block.joinToString(" ")
            val spent = when {
                Regex("""\bDEBIT\b""").containsMatchIn(all) -> true
                Regex("""\bCREDIT\b""").containsMatchIn(all) -> false
                blockOut.containsMatchIn(described) && !blockIn.containsMatchIn(described) -> true
                blockIn.containsMatchIn(described) && !blockOut.containsMatchIn(described) -> false
                else -> null
            }
            if (spent == null) {
                unreadable++
                return@forEachIndexed
            }
            val note = CsvImporter.payee(
                described.replace(currencyAmount, " ").replace(Regex("""\b(DEBIT|CREDIT|Dr|Cr)\b"""), " ").replace(Regex("""\s+"""), " ").trim(),
            ).take(120)
            out += CsvImporter.Row(date, value, if (spent) TxType.EXPENSE else TxType.INCOME, null, note, null)
        }
        return CsvImporter.Parsed(out, unreadable, transfers = transfers)
    }

    private fun money(m: MatchResult): Long = CsvImporter.parseAmount(m.groupValues[1]) ?: 0L

    private fun marker(mark: String?): Boolean? = when (mark?.lowercase()) {
        "dr" -> true
        "cr" -> false
        else -> null
    }

    private fun guess(note: String): Boolean? = when {
        outWords.containsMatchIn(note) && !inWords.containsMatchIn(note) -> true
        inWords.containsMatchIn(note) && !outWords.containsMatchIn(note) -> false
        else -> null
    }

    /** The description part of a row: what comes before its first amount. */
    private fun firstText(rest: String): String = amount.find(rest)?.let { rest.substring(0, it.range.first) } ?: rest
}
