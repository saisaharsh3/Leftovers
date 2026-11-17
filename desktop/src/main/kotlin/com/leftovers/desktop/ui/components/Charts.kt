package com.leftovers.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A ring of slices that draws itself in once, then lets a slice be pointed at: it thickens, and the rest fade.
 * [highlight] comes from outside too, so pointing at a row in the list lights its slice.
 */
@Composable
fun Donut(values: List<Pair<Long, Color>>, highlight: Int?, onHover: (Int?) -> Unit, modifier: Modifier = Modifier, thickness: Dp = 22.dp) {
    val c = T.c
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.settle()) }
    val total = values.sumOf { it.first }.coerceAtLeast(1)
    val focus = values.indices.map { i -> animateFloatAsState(if (highlight == null || highlight == i) 1f else 0.3f, Motion.quick()) }
    val wide = values.indices.map { i -> animateFloatAsState(if (highlight == i) 1f else 0f, Motion.moved()) }
    Canvas(
        modifier.pointerInput(values) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val p = e.changes.first().position
                    if (e.type == PointerEventType.Exit) { onHover(null); continue }
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val r = minOf(size.width, size.height) / 2f
                    val d = hypot(p.x - cx, p.y - cy)
                    if (d < r - thickness.toPx() * 1.6f || d > r) { onHover(null); continue }
                    var angle = Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat() + 90f
                    if (angle < 0) angle += 360f
                    var acc = 0f
                    var hit: Int? = null
                    values.forEachIndexed { i, (v, _) ->
                        val sweep = v.toFloat() / total * 360f
                        if (angle >= acc && angle < acc + sweep) hit = i
                        acc += sweep
                    }
                    onHover(hit)
                }
            }
        },
    ) {
        val stroke = thickness.toPx()
        val inset = stroke * 0.8f
        val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        drawArc(c.raised, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        var start = -90f
        val gap = if (values.size > 1) 1.6f else 0f
        values.forEachIndexed { i, (v, color) ->
            val sweep = v.toFloat() / total * 360f * grow.value
            if (sweep > gap) {
                drawArc(
                    color.copy(alpha = focus[i].value), start + gap / 2, sweep - gap, false, topLeft, arcSize,
                    style = Stroke(stroke * (1f + 0.35f * wide[i].value), cap = StrokeCap.Butt),
                )
            }
            start += sweep
        }
    }
}

/**
 * A bar for each day of the month, growing in from the bottom. Pointing at a day shows what it was, and
 * [over] marks days above the daily budget.
 */
@Composable
fun DayBars(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    today: Int? = null,
    over: Long? = null,
    label: (Int) -> String,
) {
    val c = T.c
    val grow = remember(values.size) { Animatable(0f) }
    LaunchedEffect(values.size) { grow.animateTo(1f, Motion.settle()) }
    var hover by remember { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(0) }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(over ?: 1L).coerceAtLeast(1L)
    val heights = values.map { v -> animateFloatAsState(v.toFloat() / max, Motion.settle()) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { width = it.width }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(values.size) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.first().position.x
                        hover = if (e.type == PointerEventType.Exit || values.isEmpty()) null
                        else (x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex)
                    }
                }
            },
        ) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val bar = (slot * 0.62f).coerceAtMost(14.dp.toPx())
            val top = 22.dp.toPx()
            val room = size.height - top
            over?.let { o ->
                val y = top + room * (1f - o.toFloat() / max)
                drawLine(c.text3.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            values.forEachIndexed { i, v ->
                val h = (room * heights[i].value * grow.value).coerceAtLeast(if (v > 0) 3.dp.toPx() else 1.5.dp.toPx())
                val x = slot * i + (slot - bar) / 2
                val base = when {
                    today != null && i > today -> c.raised
                    v == 0L -> c.raised
                    over != null && v > over -> c.negative
                    else -> color
                }
                val dim = hover != null && hover != i
                drawRoundRect(base.copy(alpha = if (dim) base.alpha * 0.4f else base.alpha), Offset(x, size.height - h), Size(bar, h), CornerRadius(bar / 2))
            }
        }
        hover?.let { i ->
            val slot = if (values.isEmpty()) 0f else width.toFloat() / values.size
            val x = with(density) { (slot * i + slot / 2).toDp() }
            Box(Modifier.offset { IntOffset((with(density) { x.toPx() } - 60.dp.toPx()).roundToInt().coerceIn(0, (width - 120.dp.toPx()).roundToInt().coerceAtLeast(0)), 0) }) {
                Text(
                    label(i), style = T.type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (c.dark) Color(0xFF2A2A33) else Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A row of a ranking: the name, a bar of its share, and the amount. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.settle())
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(T.c.raised)) {
        Box(Modifier.fillMaxWidth(f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}
