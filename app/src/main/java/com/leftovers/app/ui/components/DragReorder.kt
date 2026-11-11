package com.leftovers.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * Rows that can be put in order with a finger: a row lifts as it's held, follows the finger exactly, and the others
 * slide out of its way on a spring. Letting go settles it into its place, and [onOrder] gets the new order of keys.
 * [enabled]: off, they're plain rows.
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    key: (T) -> Any,
    enabled: Boolean,
    onOrder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, index: Int, lifted: Boolean) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The order on screen, which moves while dragging; it follows the data again once it changes.
    val order = remember(items.map(key)) { mutableStateListOf<T>().apply { addAll(items) } }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var dragging by remember { mutableStateOf<Any?>(null) }
    // How far the held row is from where it started, in pixels.
    var offset by remember { mutableStateOf(0f) }
    var from by remember { mutableStateOf(0) }
    val settle = remember { Animatable(0f) }
    // The rows themselves follow the data too (a balance that changed), in the order shown: kept as a copy, they
    // showed what each row said when the list was first drawn. Not while a row is held, so it doesn't jump.
    androidx.compose.runtime.LaunchedEffect(items) {
        if (dragging != null) return@LaunchedEffect
        val latest = items.associateBy(key)
        order.indices.forEach { i -> latest[key(order[i])]?.let { if (it != order[i]) order[i] = it } }
    }

    // The place the held row would land on, from how far it's moved past its neighbours' middles.
    fun target(): Int {
        if (dragging == null) return -1
        var i = from
        var moved = offset
        while (moved > 0 && i < order.lastIndex) {
            val next = heights[key(order[i + 1])] ?: break
            if (moved < next / 2f) break
            moved -= next; i++
        }
        while (moved < 0 && i > 0) {
            val prev = heights[key(order[i - 1])] ?: break
            if (-moved < prev / 2f) break
            moved += prev; i--
        }
        return i
    }

    Column(modifier) {
        order.forEachIndexed { index, item ->
            val k = key(item)
            key(k) {
                val lifted = dragging == k
                val to = target()
                val heldHeight = dragging?.let { heights[it] } ?: 0
                // The others make room: a row between where the held one was and where it's going moves by its height.
                val shift = when {
                    lifted || dragging == null -> 0f
                    to > from && index in (from + 1)..to -> -heldHeight.toFloat()
                    to < from && index in to until from -> heldHeight.toFloat()
                    else -> 0f
                }
                val slide by animateFloatAsState(shift, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow), label = "slide")
                val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "lift")
                Box(
                    Modifier
                        .zIndex(if (lifted || (dragging == null && settle.isRunning && index == from)) 1f else 0f)
                        .onSizeChanged { heights[k] = it.height }
                        .graphicsLayer {
                            translationY = if (lifted) offset else slide + if (dragging == null && index == from) settle.value else 0f
                            val s = 1f + 0.03f * lift
                            scaleX = s; scaleY = s
                            shadowElevation = 18f * lift
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(22f * density)
                            clip = lift > 0f
                        }
                        .then(
                            if (!enabled) Modifier else Modifier.pointerInput(k) {
                                var last = -1
                                detectDragGestures(
                                    onDragStart = {
                                        from = order.indexOfFirst { key(it) == k }
                                        offset = 0f
                                        dragging = k
                                        last = from
                                        Haptics.select(view)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                        // A tick each time it passes a neighbour.
                                        val t = target()
                                        if (t != last) { last = t; Haptics.tick(view) }
                                    },
                                    onDragEnd = {
                                        val t = target().coerceAtLeast(0)
                                        // Where it lands, from where the finger left it: it glides the rest of the way.
                                        val landed = (from until t).sumOf { heights[key(order[it + 1])] ?: 0 } -
                                            (t until from).sumOf { heights[key(order[it])] ?: 0 }
                                        val rest = offset - landed
                                        val start = from
                                        order.add(t, order.removeAt(start))
                                        from = t
                                        dragging = null
                                        scope.launch { settle.snapTo(rest); settle.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
                                        if (t != start) { onOrder(order.toList()); Haptics.confirm(view) }
                                    },
                                    onDragCancel = { dragging = null; offset = 0f },
                                )
                            },
                        ),
                ) {
                    row(item, index, lifted)
                }
            }
        }
    }
}

@Composable
private fun Box(modifier: Modifier, content: @Composable () -> Unit) =
    androidx.compose.foundation.layout.Box(modifier) { content() }
