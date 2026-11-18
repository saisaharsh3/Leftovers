package com.leftovers.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.leftovers.app.ai.ChartData
import com.leftovers.app.ai.ChartKind
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * A chart from the chat as a picture to save or send: the same chart, drawn on white so it reads well in a message,
 * a document or on paper. Its title, period and total go above it.
 */
object ChartImage {
    private const val W = 1200
    private const val PAD = 64f
    private const val INK = 0xFF1C1C1E.toInt()
    private const val MUTED = 0xFF6B6B70.toInt()
    private const val GRID = 0xFFE6E6EA.toInt()
    private const val MAIN = 0xFF4E7D1F.toInt()
    private const val IN = 0xFF2E9E6A.toInt()
    private const val OUT = 0xFF9AA0A6.toInt()

    fun png(chart: ChartData, money: Money): ByteArray {
        val rows = if (chart.kind == ChartKind.RANKED) chart.points.size else 0
        val h = when (chart.kind) {
            ChartKind.RANKED -> 300 + rows * 96
            ChartKind.DONUT -> 300 + max(520, chart.points.size * 64 + 40)
            else -> 820
        }
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())
        val title = text(56f, INK, bold = true)
        val sub = text(32f, MUTED)
        c.drawText(chart.title, PAD, PAD + 52f, title)
        c.drawText(chart.subtitle + (chart.total?.let { "  ·  Total ${money.format(it)}" } ?: ""), PAD, PAD + 108f, sub)
        val top = PAD + 170f
        val area = RectF(PAD, top, W - PAD, h - PAD)
        when (chart.kind) {
            ChartKind.BARS -> bars(c, chart, money, area)
            ChartKind.LINE -> line(c, chart, money, area)
            ChartKind.PAIR -> pairs(c, chart, money, area)
            ChartKind.DONUT -> donut(c, chart, money, area)
            ChartKind.RANKED -> ranked(c, chart, money, area)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = align
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** Round gridline steps from zero (or below) to the top, with their short amounts on the right. */
    private fun grid(c: Canvas, area: RectF, lo: Long, hi: Long, money: Money): (Long) -> Float {
        val ticks = com.leftovers.app.ui.components.niceTicks(lo, hi)
        val plot = RectF(area.left, area.top, area.right - 120f, area.bottom - 56f)
        val min = ticks.first().toFloat()
        val maxV = ticks.last().toFloat()
        fun y(v: Long) = plot.bottom - (plot.height()) * ((v - min) / (maxV - min).coerceAtLeast(1f))
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID; strokeWidth = 2f }
        val label = text(26f, MUTED)
        ticks.forEach { t ->
            c.drawLine(plot.left, y(t), plot.right, y(t), line)
            c.drawText(money.compact(t), plot.right + 16f, y(t) + 9f, label)
        }
        return ::y
    }

    private fun plotRight(area: RectF) = area.right - 120f

    /** Labels along the bottom, thinned to about six. */
    private fun axis(c: Canvas, labels: List<String>, xs: (Int) -> Float, bottom: Float) {
        val every = ((labels.size + 5) / 6).coerceAtLeast(1)
        val p = text(26f, MUTED, align = Paint.Align.CENTER)
        labels.forEachIndexed { i, l -> if (i % every == 0) c.drawText(l, xs(i), bottom, p) }
    }

    private fun bars(c: Canvas, chart: ChartData, money: Money, area: RectF) {
        val values = chart.points.map { it.value }
        val y = grid(c, area, 0, values.maxOrNull() ?: 0, money)
        val right = plotRight(area)
        val slot = (right - area.left) / values.size.coerceAtLeast(1)
        val w = (slot * 0.62f).coerceAtMost(44f)
        val paint = fill(MAIN)
        values.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val x = area.left + slot * i + (slot - w) / 2
            topRounded(c, RectF(x, y(v), x + w, y(0)), w / 2, paint)
        }
        axis(c, chart.points.map { it.label }, { area.left + slot * it + slot / 2 }, area.bottom - 8f)
    }

    private fun line(c: Canvas, chart: ChartData, money: Money, area: RectF) {
        val values = chart.points.map { it.value }
        if (values.size < 2) return bars(c, chart, money, area)
        val y = grid(c, area, values.min(), values.max(), money)
        val right = plotRight(area)
        fun x(i: Int) = area.left + (right - area.left) * i / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        val under = Path(path).apply { lineTo(x(values.lastIndex), y(minOf(values.min(), 0))); lineTo(x(0), y(minOf(values.min(), 0))); close() }
        c.drawPath(under, fill(0x224E7D1F))
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MAIN; style = Paint.Style.STROKE; strokeWidth = 6f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
        c.drawCircle(x(values.lastIndex), y(values.last()), 10f, fill(MAIN))
        axis(c, chart.points.map { it.label }, ::x, area.bottom - 8f)
    }

    private fun pairs(c: Canvas, chart: ChartData, money: Money, area: RectF) {
        val y = grid(c, area, 0, chart.points.maxOfOrNull { max(it.value, it.second) } ?: 0, money)
        val right = plotRight(area)
        val slot = (right - area.left) / chart.points.size.coerceAtLeast(1)
        val w = (slot * 0.26f).coerceAtMost(40f)
        chart.points.forEachIndexed { i, p ->
            val cx = area.left + slot * i + slot / 2
            if (p.value > 0) topRounded(c, RectF(cx - w - 4f, y(p.value), cx - 4f, y(0)), w / 2, fill(IN))
            if (p.second > 0) topRounded(c, RectF(cx + 4f, y(p.second), cx + w + 4f, y(0)), w / 2, fill(OUT))
        }
        axis(c, chart.points.map { it.label }, { area.left + slot * it + slot / 2 }, area.bottom - 8f)
        // The key, top right.
        val key = text(28f, INK)
        val kx = right - 260f
        c.drawCircle(kx, area.top - 30f, 10f, fill(IN)); c.drawText(chart.firstName, kx + 20f, area.top - 20f, key)
        c.drawCircle(kx + 120f, area.top - 30f, 10f, fill(OUT)); c.drawText(chart.secondName, kx + 140f, area.top - 20f, key)
    }

    private fun donut(c: Canvas, chart: ChartData, money: Money, area: RectF) {
        val total = chart.points.sumOf { it.value }.coerceAtLeast(1)
        val size = 440f
        val ring = RectF(area.left + 20f, area.top + 20f, area.left + 20f + size, area.top + 20f + size)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 72f }
        var start = -90f
        chart.points.forEachIndexed { i, p ->
            val sweep = 360f * p.value / total
            stroke.color = colorOf(p.color, i)
            c.drawArc(RectF(ring.left + 36f, ring.top + 36f, ring.right - 36f, ring.bottom - 36f), start, (sweep - 1.5f).coerceAtLeast(0.5f), false, stroke)
            start += sweep
        }
        val name = text(32f, INK)
        val value = text(32f, INK, bold = true, align = Paint.Align.RIGHT)
        val pct = text(28f, MUTED, align = Paint.Align.RIGHT)
        val lx = ring.right + 64f
        chart.points.forEachIndexed { i, p ->
            val ly = area.top + 50f + i * 64f
            c.drawCircle(lx, ly - 10f, 11f, fill(colorOf(p.color, i)))
            c.drawText(fit(p.label, 16), lx + 28f, ly, name)
            c.drawText(money.formatWhole(p.value), area.right - 110f, ly, value)
            c.drawText("${(p.value * 100 / total)}%", area.right, ly, pct)
        }
    }

    private fun ranked(c: Canvas, chart: ChartData, money: Money, area: RectF) {
        val max = chart.points.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
        val name = text(32f, INK)
        val value = text(32f, INK, bold = true, align = Paint.Align.RIGHT)
        chart.points.forEachIndexed { i, p ->
            val y = area.top + 10f + i * 96f
            c.drawText(fit(p.label, 28), area.left, y + 30f, name)
            c.drawText(money.format(p.value), area.right, y + 30f, value)
            val track = RectF(area.left, y + 50f, area.right, y + 64f)
            c.drawRoundRect(track, 7f, 7f, fill(GRID))
            c.drawRoundRect(RectF(track.left, track.top, track.left + track.width() * p.value / max, track.bottom), 7f, 7f, fill(colorOf(p.color, i)))
        }
    }

    private fun topRounded(c: Canvas, r: RectF, radius: Float, paint: Paint) {
        val rr = radius.coerceAtMost(r.height())
        val path = Path().apply { addRoundRect(r, floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f), Path.Direction.CW) }
        c.drawPath(path, paint)
    }

    private val spare = intArrayOf(0xFF4E7D1F.toInt(), 0xFF3B82C4.toInt(), 0xFFE08A3C.toInt(), 0xFF9B59B6.toInt(), 0xFF2E9E6A.toInt(), 0xFFD9534F.toInt(), 0xFF7F8C8D.toInt(), 0xFFB8860B.toInt())

    private fun colorOf(color: Long?, i: Int): Int = color?.toInt()?.let { it or 0xFF000000.toInt() } ?: spare[i % spare.size]

    private fun fit(s: String, n: Int) = if (s.length <= n) s else s.take(n - 1) + "…"
}
