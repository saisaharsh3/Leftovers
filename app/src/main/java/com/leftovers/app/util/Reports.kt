package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** What goes into the assistant's Excel and Word files, worked out from the entries themselves. */
object Reports {
    private fun amount(minor: Long) = Cell.Number(BigDecimal.valueOf(minor, 2))
    private fun text(s: String) = Cell.Text(s)
    private val monthName = DateTimeFormatter.ofPattern("MMMM yyyy")

    /** Three sheets: every entry (newest first), totals by category, and money in and out by month. */
    fun workbook(items: List<TransactionItem>, accountNames: Map<Long, String>, notes: Boolean): ByteArray {
        val sorted = items.sortedWith(compareByDescending<TransactionItem> { it.epochDay }.thenByDescending { it.createdAt })
        val entries = Sheet(
            "Entries",
            listOf("Date", "Type", "Category", "Account", "Amount") + if (notes) listOf("Note") else emptyList(),
            sorted.map { t ->
                listOf(
                    text(t.date.toString()),
                    text(if (t.type == TxType.INCOME) "Income" else "Spending"),
                    text(t.categoryName),
                    text(t.accountId?.let { accountNames[it] }.orEmpty()),
                    // Spending is negative, so a column total is what's left.
                    amount(if (t.type == TxType.INCOME) t.amountMinor else -t.amountMinor),
                ) + if (notes) listOf(text(t.note)) else emptyList()
            },
            widths = listOf(12, 10, 22, 16, 14) + if (notes) listOf(40) else emptyList(),
        )
        val categories = Sheet(
            "By category",
            listOf("Category", "Type", "Entries", "Total"),
            categoryTotals(items).map { (name, type, count, total) ->
                listOf(text(name), text(if (type == TxType.INCOME) "Income" else "Spending"), Cell.Number(BigDecimal(count)), amount(total))
            },
            widths = listOf(24, 10, 10, 14),
        )
        val months = Sheet(
            "By month",
            listOf("Month", "In", "Out", "Saved"),
            monthTotals(items).map { (m, inMinor, outMinor) -> listOf(text(m.format(monthName)), amount(inMinor), amount(outMinor), amount(inMinor - outMinor)) },
            widths = listOf(18, 14, 14, 14),
        )
        return XlsxWriter.write(listOf(entries, categories, months))
    }

    /**
     * A Word document: the AI's [writeUp] (paragraphs, "#" headings, "-" bullets), then, when [items] are given,
     * tables of the totals, the categories and the months the app works out.
     */
    fun document(writeUp: String, items: List<TransactionItem>, money: Money, from: LocalDate?, to: LocalDate?): List<DocBlock> {
        val blocks = ArrayList<DocBlock>()
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) blocks += DocBlock.Paragraph(para.toString().trim()); para.setLength(0) }
        writeUp.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("#") -> { flush(); blocks += DocBlock.Heading(line.trimStart('#').trim(), if (line.startsWith("##")) 2 else 1) }
                line.isBlank() -> flush()
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> { if (para.isNotEmpty()) para.append('\n'); para.append("• ").append(line.trimStart().drop(2).trim()) }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line.replace("**", "")) }
            }
        }
        flush()
        if (items.isEmpty()) return blocks
        val f = DateTimeFormatter.ofPattern("d MMM yyyy")
        val first = from ?: items.minOf { it.date }
        val last = to ?: items.maxOf { it.date }
        val income = items.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        val spent = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
        blocks += DocBlock.Heading("The numbers")
        blocks += DocBlock.Paragraph("${first.format(f)} to ${last.format(f)}, from ${items.size} entries in Leftovers.", muted = true)
        blocks += DocBlock.Table(
            listOf("", "Amount"),
            listOf(listOf("Money in", money.format(income)), listOf("Money out", money.format(spent)), listOf(if (income >= spent) "Saved" else "More out than in", money.format(kotlin.math.abs(income - spent)))),
        )
        val spending = categoryTotals(items).filter { it.second == TxType.EXPENSE }
        if (spending.isNotEmpty()) {
            blocks += DocBlock.Heading("Spending by category", 2)
            blocks += DocBlock.Table(
                listOf("Category", "Entries", "Total", "Share"),
                spending.map { (name, _, count, total) -> listOf(name, "$count", money.format(total), if (spent > 0) "${Math.round(total * 100.0 / spent)}%" else "") },
            )
        }
        val months = monthTotals(items)
        if (months.size > 1) {
            blocks += DocBlock.Heading("By month", 2)
            blocks += DocBlock.Table(
                listOf("Month", "In", "Out", "Saved"),
                months.map { (m, i, o) -> listOf(m.format(monthName), money.format(i), money.format(o), money.format(i - o)) },
            )
        }
        return blocks
    }

    /** Name, kind, how many entries and their total for each category, biggest first. */
    internal fun categoryTotals(items: List<TransactionItem>): List<Quad> =
        items.groupBy { it.categoryId }.map { (_, list) -> Quad(list.first().categoryName, list.first().type, list.size, list.sumOf { it.amountMinor }) }
            .sortedWith(compareBy<Quad> { it.second }.thenByDescending { it.fourth })

    /** In and out for each month the entries cover, oldest first. */
    internal fun monthTotals(items: List<TransactionItem>): List<Triple<YearMonth, Long, Long>> =
        items.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (m, list) ->
            Triple(m, list.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }, list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor })
        }

    data class Quad(val first: String, val second: TxType, val third: Int, val fourth: Long)
}
