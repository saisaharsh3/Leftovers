package com.leftovers.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.Haptics
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Detected payments on Home as a roller: one in the middle, the ones before and after tilting away above and below
 * it. Swipe up or down to roll through them; swipe the middle one right to add it (or settle it, for a payback or a
 * subscription's payment) and left to dismiss it. Tapping it still opens it to look over first.
 */
@Composable
fun PaymentRoller(
    items: List<SmsSuggestion>,
    onAdd: (SmsSuggestion) -> Unit,
    onDismiss: (SmsSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (SmsSuggestion) -> Unit,
) {
    val c = LocalAppColors.current
    val view = LocalView.current
    val rowHeight = 76.dp
    // Just enough of the one above and below to see there's more, and that it rolls.
    val peek = 38.dp
    val many = items.size > 1
    val pager = rememberPagerState { items.size }
    // A tick as each one settles in the middle, like a wheel's detent.
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.settledPage }.collect { if (!first) Haptics.select(view); first = false }
    }
    Glass(modifier.fillMaxWidth()) {
        Column {
            if (!many) {
                items.firstOrNull()?.let { s ->
                    Box(Modifier.fillMaxWidth().height(rowHeight)) {
                        SwipeRow(s, active = true, onAdd = { onAdd(s) }, onDismiss = { onDismiss(s) }) { row(s) }
                    }
                }
            } else {
                VerticalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight + peek * 2)
                        .clip(RectangleShape)
                        // The ones above and below melt into the card at its edges instead of being cut off.
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edge = peek.toPx() / size.height
                            drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to androidx.compose.ui.graphics.Color.Transparent,
                                    edge * 0.55f to androidx.compose.ui.graphics.Color.Black,
                                    1f - edge * 0.55f to androidx.compose.ui.graphics.Color.Black,
                                    1f to androidx.compose.ui.graphics.Color.Transparent,
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        },
                    contentPadding = PaddingValues(vertical = peek),
                    key = { items[it].id },
                    flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val s = items[page]
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                // Rolling away from the middle: tilted back, a little smaller and fainter, like a drum.
                                val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1.5f, 1.5f)
                                cameraDistance = 12f * density
                                rotationX = o * 42f
                                val scale = 1f - 0.08f * abs(o)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - 0.4f * abs(o).coerceAtMost(1f)
                            },
                    ) {
                        SwipeRow(
                            s,
                            active = page == pager.currentPage && abs(pager.currentPageOffsetFraction) < 0.05f,
                            onAdd = { onAdd(s) },
                            onDismiss = { onDismiss(s) },
                        ) { row(s) }
                    }
                }
                // Where you are in them, and what the gestures do.
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(items.size.coerceAtMost(8)) { i ->
                            val on = i == pager.currentPage.coerceAtMost(7)
                            Box(Modifier.size(width = if (on) 14.dp else 5.dp, height = 5.dp).clip(CircleShape).background(if (on) c.accent else c.glassStrong))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${pager.currentPage + 1} of ${items.size} · swipe " + (if (pager.currentPage == items.lastIndex) "down" else "up") + " for more",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * One payment that swipes sideways: right shows a green ✓ behind it and adds it, left shows ✕ and dismisses it.
 * Short of the mark, it springs back.
 */
@Composable
private fun SwipeRow(
    s: SmsSuggestion,
    active: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember(s.id) { Animatable(0f) }
    var decided by remember(s.id) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val threshold = width * 0.32f
        val progress = (offset.value / threshold).coerceIn(-1f, 1f)
        val crossed = abs(progress) >= 1f
        LaunchedEffect(crossed) { if (crossed) Haptics.select(view) }
        // What letting go would do, under the row as it moves aside.
        if (offset.value != 0f) {
            val right = offset.value > 0
            Box(
                Modifier
                    .fillMaxSize()
                    .background((if (right) c.positive else c.textTertiary).copy(alpha = 0.10f + 0.18f * abs(progress))),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(Modifier.padding(horizontal = 22.dp).graphicsLayer { alpha = abs(progress); val k = 0.7f + 0.3f * abs(progress); scaleX = k; scaleY = k }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (right) Lucide.Check else Lucide.X, contentDescription = null, tint = if (right) c.positive else c.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (right) "Add" else "Dismiss", style = MaterialTheme.typography.labelLarge, color = if (right) c.positive else c.textSecondary)
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .then(
                    if (active) Modifier.pointerInput(s.id) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onHorizontalDrag = { change, amount ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + amount) }
                            },
                            onDragEnd = {
                                // Where the throw would carry it, so a quick flick counts as much as a long drag.
                                val projected = offset.value + tracker.calculateVelocity().x * 0.15f
                                if (abs(projected) > threshold && !decided) {
                                    decided = true
                                    val add = projected > 0
                                    scope.launch {
                                        offset.animateTo(if (add) width else -width, tween(180))
                                        if (add) onAdd() else onDismiss()
                                    }
                                } else {
                                    scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) } },
                        )
                    } else Modifier,
                ),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}
