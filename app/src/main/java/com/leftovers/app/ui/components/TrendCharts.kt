package com.leftovers.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leftovers.app.ui.theme.LocalAppColors
import kotlin.math.abs

/**
 * A tap picks the point under the finger (a tap on the one showing hides it), and sliding sideways follows it.
 * A drag that's mostly up or down is left to the page, so scrolling past a chart doesn't leave a label on it.
 * [index] turns an x position into a point.
 */
private fun Modifier.scrub(key: Any, touched: MutableState<Int?>, index: (x: Float, width: Float) -> Int): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var following = false
        var scrolled = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            if (!change.pressed) {
                // A tap: show this point, or hide it if it's the one showing.
                if (!following && !scrolled) {
                    val i = index(down.position.x, size.width.toFloat())
                    touched.value = if (touched.value == i) null else i
                }
                break
            }
            if (scrolled || change.isConsumed) { scrolled = true; continue }
            val moved = change.position - down.position
            if (!following) {
                if (abs(moved.y) > slop && abs(moved.y) >= abs(moved.x)) { scrolled = true; continue }
                if (abs(moved.x) > slop) following = true else continue
            }
            change.consume()
            touched.value = index(change.position.x, size.width.toFloat())
        }
    }
}

/** The label over a touched point, kept inside the chart. */
private fun DrawScope.tip(measurer: TextMeasurer, text: String, style: TextStyle, bg: Color, centre: Float, height: Float) {
    val layout = measurer.measure(text, style)
    val padX = 10.dp.toPx()
    val w = layout.size.width + padX * 2
    val left = (centre - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    drawRoundRect(bg, Offset(left, 0f), Size(w, height - 4.dp.toPx()), CornerRadius(12.dp.toPx(), 12.dp.toPx()))
    drawText(layout, topLeft = Offset(left + padX, (height - 4.dp.toPx() - layout.size.height) / 2))
}

/**
 * A line that draws itself in from the left, filled softly underneath, like a balance over time. Touching it shows
 * [tooltip] for that point; [axis] gives the few labels along the bottom (null for none at that point).
 */
@Composable
fun TrendLine(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    axis: (Int) -> String? = { null },
    tooltip: (Int) -> String,
) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.textTertiary, fontSize = 10.sp)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = c.textPrimary)
    val tipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val touched = remember(values) { mutableStateOf<Int?>(null) }
    val count = values.size
    Canvas(modifier.scrub(count, touched) { x, w -> if (count < 2) 0 else (x / (w / (count - 1)) + 0.5f).toInt().coerceIn(0, count - 1) }) {
        if (count < 2) return@Canvas
        val tipH = 30.dp.toPx()
        val labelH = 18.dp.toPx()
        val top = tipH + 6.dp.toPx()
        val room = size.height - top - labelH
        val max = values.max()
        val min = values.min()
        val span = (max - min).coerceAtLeast(1L).toFloat()
        fun x(i: Int) = size.width * i / (count - 1)
        fun y(v: Long) = top + room * (1f - (v - min) / span)
        // Where zero is, when the line goes below it.
        if (min < 0 && max > 0) drawLine(c.textTertiary.copy(alpha = 0.4f), Offset(0f, y(0)), Offset(size.width, y(0)), 1.dp.toPx())
        val shown = (count * grow.value).toInt().coerceIn(2, count)
        val path = Path()
        for (i in 0 until shown) if (i == 0) path.moveTo(x(i), y(values[i])) else path.lineTo(x(i), y(values[i]))
        val area = Path().apply { addPath(path); lineTo(x(shown - 1), top + room); lineTo(0f, top + room); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0f)), startY = top, endY = top + room))
        drawPath(path, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (i in 0 until count) {
            val text = axis(i) ?: continue
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset((x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), top + room + 4.dp.toPx()))
        }
        touched.value?.let { i ->
            drawLine(c.textTertiary, Offset(x(i), tipH), Offset(x(i), top + room), 1.dp.toPx())
            drawCircle(color, 5.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(tipBg, 2.2.dp.toPx(), Offset(x(i), y(values[i])))
            tip(measurer, tooltip(i), tipStyle, tipBg, x(i), tipH)
        } ?: run {
            // The last point, so today's balance stands out.
            drawCircle(color, 4.dp.toPx(), Offset(x(shown - 1), y(values[shown - 1])))
        }
    }
}

/**
 * Two bars for each month, side by side (money in, money out), rising in. Touching a month shows [tooltip] for it;
 * [axis] labels each month underneath.
 */
@Composable
fun PairBars(
    pairs: List<Pair<Long, Long>>,
    first: Color,
    second: Color,
    modifier: Modifier = Modifier,
    axis: (Int) -> String,
    tooltip: (Int) -> String,
) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.textTertiary, fontSize = 10.sp)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = c.textPrimary)
    val tipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val grow = remember(pairs) { Animatable(0f) }
    LaunchedEffect(pairs) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val touched = remember(pairs) { mutableStateOf<Int?>(null) }
    Canvas(modifier.scrub(pairs.size, touched) { x, w -> if (pairs.isEmpty()) 0 else (x / (w / pairs.size)).toInt().coerceIn(0, pairs.lastIndex) }) {
        if (pairs.isEmpty()) return@Canvas
        val tipH = 30.dp.toPx()
        val labelH = 18.dp.toPx()
        val room = size.height - tipH - labelH - 6.dp.toPx()
        val base = size.height - labelH
        val max = pairs.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1L).toFloat()
        val slot = size.width / pairs.size
        val bar = (slot * 0.28f).coerceAtMost(14.dp.toPx())
        val gap = 3.dp.toPx()
        // Labels thin out when there are many months.
        val every = ((pairs.size + 5) / 6).coerceAtLeast(1)
        pairs.forEachIndexed { i, (a, b) ->
            val dim = touched.value != null && touched.value != i
            val local = ((grow.value * pairs.size * 1.6f) - i * 0.6f).coerceIn(0f, 1f)
            val cx = slot * i + slot / 2
            fun barAt(v: Long, left: Float, color: Color) {
                val h = if (v > 0) (room * v / max * local).coerceAtLeast(bar / 2) else 2.dp.toPx()
                drawRoundRect(color.copy(alpha = if (dim) 0.35f else 1f), Offset(left, base - h), Size(bar, h), CornerRadius(bar / 2, bar / 2))
            }
            barAt(a, cx - bar - gap / 2, first)
            barAt(b, cx + gap / 2, second)
            if (i % every == 0 || i == pairs.lastIndex && pairs.size <= 6) {
                val layout = measurer.measure(axis(i), labelStyle)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, base + 4.dp.toPx()))
            }
        }
        touched.value?.let { i -> tip(measurer, tooltip(i), tipStyle, tipBg, slot * i + slot / 2, tipH) }
    }
}
