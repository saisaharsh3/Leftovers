package com.leftovers.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leftovers.app.data.Category
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Haptics
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Detected payments on a roller: the one in the middle is the one you're deciding, the ones before and after tilt away
 * above and below it. Swipe up or down to roll through them; swipe the middle one right to add it with the category
 * it had before, or left to skip it. Edit opens it to look over first.
 */
@Composable
fun PaymentReview(
    suggestions: List<SmsSuggestion>,
    /** The category a payment would be added with, as worked out from what was paid there before. */
    guess: suspend (SmsSuggestion) -> Category?,
    /** The account a payment came from, by name, when the message matched one. */
    accountName: (SmsSuggestion) -> String?,
    onAdd: (SmsSuggestion, undo: (suspend () -> Unit) -> Unit) -> Unit,
    onSkip: (SmsSuggestion, undo: (suspend () -> Unit) -> Unit) -> Unit,
    onEdit: (SmsSuggestion) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The last add or skip, to take back from here: the main Undo bar sits under this screen.
    var lastUndo by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var lastLabel by remember { mutableStateOf("") }
    // How many have been dealt with, for the progress at the top.
    var done by remember { mutableStateOf(0) }
    val total = suggestions.size + done
    val pager = rememberPagerState { suggestions.size }
    // A tick as each one lands in the middle, like a wheel's detent.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { if (suggestions.isNotEmpty()) Haptics.select(view) }
    }
    // Shut once the last one's done, after a moment to see "All done".
    LaunchedEffect(suggestions.isEmpty()) {
        if (suggestions.isEmpty()) {
            kotlinx.coroutines.delay(1_000)
            onClose()
        }
    }
    fun added(s: SmsSuggestion): (suspend () -> Unit) -> Unit = { u -> done++; lastUndo = { done--; u() }; lastLabel = "Added ${s.merchant.ifBlank { "payment" }}" }
    fun skipped(s: SmsSuggestion): (suspend () -> Unit) -> Unit = { u -> done++; lastUndo = { done--; u() }; lastLabel = "Skipped ${s.merchant.ifBlank { "payment" }}" }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val base = if (c.isDark) Color(0xFF0E0E13) else Color(0xFFF2F2F6)
        Box(Modifier.fillMaxSize().background(base)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // (The roller below is clipped to its own space, so a card never slides under the buttons.)
                // Header: where you are, and how far there is to go.
                Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Review payments", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (suggestions.isEmpty()) "All done" else "${minOf(pager.currentPage + 1, suggestions.size)} of ${suggestions.size} · swipe up or down to roll",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                    Icon(
                        Lucide.X, contentDescription = "Close", tint = c.textPrimary,
                        modifier = Modifier.size(48.dp).clip(CircleShape).pressable(onClose, pressedScale = 0.9f).padding(12.dp),
                    )
                }
                // Progress through the lot: one segment each, filled as they're done.
                if (total > 1) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(total) { i ->
                            val filled by animateColorAsState(if (i < done) c.accent else c.glassStrong, label = "progress")
                            Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(filled))
                        }
                    }
                }
                AnimatedVisibility(lastUndo != null && suggestions.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        AllDone()
                    } else {
                        // The roller: the middle card full size; the ones above and below tilt away and fade.
                        // Tall in the middle, with just the edge of the one before and after showing.
                        val cardHeight = (maxHeight - 130.dp).coerceIn(300.dp, 560.dp)
                        val peek = ((maxHeight - cardHeight) / 2).coerceAtLeast(24.dp)
                        VerticalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape),
                            contentPadding = PaddingValues(vertical = peek),
                            pageSpacing = 14.dp,
                            key = { suggestions[it].id },
                            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            val s = suggestions[page]
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(cardHeight)
                                    .padding(horizontal = 22.dp)
                                    .graphicsLayer {
                                        val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1.5f, 1.5f)
                                        cameraDistance = 14f * density
                                        rotationX = o * 28f
                                        val scale = 1f - 0.1f * abs(o)
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 1f - 0.65f * abs(o).coerceAtMost(1f)
                                    },
                            ) {
                                ReviewCard(
                                    s = s,
                                    active = page == pager.currentPage && abs(pager.currentPageOffsetFraction) < 0.05f,
                                    width = width,
                                    guess = guess,
                                    account = accountName(s),
                                    onDecide = { add -> if (add) onAdd(s, added(s)) else onSkip(s, skipped(s)) },
                                )
                            }
                        }
                    }
                }

                // Buttons for each choice too: swiping isn't for everyone.
                suggestions.getOrNull(pager.currentPage)?.let { top ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChoiceButton(Lucide.X, "Skip", c.textPrimary, c.glassStrong) { onSkip(top, skipped(top)) }
                        ChoiceButton(Lucide.Pencil, "Edit", c.textPrimary, c.glassStrong, small = true) { onEdit(top) }
                        ChoiceButton(Lucide.Check, "Add", c.onAccent, c.accent) { onAdd(top, added(top)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllDone() {
    val c = LocalAppColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 300f)) + fadeIn()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(c.positive.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Lucide.Check, contentDescription = null, tint = c.positive, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("All done", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text("Every payment is sorted", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun ChoiceButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, fill: Color, small: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (small) 52.dp else 66.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (fill == c.accent) Modifier else Modifier.border(1.dp, c.borderTop, CircleShape))
                .pressable({ Haptics.select(view); onClick() }, pressedScale = 0.88f),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(if (small) 20.dp else 28.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

@Composable
private fun ReviewCard(
    s: SmsSuggestion,
    /** The one in the middle, which swipes left and right; the others only roll. */
    active: Boolean,
    width: Float,
    guess: suspend (SmsSuggestion) -> Category?,
    account: String?,
    onDecide: (add: Boolean) -> Unit,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val category by produceState<Category?>(null, s.id) { value = guess(s) }
    val threshold = width * 0.26f
    val progress = (offset.value / threshold).coerceIn(-1f, 1f)
    var decided by remember { mutableStateOf(false) }
    // The card takes its category's colour, so a glance says what it is.
    val tint = category?.let { Color(it.color) } ?: c.accent
    val cardFill = if (c.isDark) Color(0xFF1C1C24) else Color.White
    val crossed = abs(progress) >= 1f
    LaunchedEffect(crossed) { if (crossed) Haptics.select(view) }

    fun fling(add: Boolean) {
        if (decided) return
        decided = true
        scope.launch {
            offset.animateTo(if (add) width * 1.4f else -width * 1.4f, tween(220))
            onDecide(add)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / width * 10f
            }
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
            .clip(RoundedCornerShape(34.dp))
            .background(cardFill)
            // A glow of its colour from the top, over the card.
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = if (c.isDark) 0.28f else 0.18f), Color.Transparent), endY = 700f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(34.dp)),
    ) {
        // Green as it goes right to add, grey as it goes left to skip.
        Box(Modifier.fillMaxSize().background((if (progress > 0) c.positive else c.textTertiary).copy(alpha = abs(progress) * 0.16f)))
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Where it came from, and the swipe labels as they come into play.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val sourceIcon = when (s.source) {
                    SmsSuggestion.SOURCE_EMAIL -> Lucide.Mail
                    SmsSuggestion.SOURCE_APP -> Lucide.Bell
                    else -> Lucide.Smartphone
                }
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.graphicsLayer { alpha = 1f - abs(progress) }.clip(CircleShape).background(c.glass).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(sourceIcon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            s.sender.ifBlank { if (s.source == SmsSuggestion.SOURCE_EMAIL) "Email" else if (s.source == SmsSuggestion.SOURCE_APP) "Payment app" else "Bank SMS" },
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                    }
                    SwipeLabel("SKIP", c.textSecondary, (-progress).coerceIn(0f, 1f), Modifier.align(Alignment.CenterStart))
                }
                SwipeLabel("ADD", c.positive, progress.coerceIn(0f, 1f))
            }
            Spacer(Modifier.weight(1f))
            // Who: a big initial in the category's colour.
            val name = s.merchant.ifBlank { if (s.isIncome) "Money in" else "Bank payment" }
            Box(Modifier.size(58.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(name.first().uppercase(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = tint)
            }
            Spacer(Modifier.height(12.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                (if (s.isIncome) "+" else "") + money.format(s.amountMinor),
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.5).sp),
                fontWeight = FontWeight.SemiBold,
                color = if (s.isIncome) c.positive else c.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(java.time.LocalDate.ofEpochDay(s.epochDay).friendlyLabel(), account ?: s.accountDigits?.let { "A/c ending $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            // What it'll be added as.
            Row(
                Modifier.clip(CircleShape).background(tint.copy(alpha = 0.14f)).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
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

@Composable
private fun SwipeLabel(text: String, color: Color, shown: Float, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (shown >= 1f) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "swipeLabel")
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .graphicsLayer { alpha = shown; scaleX = scale; scaleY = scale }
            .border(1.5.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
