package com.leftovers.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leftovers.app.data.Category
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Haptics
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Detected payments one at a time, as a stack of cards (#review): swipe right to add one with the category it had
 * before, left to skip it, or tap Edit to look it over first. Quicker than a list when a few arrive together.
 */
@Composable
fun PaymentReview(
    suggestions: List<SmsSuggestion>,
    /** The category a payment would be added with, as worked out from what was paid there before. */
    guess: suspend (SmsSuggestion) -> Category?,
    onAdd: (SmsSuggestion, undo: (suspend () -> Unit) -> Unit) -> Unit,
    onSkip: (SmsSuggestion, undo: (suspend () -> Unit) -> Unit) -> Unit,
    onEdit: (SmsSuggestion) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    // The last add or skip, to take back from here: the main Undo bar sits under this screen.
    var lastUndo by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var lastLabel by remember { mutableStateOf("") }
    // Shut once the last one's done, after a moment to see "All done".
    LaunchedEffect(suggestions.isEmpty()) {
        if (suggestions.isEmpty()) {
            kotlinx.coroutines.delay(900)
            onClose()
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Solid, not glass: the cards and Home mustn't show through each other.
        Box(Modifier.fillMaxSize().background(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF111116) else androidx.compose.ui.graphics.Color(0xFFF3F3F6))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Review payments", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                        Text(
                            if (suggestions.isEmpty()) "All done" else "${suggestions.size} to go · swipe right to add, left to skip",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                    Icon(
                        Lucide.X, contentDescription = "Close", tint = c.textPrimary,
                        modifier = Modifier.size(44.dp).clip(CircleShape).pressable(onClose, pressedScale = 0.9f).padding(10.dp),
                    )
                }
                AnimatedVisibility(lastUndo != null, enter = fadeIn(), exit = fadeOut()) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lastLabel, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Undo",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.accent,
                            modifier = Modifier.clip(CircleShape).pressable({
                                val u = lastUndo
                                lastUndo = null
                                if (u != null) scope.launch { u() }
                            }, pressedScale = 0.92f).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val width = constraints.maxWidth.toFloat()
                    if (suggestions.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Lucide.Check, contentDescription = null, tint = c.positive, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("All done", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        }
                    }
                    // The two behind the top one peek out underneath, smaller, as a stack.
                    suggestions.take(3).reversed().forEachIndexed { i, s ->
                        val depth = minOf(suggestions.size, 3) - 1 - i
                        key(s.id) {
                            ReviewCard(
                                s = s,
                                depth = depth,
                                width = width,
                                guess = guess,
                                onDecide = { add ->
                                    if (add) onAdd(s) { u -> lastUndo = u; lastLabel = "Added ${s.merchant.ifBlank { "payment" }}" }
                                    else onSkip(s) { u -> lastUndo = u; lastLabel = "Skipped ${s.merchant.ifBlank { "payment" }}" }
                                },
                            )
                        }
                    }
                }
                // Buttons for each choice too: swiping isn't for everyone.
                suggestions.firstOrNull()?.let { top ->
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        ChoiceButton(Lucide.X, "Skip", c.textSecondary, c.glass) { onSkip(top) { u -> lastUndo = u; lastLabel = "Skipped ${top.merchant.ifBlank { "payment" }}" } }
                        ChoiceButton(Lucide.Pencil, "Edit", c.textSecondary, c.glass, small = true) { onEdit(top) }
                        ChoiceButton(Lucide.Check, "Add", c.onAccent, c.accent) { onAdd(top) { u -> lastUndo = u; lastLabel = "Added ${top.merchant.ifBlank { "payment" }}" } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, fill: androidx.compose.ui.graphics.Color, small: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (small) 52.dp else 64.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, c.glassStrong, CircleShape)
                .pressable({ Haptics.select(view); onClick() }, pressedScale = 0.9f),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(if (small) 20.dp else 26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

@Composable
private fun ReviewCard(
    s: SmsSuggestion,
    depth: Int,
    width: Float,
    guess: suspend (SmsSuggestion) -> Category?,
    onDecide: (add: Boolean) -> Unit,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val category by produceState<Category?>(null, s.id) { value = guess(s) }
    val threshold = width * 0.28f
    // Behind the top card: a little smaller and lower, catching up as the top one goes.
    val lift by androidx.compose.animation.core.animateFloatAsState(depth.toFloat(), spring(dampingRatio = 0.8f, stiffness = 400f), label = "stackDepth")
    val dropPx = with(LocalDensity.current) { 22.dp.toPx() }
    val progress = (offset.value / threshold).coerceIn(-1f, 1f)
    var decided by remember { mutableStateOf(false) }

    fun fling(add: Boolean) {
        if (decided) return
        decided = true
        Haptics.select(view)
        scope.launch {
            offset.animateTo(if (add) width * 1.4f else -width * 1.4f, tween(220))
            onDecide(add)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .graphicsLayer {
                translationX = offset.value
                translationY = lift * dropPx
                rotationZ = offset.value / width * 12f
                val scale = 1f - lift * 0.05f
                scaleX = scale
                scaleY = scale
                alpha = if (depth > 1) 0.7f else 1f
            }
            .then(
                if (depth == 0) Modifier.pointerInput(s.id) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, amount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            // Where the throw would carry it, so a quick flick counts as much as a long drag.
                            val projected = offset.value + v * 0.15f
                            when {
                                projected > threshold -> fling(true)
                                projected < -threshold -> fling(false)
                                else -> scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) } },
                    )
                } else Modifier,
            )
            .clip(RoundedCornerShape(32.dp))
            .background(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF22222B) else androidx.compose.ui.graphics.Color.White)
            .border(1.dp, c.borderTop, RoundedCornerShape(32.dp)),
    ) {
        // Only the top card shows what's on it; the ones under it are the stack.
        if (depth > 0) return@Box
        // Green for add, grey for skip, deepening as the card goes that way.
        val tint = if (progress > 0) c.positive else c.textTertiary
        Box(Modifier.fillMaxSize().background(tint.copy(alpha = abs(progress) * 0.18f)))
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SKIP",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.graphicsLayer { alpha = (-progress).coerceIn(0f, 1f) }
                        .border(1.5.dp, c.textSecondary, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "ADD",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.positive,
                    modifier = Modifier.graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                        .border(1.5.dp, c.positive, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                s.merchant.ifBlank { if (s.isIncome) "Money in" else "Bank payment" },
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (s.isIncome) "+" else "") + money.format(s.amountMinor),
                style = MaterialTheme.typography.displaySmall,
                color = if (s.isIncome) c.positive else c.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            val where = s.sender.ifBlank { if (s.source == SmsSuggestion.SOURCE_EMAIL) "email" else if (s.source == SmsSuggestion.SOURCE_APP) "payment app" else "bank SMS" }
            Text(java.time.LocalDate.ofEpochDay(s.epochDay).friendlyLabel() + " · " + where, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            // What it'll be added as.
            Row(
                Modifier.clip(CircleShape).background(c.glass).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val cat = category
                if (cat != null) CategoryIcon(cat.emoji, cat.color, size = 28.dp) else Spacer(Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (s.isIncome) "Income · " else "") + (cat?.name ?: "…"),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                )
            }
        }
    }
}
