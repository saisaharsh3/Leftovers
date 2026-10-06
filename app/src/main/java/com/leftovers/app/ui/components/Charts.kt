package com.leftovers.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leftovers.app.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

data class ChartSlice(val value: Float, val color: Color)

/** Ring of rounded segments separated by small gaps. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 14.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(c.glassStrong, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            // Gap measured in degrees so round caps never touch.
            val gap = if (slices.size > 1) (stroke / (d / 2f)) * 57.3f + 3f else 0f
            var start = -90f
            slices.forEach { s ->
                val sweep = 360f * (s.value / total) * progress.value
                if (sweep > gap + 0.5f) {
                    drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                start += sweep
            }
        }
        center()
    }
}

/** 240° arc gauge used for budgets. [fraction] is the part used. */
@Composable
fun Gauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    val color = budgetColor(fraction)
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 40f), label = "gauge")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val start = 150f
            val sweep = 240f
            drawArc(c.glassStrong, start, sweep, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color.copy(alpha = 0.55f)), center = center),
                    startAngle = start,
                    sweepAngle = sweep * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(d, d),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * One rounded bar per day; today is highlighted, other days are softer. Touching the chart picks
 * the bar under the finger and shows [tooltip] for it; it stays until the chart is touched again.
 */
@Composable
fun DailyBars(values: List<Long>, highlight: Int?, modifier: Modifier = Modifier, tooltip: ((Int) -> String)? = null) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.textTertiary, fontSize = 10.sp)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = c.textPrimary)
    val tipBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var touched by remember(values) { mutableStateOf<Int?>(null) }
    val input = if (tooltip == null || values.isEmpty()) Modifier else Modifier.pointerInput(values.size) {
        fun indexAt(x: Float) = (x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.size - 1)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val first = indexAt(down.position.x)
            // A tap on the bar that is already showing hides it again.
            val clearOnUp = touched == first
            touched = first
            var moved = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                // Let a vertical scroll of the page win; follow the finger only while it isn't scrolling.
                if (change.isConsumed) continue
                val delta = change.position - change.previousPosition
                if (abs(delta.x) > abs(delta.y)) change.consume()
                val i = indexAt(change.position.x)
                if (i != touched) {
                    moved = true
                    touched = i
                }
            }
            if (clearOnUp && !moved) touched = null
        }
    }
    Canvas(modifier.then(input)) {
        if (values.isEmpty()) return@Canvas
        val tipH = if (tooltip != null) 30.dp.toPx() else 0f
        val labelH = 18.dp.toPx()
        val chartTop = tipH
        val chartH = size.height - labelH - chartTop
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val slot = size.width / values.size
        val barW = (slot * 0.56f).coerceAtMost(10.dp.toPx())
        values.forEachIndexed { i, v ->
            // Bars rise left to right for a subtle wave.
            val local = ((progress.value * values.size * 1.6f) - i * 0.6f).coerceIn(0f, 1f)
            val h = if (v > 0) (chartH * (v / max) * local).coerceAtLeast(barW) else 2.dp.toPx()
            val x = slot * i + (slot - barW) / 2
            val focus = touched ?: highlight
            val active = focus == null || i == focus
            val brush = if (v == 0L) {
                Brush.verticalGradient(listOf(c.glassStrong, c.glassStrong))
            } else {
                Brush.verticalGradient(
                    listOf(c.accent.copy(alpha = if (active) 1f else 0.55f), c.accent.copy(alpha = if (active) 0.45f else 0.18f)),
                    startY = chartH - h,
                    endY = chartH,
                )
            }
            drawRoundRect(brush, Offset(x, chartTop + chartH - h), Size(barW, h), CornerRadius(barW / 2, barW / 2))
            val day = i + 1
            if (day == 1 || day % 5 == 0) {
                val layout = measurer.measure(day.toString(), labelStyle)
                drawText(layout, topLeft = Offset(slot * i + slot / 2 - layout.size.width / 2f, chartTop + chartH + 4.dp.toPx()))
            }
        }
        val sel = touched
        if (tooltip != null && sel != null) {
            val center = slot * sel + slot / 2
            drawLine(c.textTertiary, Offset(center, tipH), Offset(center, chartTop + chartH), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(tooltip(sel), tipStyle)
            val padX = 10.dp.toPx()
            val w = layout.size.width + padX * 2
            val left = (center - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            drawRoundRect(tipBg, Offset(left, 0f), Size(w, tipH - 4.dp.toPx()), CornerRadius(12.dp.toPx(), 12.dp.toPx()))
            drawText(layout, topLeft = Offset(left + padX, (tipH - 4.dp.toPx() - layout.size.height) / 2))
        }
    }
}

/** Calendar tinted by how each day compares with the average spending day. Days can be tapped. */
@Composable
fun SpendingCalendar(
    month: YearMonth,
    daily: List<Long>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    val c = LocalAppColors.current
    val today = LocalDate.now()
    val spendDays = daily.filter { it > 0 }
    val average = if (spendDays.isEmpty()) 0L else spendDays.sum() / spendDays.size
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<Int?> = List(leading) { null } + (1..month.lengthOfMonth()).toList()
    val shape = RoundedCornerShape(10.dp)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day == null) return@Box
                        val spent = daily.getOrElse(day - 1) { 0L }
                        val tone = when {
                            spent == 0L -> null
                            spent <= average * 3 / 4 -> c.positive
                            spent <= average * 5 / 4 -> c.warning
                            else -> c.negative
                        }
                        val isToday = month.atDay(day) == today
                        val isSelected = day == selected
                        Box(
                            Modifier
                                .matchParentSize()
                                .then(if (onSelect != null) Modifier.pressable({ onSelect(day) }, pressedScale = 0.9f) else Modifier)
                                .clip(shape)
                                .background(
                                    when {
                                        isSelected -> c.accent.copy(alpha = 0.9f)
                                        else -> tone?.copy(alpha = 0.22f) ?: Color.Transparent
                                    },
                                )
                                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.textPrimary, shape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.toString(), style = MaterialTheme.typography.labelMedium, color = if (isSelected) c.onAccent else tone ?: c.textTertiary)
                        }
                    }
                }
            }
        }
    }
}
