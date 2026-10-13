package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.leftovers.app.R
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.CategoryTotal
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.categoryTotals
import com.leftovers.app.data.totalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A statement for a month or a year as a PDF: income and spending, categories, biggest expenses and
 * subscriptions on one page, made on the phone. Notes are left out unless asked for, and there are no
 * account numbers.
 */
object StatementPdf {
    /** A subscription or recurring payment in the statement. */
    data class Charge(val name: String, val date: LocalDate, val amountMinor: Long)

    /** One month's totals, for a year's statement. */
    data class MonthRow(val month: YearMonth, val incomeMinor: Long, val spentMinor: Long)

    data class Statement(
        /** The month, or for a year's statement null with [year] set. */
        val month: YearMonth?,
        val year: Int,
        val incomeMinor: Long,
        val spentMinor: Long,
        /** Null when no budget is set. */
        val budgetMinor: Long?,
        /** Spending in the month or year before, for the comparison line. */
        val previousSpentMinor: Long,
        val categories: List<CategoryTotal>,
        val biggest: List<TransactionItem>,
        val charges: List<Charge>,
        /** A year's statement only: each month's totals. */
        val months: List<MonthRow>,
        /** Every entry, newest first, for the optional list after the summary. */
        val entries: List<TransactionItem>,
    ) {
        val isYear: Boolean get() = month == null
        val leftMinor: Long? get() = budgetMinor?.let { it - spentMinor }

        /** Percent change in spending from the period before; null when there's nothing to compare with. */
        val changePercent: Int?
            get() = if (previousSpentMinor <= 0) null else Math.round((spentMinor - previousSpentMinor) * 100.0 / previousSpentMinor).toInt()

        val title: String
            get() = month?.let { "${it.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.year}" } ?: "$year"
    }

    /** The numbers for [month], or for the whole [year] when [month] is null. */
    fun build(
        month: YearMonth?,
        year: Int,
        all: List<TransactionItem>,
        plan: BudgetPlan,
        recurring: List<RecurringItem>,
        today: LocalDate = LocalDate.now(),
    ): Statement {
        val months = if (month != null) listOf(month) else (1..12).map { YearMonth.of(year, it) }
        fun inPeriod(d: LocalDate) = if (month != null) YearMonth.from(d) == month else d.year == year
        fun inPrevious(d: LocalDate) = if (month != null) YearMonth.from(d) == month.minusMonths(1) else d.year == year - 1
        val items = all.filter { inPeriod(it.date) }.sortedByDescending { it.date }
        // A budget only counts when one is set; for a year, the months up to now.
        val budget = if (!plan.isSet) null else months.filter { it <= YearMonth.from(today) }.sumOf { plan.budgetFor(it, all) }
        // Subscriptions and recurring payments due in the period, up to today.
        val charges = months.flatMap { m ->
            recurring.filter { it.active && it.type == TxType.EXPENSE && it.toRecurring().isDueIn(m) }
                .map { Charge(it.name, it.toRecurring().chargeDate(m), it.amountMinor) }
        }.filter { it.date <= today }.sortedBy { it.date }
        return Statement(
            month = month,
            year = month?.year ?: year,
            incomeMinor = items.totalOf(TxType.INCOME),
            spentMinor = items.totalOf(TxType.EXPENSE),
            budgetMinor = budget,
            previousSpentMinor = all.filter { inPrevious(it.date) }.totalOf(TxType.EXPENSE),
            categories = items.categoryTotals(TxType.EXPENSE),
            biggest = items.filter { it.type == TxType.EXPENSE }.sortedByDescending { it.amountMinor }.take(5),
            charges = charges,
            months = if (month != null) emptyList() else months.map { m ->
                val inMonth = items.filter { YearMonth.from(it.date) == m }
                MonthRow(m, inMonth.totalOf(TxType.INCOME), inMonth.totalOf(TxType.EXPENSE))
            },
            entries = items,
        )
    }

    // A4 in points.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val INK = 0xFF18181B.toInt()
    private const val MUTED = 0xFF71717A.toInt()
    private const val LINE = 0xFFE4E4E7.toInt()
    private const val BAR = 0xFFA1A1AA.toInt()
    private const val BAR_BACK = 0xFFF4F4F5.toInt()
    private val day = DateTimeFormatter.ofPattern("d MMM")
    private val made = DateTimeFormatter.ofPattern("d MMM yyyy")

    /** Writes the PDF to the app's cache, shared through its FileProvider. */
    suspend fun write(context: Context, s: Statement, money: Money, includeNotes: Boolean, includeEntries: Boolean): Uri = withContext(Dispatchers.IO) {
        val base = runCatching { ResourcesCompat.getFont(context, R.font.manrope) }.getOrNull() ?: Typeface.DEFAULT
        val bold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, 700, false) else Typeface.create(base, Typeface.BOLD)
        val pen = Pen(base, bold)
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create())

        var page = newPage()
        var c = page.canvas
        c.drawColor(Color.WHITE)
        var y = MARGIN + 6f

        // Header
        pen.text(c, "Leftovers", MARGIN, y, 10f, MUTED)
        pen.text(c, "Made ${LocalDate.now().format(made)}", PAGE_W - MARGIN, y, 9f, MUTED, align = Paint.Align.RIGHT)
        y += 30f
        pen.text(c, "${s.title} statement", MARGIN, y, 24f, INK, bold = true)
        y += 26f

        // Summary: four boxes in a row.
        val boxes = listOf(
            "Income" to money.format(s.incomeMinor),
            "Spent" to money.format(s.spentMinor),
            "Budget" to (s.budgetMinor?.let { money.format(it) } ?: "Not set"),
            (if ((s.leftMinor ?: 0) < 0) "Over budget" else "Left") to (s.leftMinor?.let { money.format(kotlin.math.abs(it)) } ?: "—"),
        )
        val gap = 10f
        val boxW = (PAGE_W - 2 * MARGIN - gap * 3) / 4
        boxes.forEachIndexed { i, (label, value) ->
            val x = MARGIN + i * (boxW + gap)
            pen.box(c, RectF(x, y, x + boxW, y + 56f))
            pen.text(c, label, x + 12f, y + 20f, 9f, MUTED)
            pen.text(c, value, x + 12f, y + 42f, 14f, INK, bold = true, maxWidth = boxW - 20f)
        }
        y += 74f
        val previousName = if (s.isYear) "${s.year - 1}" else s.month!!.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        s.changePercent?.let { change ->
            val line = when {
                change < 0 -> "Spent ${-change}% less than $previousName (${money.format(s.previousSpentMinor)})."
                change > 0 -> "Spent $change% more than $previousName (${money.format(s.previousSpentMinor)})."
                else -> "Spent the same as $previousName."
            }
            pen.text(c, line, MARGIN, y, 10f, INK)
            y += 22f
        }

        // A year's statement: each month's income and spending.
        if (s.isYear) {
            y = pen.section(c, "Month by month", y)
            val colIn = PAGE_W - MARGIN - 110f
            pen.text(c, "Income", colIn, y, 8.5f, MUTED, align = Paint.Align.RIGHT)
            pen.text(c, "Spent", PAGE_W - MARGIN, y, 8.5f, MUTED, align = Paint.Align.RIGHT)
            y += 14f
            s.months.forEach { m ->
                pen.text(c, m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), MARGIN, y, 9.5f, INK)
                pen.text(c, if (m.incomeMinor > 0) money.format(m.incomeMinor) else "—", colIn, y, 9.5f, INK, align = Paint.Align.RIGHT)
                pen.text(c, if (m.spentMinor > 0) money.format(m.spentMinor) else "—", PAGE_W - MARGIN, y, 9.5f, INK, align = Paint.Align.RIGHT)
                y += 13f
            }
            y += 6f
        }

        // Spending by category, as plain bars: the amounts and shares carry it, not colour.
        y = pen.section(c, "Spending by category", y)
        if (s.categories.isEmpty()) {
            pen.text(c, "No spending ${if (s.isYear) "this year" else "this month"}.", MARGIN, y, 10f, MUTED)
            y += 18f
        } else {
            val shown = s.categories.take(if (s.isYear) 5 else 9)
            val rest = s.categories.drop(shown.size)
            val most = shown.maxOf { it.totalMinor }.coerceAtLeast(1)
            val barX = MARGIN + 130f
            val barW = PAGE_W - 2 * MARGIN - 130f - 150f
            (shown.map { it.name to it.totalMinor } + if (rest.isNotEmpty()) listOf("${rest.size} more" to rest.sumOf { it.totalMinor }) else emptyList())
                .forEach { (name, total) ->
                    pen.text(c, name, MARGIN, y, 10f, INK, maxWidth = 122f)
                    pen.bar(c, barX, y - 7f, barW, 7f, total.toFloat() / most)
                    pen.text(c, money.format(total), PAGE_W - MARGIN - 44f, y, 10f, INK, align = Paint.Align.RIGHT)
                    pen.text(c, "${Math.round(total * 100.0 / s.spentMinor.coerceAtLeast(1))}%", PAGE_W - MARGIN, y, 9f, MUTED, align = Paint.Align.RIGHT)
                    y += 17f
                }
            y += 6f
        }

        // Biggest expenses
        y = pen.section(c, "Biggest expenses", y)
        if (s.biggest.isEmpty()) {
            pen.text(c, "None.", MARGIN, y, 10f, MUTED)
            y += 18f
        } else {
            s.biggest.forEach { t ->
                pen.text(c, t.date.format(day), MARGIN, y, 9.5f, MUTED)
                val what = if (includeNotes && t.note.isNotBlank()) "${t.categoryName} · ${t.note}" else t.categoryName
                pen.text(c, what, MARGIN + 52f, y, 10f, INK, maxWidth = PAGE_W - 2 * MARGIN - 52f - 100f)
                pen.text(c, money.format(t.amountMinor), PAGE_W - MARGIN, y, 10f, INK, align = Paint.Align.RIGHT)
                y += 16f
            }
            y += 6f
        }

        // Subscriptions charged in the period. A year lists each one once, with how often and the total.
        if (y < PAGE_H - 100f) {
            y = pen.section(c, "Subscriptions", y)
            if (s.charges.isEmpty()) {
                pen.text(c, "None charged.", MARGIN, y, 10f, MUTED)
                y += 18f
            } else {
                val rows = if (s.isYear) {
                    s.charges.groupBy { it.name }.map { (name, list) -> Triple(name, "${list.size}×", list.sumOf { it.amountMinor }) }
                } else {
                    s.charges.map { Triple(it.name, it.date.format(day), it.amountMinor) }
                }
                val room = ((PAGE_H - 70f - y) / 16f).toInt().coerceAtLeast(1)
                rows.take(room).forEach { (name, detail, amount) ->
                    pen.text(c, detail, MARGIN, y, 9.5f, MUTED)
                    pen.text(c, name, MARGIN + 52f, y, 10f, INK, maxWidth = PAGE_W - 2 * MARGIN - 52f - 100f)
                    pen.text(c, money.format(amount), PAGE_W - MARGIN, y, 10f, INK, align = Paint.Align.RIGHT)
                    y += 16f
                }
                if (rows.size > room) {
                    pen.text(c, "and ${rows.size - room} more", MARGIN + 52f, y, 9f, MUTED)
                }
            }
        }
        pen.footer(c, pageNo)
        doc.finishPage(page)

        // Optional: every entry, on as many pages as it takes.
        if (includeEntries && s.entries.isNotEmpty()) {
            page = newPage()
            c = page.canvas
            c.drawColor(Color.WHITE)
            y = MARGIN + 10f
            y = pen.section(c, "Every entry", y)
            s.entries.forEach { t ->
                if (y > PAGE_H - 60f) {
                    pen.footer(c, pageNo)
                    doc.finishPage(page)
                    page = newPage()
                    c = page.canvas
                    c.drawColor(Color.WHITE)
                    y = MARGIN + 10f
                }
                pen.text(c, t.date.format(day), MARGIN, y, 9f, MUTED)
                val what = if (includeNotes && t.note.isNotBlank()) "${t.categoryName} · ${t.note}" else t.categoryName
                pen.text(c, what, MARGIN + 52f, y, 9.5f, INK, maxWidth = PAGE_W - 2 * MARGIN - 52f - 100f)
                pen.text(c, (if (t.type == TxType.INCOME) "+" else "") + money.format(t.amountMinor), PAGE_W - MARGIN, y, 9.5f, INK, align = Paint.Align.RIGHT)
                y += 14f
            }
            pen.footer(c, pageNo)
            doc.finishPage(page)
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "leftovers_statement_${s.month ?: s.year}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Leftovers · $title statement")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share statement")
    }

    /** Drawing helpers for the page. */
    private class Pen(regular: Typeface, boldFace: Typeface) {
        // Manrope is a variable font whose default is its lightest weight, so each paint sets its weight once.
        // (Changing the typeface on one paint drops the weight, and setting the same weight again is ignored.)
        private val plain = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; fontVariationSettings = "'wght' 460" }
        private val heavy = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFace; fontVariationSettings = "'wght' 700" }
        private val shape = Paint(Paint.ANTI_ALIAS_FLAG)

        fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float? = null) {
            val paint = if (bold) heavy else plain
            paint.textSize = size
            paint.color = color
            paint.textAlign = align
            val shown = if (maxWidth != null) TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString() else value
            c.drawText(shown, x, y, paint)
        }

        /** A section heading with a hairline under it; returns where the section's first line goes. */
        fun section(c: Canvas, title: String, y: Float): Float {
            text(c, title, MARGIN, y + 8f, 12f, INK, bold = true)
            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 0.6f
            shape.color = LINE
            c.drawLine(MARGIN, y + 16f, PAGE_W - MARGIN, y + 16f, shape)
            return y + 34f
        }

        fun box(c: Canvas, r: RectF) {
            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 0.8f
            shape.color = LINE
            c.drawRoundRect(r, 8f, 8f, shape)
        }

        fun bar(c: Canvas, x: Float, y: Float, width: Float, height: Float, fraction: Float) {
            shape.style = Paint.Style.FILL
            shape.color = BAR_BACK
            c.drawRoundRect(RectF(x, y, x + width, y + height), height / 2, height / 2, shape)
            shape.color = BAR
            c.drawRoundRect(RectF(x, y, x + (width * fraction).coerceAtLeast(height), y + height), height / 2, height / 2, shape)
        }

        fun footer(c: Canvas, page: Int) {
            text(c, "Made on this phone with Leftovers · page $page", PAGE_W / 2f, PAGE_H - 24f, 8f, MUTED, align = Paint.Align.CENTER)
        }
    }
}
