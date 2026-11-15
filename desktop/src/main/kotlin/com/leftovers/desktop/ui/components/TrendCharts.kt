package com.leftovers.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate
import kotlin.math.roundToInt

/** One line on a [LineChart]. */
data class Series(val values: List<Long>, val color: Color, val fill: Boolean = false, val dashed: Boolean = false)

/** The small label that follows the pointer over a chart, kept inside the chart's width. */
@Composable
private fun PointerLabel(text: String, centreX: Float, width: Int, half: Int = 100) {
    val c = T.c
    Box(Modifier.offset { IntOffset((centreX - half.dp.toPx()).roundToInt().coerceIn(0, (width - (half * 2).dp.toPx()).roundToInt().coerceAtLeast(0)), 0) }) {
        Text(
            text, style = T.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (c.dark) Color(0xFF2A2A33) else Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Lines that draw themselves in from the left. Pointing anywhere shows a guide at that point and [label] for it;
 * [count] is how many points the x axis holds (a series can be shorter, like this month so far).
 */
@Composable
fun LineChart(series: List<Series>, count: Int, modifier: Modifier = Modifier, label: (Int) -> String) {
    val c = T.c
    val grow = remember(count) { Animatable(0f) }
    LaunchedEffect(count) { grow.animateTo(1f, Motion.settle()) }
    var hover by remember { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(0) }
    val all = series.flatMap { it.values }
    val max = (all.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val min = minOf(all.minOrNull() ?: 0L, 0L)
    Box(modifier.onSizeChanged { width = it.width }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(count) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.first().position.x
                        hover = if (e.type == PointerEventType.Exit || count < 2) null
                        else (x / (size.width.toFloat() / (count - 1)) + 0.5f).toInt().coerceIn(0, count - 1)
                    }
                }
            },
        ) {
            if (count < 2) return@Canvas
            val top = 26.dp.toPx()
            val room = size.height - top - 2.dp.toPx()
            fun x(i: Int) = size.width * i / (count - 1)
            fun y(v: Long) = top + room * (1f - (v - min).toFloat() / (max - min).coerceAtLeast(1))
            if (min < 0) drawLine(c.line, Offset(0f, y(0)), Offset(size.width, y(0)), 1.dp.toPx())
            series.forEach { s ->
                if (s.values.size < 2) return@forEach
                val shown = (s.values.size * grow.value).toInt().coerceIn(2, s.values.size)
                val path = Path()
                s.values.take(shown).forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                if (s.fill) {
                    val area = Path().apply { addPath(path); lineTo(x(shown - 1), size.height); lineTo(0f, size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.22f), s.color.copy(alpha = 0f))))
                }
                drawPath(
                    path, s.color,
                    style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null),
                )
            }
            hover?.let { i ->
                drawLine(c.text3.copy(alpha = 0.5f), Offset(x(i), top), Offset(x(i), size.height), 1.dp.toPx())
                series.forEach { s ->
                    s.values.getOrNull(i)?.let { v ->
                        drawCircle(s.color, 4.5.dp.toPx(), Offset(x(i), y(v)))
                        drawCircle(if (c.dark) Color(0xFF16161C) else Color.White, 2.dp.toPx(), Offset(x(i), y(v)))
                    }
                }
            }
        }
        hover?.let { i -> PointerLabel(label(i), if (count < 2) 0f else width.toFloat() * i / (count - 1), width, 110) }
    }
}

/** Two bars a month, side by side (money in and money out), growing in; pointing at a month says what it was. */
@Composable
fun PairBars(pairs: List<Pair<Long, Long>>, first: Color, second: Color, modifier: Modifier = Modifier, label: (Int) -> String) {
    val grow = remember(pairs.size) { Animatable(0f) }
    LaunchedEffect(pairs.size) { grow.animateTo(1f, Motion.settle()) }
    var hover by remember { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(0) }
    val max = pairs.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1L) ?: 1L
    Box(modifier.onSizeChanged { width = it.width }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(pairs.size) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.first().position.x
                        hover = if (e.type == PointerEventType.Exit || pairs.isEmpty()) null else (x / (size.width.toFloat() / pairs.size)).toInt().coerceIn(0, pairs.lastIndex)
                    }
                }
            },
        ) {
            if (pairs.isEmpty()) return@Canvas
            val slot = size.width / pairs.size
            val bar = (slot * 0.3f).coerceAtMost(18.dp.toPx())
            val gap = 3.dp.toPx()
            val room = size.height - 26.dp.toPx()
            pairs.forEachIndexed { i, (a, b) ->
                val dim = hover != null && hover != i
                val cx = slot * i + slot / 2
                fun barAt(v: Long, left: Float, color: Color) {
                    val h = (room * v / max * grow.value).coerceAtLeast(if (v > 0) 2.dp.toPx() else 0f)
                    drawRoundRect(color.copy(alpha = if (dim) 0.35f else 1f), Offset(left, size.height - h), Size(bar, h), CornerRadius(bar / 3))
                }
                barAt(a, cx - bar - gap / 2, first)
                barAt(b, cx + gap / 2, second)
            }
        }
        hover?.let { i ->
            val slot = if (pairs.isEmpty()) 0f else width.toFloat() / pairs.size
            PointerLabel(label(i), slot * i + slot / 2, width)
        }
    }
}

/** A square for every day of a year, a week to a column, darker the more was spent; pointing at one says it. */
@Composable
fun YearHeatmap(days: List<Pair<LocalDate, Long>>, color: Color, modifier: Modifier = Modifier, label: (Int) -> String) {
    val c = T.c
    var hover by remember { mutableStateOf<Int?>(null) }
    val spending = remember(days) { days.map { it.second }.filter { it > 0 }.sorted() }
    // Shaded by where a day falls among spending days, so one big day doesn't wash out the rest.
    fun level(v: Long): Float = if (v <= 0 || spending.isEmpty()) 0f
    else (spending.binarySearch(v).let { if (it < 0) -it - 1 else it } + 1).toFloat() / spending.size
    val lead = days.firstOrNull()?.first?.dayOfWeek?.value?.minus(1) ?: 0
    val weeks = (lead + days.size + 6) / 7
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(130.dp).pointerInput(days.size) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val p = e.changes.first().position
                        if (e.type == PointerEventType.Exit) { hover = null; continue }
                        val col = (p.x / (size.width.toFloat() / weeks)).toInt()
                        val row = (p.y / (size.height.toFloat() / 7)).toInt().coerceIn(0, 6)
                        hover = (col * 7 + row - lead).takeIf { it in days.indices }
                    }
                }
            },
        ) {
            val cell = size.width / weeks
            val rowH = size.height / 7
            val side = minOf(cell, rowH) * 0.8f
            days.forEachIndexed { i, (_, v) ->
                val slot = i + lead
                val x = (slot / 7) * cell + (cell - side) / 2
                val y = (slot % 7) * rowH + (rowH - side) / 2
                val l = level(v)
                drawRoundRect(if (l == 0f) c.raised else color.copy(alpha = 0.2f + 0.8f * l), Offset(x, y), Size(side, side), CornerRadius(side / 4))
                if (hover == i) drawRoundRect(c.text, Offset(x, y), Size(side, side), CornerRadius(side / 4), style = Stroke(1.5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(hover?.let(label) ?: "Point at a day to see it", style = T.type.small, color = if (hover != null) c.text else c.text3)
    }
}
