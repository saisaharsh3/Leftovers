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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leftovers.app.ui.theme.LocalAppColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The charts on Insights share one frame: a tooltip strip on top, the plot, a few faint gridlines with short amounts
 * in a gutter on the right, and dates along the bottom. Touching picks a point; sliding sideways follows the finger.
 */

private val TipHeight = 34.dp
private val AxisHeight = 20.dp
private val Gutter = 44.dp

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

/** Round steps for the gridlines (1, 2, 2.5 or 5 times a power of ten), from [min] (or zero) to [max] in about three. */
internal fun niceTicks(min: Long, max: Long, count: Int = 3): List<Long> {
    val lo = minOf(min, 0L).toDouble()
    val hi = maxOf(max.toDouble(), lo + 1)
    val raw = (hi - lo) / count
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val first = floor(lo / step) * step
    val last = ceil(hi / step) * step
    val ticks = ArrayList<Long>()
    var v = first
    while (v <= last + step / 2) { ticks += Math.round(v); v += step }
    return ticks
}

/** The plot's frame for one draw: where it sits, and where an amount lands on it. */
private class Frame(scope: DrawScope, ticks: List<Long>) {
    val left = 0f
    val right = scope.size.width - with(scope) { Gutter.toPx() }
    val top = with(scope) { TipHeight.toPx() + 6.dp.toPx() }
    val bottom = scope.size.height - with(scope) { AxisHeight.toPx() }
    private val lo = ticks.first().toFloat()
    private val hi = ticks.last().toFloat()
    fun y(v: Long): Float = bottom - (bottom - top) * ((v - lo) / (hi - lo).coerceAtLeast(1f))
}

/** Faint gridlines at [ticks], each with its short amount in the gutter. */
private fun DrawScope.grid(frame: Frame, ticks: List<Long>, measurer: TextMeasurer, style: TextStyle, line: Color, label: (Long) -> String) {
    ticks.forEach { t ->
        val y = frame.y(t)
        drawLine(line, Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
        val layout = measurer.measure(label(t), style)
        drawText(layout, topLeft = Offset(frame.right + 8.dp.toPx(), y - layout.size.height / 2f))
    }
}

/** The label over a touched point: what it is in grey, then the amount, kept inside the chart. */
private fun DrawScope.tip(measurer: TextMeasurer, title: String, value: String, base: TextStyle, muted: Color, strong: Color, bg: Color, centre: Float) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) { append(title) }
        append("   ")
        withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(value) }
    }
    val layout = measurer.measure(text, base)
    val padX = 12.dp.toPx()
    val h = TipHeight.toPx() - 6.dp.toPx()
    val w = layout.size.width + padX * 2
    val left = (centre - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    drawRoundRect(bg, Offset(left, 0f), Size(w, h), CornerRadius(h / 2, h / 2))
    drawText(layout, topLeft = Offset(left + padX, (h - layout.size.height) / 2))
}

/** A bar standing on the baseline, rounded on top only. */
private fun DrawScope.bar(color: Color, left: Float, bottom: Float, width: Float, height: Float) {
    if (height <= 0f) return
    val r = (width / 2).coerceAtMost(height)
    val path = Path().apply {
        addRoundRect(RoundRect(Rect(left, bottom - height, left + width, bottom), topLeft = CornerRadius(r, r), topRight = CornerRadius(r, r)))
    }
    drawPath(path, color)
}

/** A smooth line through the first [count] points that never overshoots them (monotone cubic): flat stays flat. */
private fun smoothPath(xs: FloatArray, ys: FloatArray, count: Int): Path {
    val path = Path()
    if (count == 0) return path
    path.moveTo(xs[0], ys[0])
    if (count == 1) return path
    val d = FloatArray(count - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val m = FloatArray(count) { i ->
        when (i) {
            0 -> d[0]
            count - 1 -> d[count - 2]
            else -> if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2
        }
    }
    for (i in 0 until count - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) { val t = 3f / sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    for (i in 0 until count - 1) {
        val h = xs[i + 1] - xs[i]
        path.cubicTo(xs[i] + h / 3, ys[i] + m[i] * h / 3, xs[i + 1] - h / 3, ys[i + 1] - m[i + 1] * h / 3, xs[i + 1], ys[i + 1])
    }
    return path
}

private class ChartStyles(val axis: TextStyle, val tip: TextStyle, val muted: Color, val strong: Color, val tipBg: Color, val line: Color, val guide: Color)

@Composable
private fun chartStyles(): ChartStyles {
    val c = LocalAppColors.current
    return ChartStyles(
        axis = MaterialTheme.typography.labelSmall.copy(color = c.textTertiary, fontSize = 10.sp),
        tip = MaterialTheme.typography.labelMedium,
        muted = c.textSecondary,
        strong = c.textPrimary,
        tipBg = MaterialTheme.colorScheme.surfaceContainerHighest,
        line = c.textTertiary.copy(alpha = 0.16f),
        guide = c.textTertiary.copy(alpha = 0.7f),
    )
}

/**
 * A smooth line that draws itself in from the left, softly filled underneath: a balance over time, or a running
 * total. [reference] adds a dashed level (a budget, say) named [referenceLabel]. Touching shows [title] and [value]
 * for that point; [axis] gives the few dates along the bottom and [scale] the amounts at the side.
 */
@Composable
fun TrendLine(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    axis: (Int) -> String? = { null },
    title: (Int) -> String,
    value: (Int) -> String,
    scale: (Long) -> String,
    reference: Long? = null,
    referenceLabel: String? = null,
    /** How many points the bottom axis holds; more than [values] when the line stops short, like a month so far. */
    span: Int = values.size,
) {
    val st = chartStyles()
    val measurer = rememberTextMeasurer()
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val touched = remember(values) { mutableStateOf<Int?>(null) }
    val count = values.size
    val gutter = with(LocalDensity.current) { Gutter.toPx() }
    val slots = maxOf(span, count)
    Canvas(modifier.scrub(count, touched) { x, w -> if (count < 2) 0 else (x / ((w - gutter) / (slots - 1)) + 0.5f).toInt().coerceIn(0, count - 1) }) {
        if (count < 2) return@Canvas
        val ticks = niceTicks(minOf(values.min(), reference ?: values.min()), maxOf(values.max(), reference ?: values.max()))
        val f = Frame(this, ticks)
        grid(f, ticks, measurer, st.axis, st.line, scale)
        fun x(i: Int) = f.left + (f.right - f.left) * i / (slots - 1)
        reference?.let { r ->
            val y = f.y(r)
            drawLine(st.guide, Offset(f.left, y), Offset(f.right, y), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            referenceLabel?.let { text ->
                val layout = measurer.measure(text, st.axis.copy(color = st.muted))
                drawText(layout, topLeft = Offset(f.left + 2.dp.toPx(), y - layout.size.height - 2.dp.toPx()))
            }
        }
        val shown = (count * grow.value).toInt().coerceIn(2, count)
        val xs = FloatArray(count) { x(it) }
        val ys = FloatArray(count) { f.y(values[it]) }
        val path = smoothPath(xs, ys, shown)
        val area = Path().apply { addPath(path); lineTo(xs[shown - 1], f.bottom); lineTo(xs[0], f.bottom); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), startY = f.top, endY = f.bottom))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (i in 0 until slots) {
            val text = axis(i) ?: continue
            val layout = measurer.measure(text, st.axis)
            drawText(layout, topLeft = Offset((x(i) - layout.size.width / 2f).coerceIn(0f, f.right - layout.size.width), f.bottom + 5.dp.toPx()))
        }
        val i = touched.value
        if (i != null) {
            drawLine(st.guide, Offset(xs[i], TipHeight.toPx() - 6.dp.toPx()), Offset(xs[i], f.bottom), 1.dp.toPx())
            drawCircle(color, 5.dp.toPx(), Offset(xs[i], ys[i]))
            drawCircle(st.tipBg, 2.2.dp.toPx(), Offset(xs[i], ys[i]))
            tip(measurer, title(i), value(i), st.tip, st.muted, st.strong, st.tipBg, xs[i])
        } else if (shown == count) {
            // Where it ends stands out a little.
            drawCircle(color.copy(alpha = 0.25f), 7.dp.toPx(), Offset(xs[count - 1], ys[count - 1]))
            drawCircle(color, 3.5.dp.toPx(), Offset(xs[count - 1], ys[count - 1]))
        }
    }
}

/**
 * Columns on a baseline, one per day or month, rising in from the left. [highlight] (today) is brighter; touching
 * one shows [title] and [value] for it. Days with nothing draw nothing, so the shape reads at a glance.
 */
@Composable
fun ColumnChart(
    values: List<Long>,
    highlight: Int?,
    modifier: Modifier = Modifier,
    axis: (Int) -> String?,
    title: (Int) -> String,
    value: (Int) -> String,
    scale: (Long) -> String,
) {
    val c = LocalAppColors.current
    val st = chartStyles()
    val measurer = rememberTextMeasurer()
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val touched = remember(values) { mutableStateOf<Int?>(null) }
    val gutter = with(LocalDensity.current) { Gutter.toPx() }
    Canvas(modifier.scrub(values.size, touched) { x, w -> if (values.isEmpty()) 0 else (x / ((w - gutter) / values.size)).toInt().coerceIn(0, values.lastIndex) }) {
        if (values.isEmpty()) return@Canvas
        val ticks = niceTicks(0, values.max())
        val f = Frame(this, ticks)
        grid(f, ticks, measurer, st.axis, st.line, scale)
        val slot = (f.right - f.left) / values.size
        val w = (slot * 0.62f).coerceIn(2.dp.toPx(), 12.dp.toPx())
        values.forEachIndexed { i, v ->
            val local = ((grow.value * values.size * 1.5f) - i * 0.5f).coerceIn(0f, 1f)
            val h = (f.bottom - f.y(v)) * local
            val alpha = when {
                touched.value != null -> if (i == touched.value) 1f else 0.35f
                highlight == null || i == highlight -> 1f
                else -> 0.62f
            }
            bar(c.accent.copy(alpha = alpha), f.left + slot * i + (slot - w) / 2, f.bottom, w, if (v > 0) h.coerceAtLeast(2.dp.toPx() * local) else 0f)
            axis(i)?.let { text ->
                val layout = measurer.measure(text, st.axis)
                drawText(layout, topLeft = Offset((f.left + slot * i + slot / 2 - layout.size.width / 2f).coerceIn(0f, f.right - layout.size.width), f.bottom + 5.dp.toPx()))
            }
        }
        touched.value?.let { i -> tip(measurer, title(i), value(i), st.tip, st.muted, st.strong, st.tipBg, f.left + slot * i + slot / 2) }
    }
}

/**
 * Two columns for each month, side by side (money in, money out), rising in. Touching a month shows [title] and
 * [value] for it; [axis] labels each month underneath.
 */
@Composable
fun PairBars(
    pairs: List<Pair<Long, Long>>,
    first: Color,
    second: Color,
    modifier: Modifier = Modifier,
    axis: (Int) -> String,
    title: (Int) -> String,
    value: (Int) -> String,
    scale: (Long) -> String,
) {
    val st = chartStyles()
    val measurer = rememberTextMeasurer()
    val grow = remember(pairs) { Animatable(0f) }
    LaunchedEffect(pairs) { grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val touched = remember(pairs) { mutableStateOf<Int?>(null) }
    val gutter = with(LocalDensity.current) { Gutter.toPx() }
    Canvas(modifier.scrub(pairs.size, touched) { x, w -> if (pairs.isEmpty()) 0 else (x / ((w - gutter) / pairs.size)).toInt().coerceIn(0, pairs.lastIndex) }) {
        if (pairs.isEmpty()) return@Canvas
        val ticks = niceTicks(0, pairs.maxOf { maxOf(it.first, it.second) })
        val f = Frame(this, ticks)
        grid(f, ticks, measurer, st.axis, st.line, scale)
        val slot = (f.right - f.left) / pairs.size
        val w = (slot * 0.26f).coerceAtMost(12.dp.toPx())
        val gap = 3.dp.toPx()
        // Labels thin out when there are many months.
        val every = ((pairs.size + 5) / 6).coerceAtLeast(1)
        pairs.forEachIndexed { i, (a, b) ->
            val dim = touched.value != null && touched.value != i
            val local = ((grow.value * pairs.size * 1.5f) - i * 0.5f).coerceIn(0f, 1f)
            val cx = f.left + slot * i + slot / 2
            bar(first.copy(alpha = if (dim) 0.35f else 1f), cx - w - gap / 2, f.bottom, w, (f.bottom - f.y(a)) * local)
            bar(second.copy(alpha = if (dim) 0.35f else 1f), cx + gap / 2, f.bottom, w, (f.bottom - f.y(b)) * local)
            if (i % every == 0) {
                val layout = measurer.measure(axis(i), st.axis)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, f.bottom + 5.dp.toPx()))
            }
        }
        touched.value?.let { i -> tip(measurer, title(i), value(i), st.tip, st.muted, st.strong, st.tipBg, f.left + slot * i + slot / 2) }
    }
}
