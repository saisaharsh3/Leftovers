package com.leftovers.app.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.label
import dev.chrisbanes.haze.hazeSource
import java.time.YearMonth

/** Rounded square holding a line icon on a faint wash of its colour. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(size * 0.34f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun CategoryIcon(key: String, color: Long, size: Dp = 44.dp, modifier: Modifier = Modifier) =
    IconTile(CategoryIcons[key], Color(color), size, modifier)

@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = LocalAppColors.current.textPrimary,
    container: Color? = null,
    enabled: Boolean = true,
) {
    Glass(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f),
        shape = CircleShape,
        tint = container,
        onClick = if (enabled) onClick else null,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size * 0.45f)
                .align(Alignment.Center),
        )
    }
}

/** Solid accent capsule — the one loud element on any screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (enabled) c.accent else c.glassStrong, label = "primaryBg")
    val fg by animateColorAsState(if (enabled) c.onAccent else c.textTertiary, label = "primaryFg")
    Box(
        modifier
            .height(56.dp)
            .pressable(onClick, enabled = enabled)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = fg)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = LocalAppColors.current
    Glass(modifier.height(56.dp), shape = CircleShape, strong = true, onClick = onClick) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
    }
}

/** Large title header that floats over content with a frosted background. */
@Composable
fun GlassHeader(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .frosted(fadeBottom = true)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                RoundButton(Lucide.ChevronLeft, "Back", onBack)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
        // Room for the frost to fade out, so the bar has no hard bottom edge.
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Standard screen: transparent so the aurora shows through, content scrolls beneath a frosted
 * header. [content] receives the padding to use as its content padding.
 */
@Composable
fun GlassScreen(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = LocalAppColors.current
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = { GlassHeader(title, onBack, subtitle, actions) },
        bottomBar = bottomBar,
        snackbarHost = {
            if (snackbar != null) {
                SnackbarHost(
                    snackbar,
                    Modifier.navigationBarsPadding(),
                ) { data ->
                    Snackbar(
                        data,
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = c.textPrimary,
                        actionColor = c.accent,
                        dismissActionContentColor = c.textSecondary,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(LocalHazeState.current, zIndex = 1f),
        ) { content(padding) }
    }
}

/** Segmented control with a sliding glass thumb. */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    BoxWithConstraints(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(c.glass)
            .border(1.dp, glassBorder(c), CircleShape)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.78f, stiffness = 520f), label = "thumb")
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (c.isDark) Color.White.copy(alpha = 0.14f) else Color.White)
                .border(1.dp, glassBorder(c), CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val color by animateColorAsState(
                    if (option == selected) c.textPrimary else c.textSecondary,
                    label = "segText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable({ onSelect(option) }, pressedScale = 0.94f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Capsule chip; selected chips fill with the accent. */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    selected: Boolean = false,
) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (selected) c.accent else c.glass, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.textPrimary, label = "chipFg")
    Row(
        modifier
            .height(42.dp)
            .pressable(onClick, pressedScale = 0.95f)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)) else glassBorder(c), CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) fg else iconTint ?: fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/**
 * Text whose characters roll vertically when they change — used for money so updates feel alive.
 * Characters are keyed from the right so digits stay aligned as the number grows. The text shrinks
 * to fit the available width instead of being clipped on the right.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    /** Draw a blinking caret after this character index (-1 = before the first); null hides it. */
    caretAfter: Int? = null,
    caretColor: Color = color,
    onCharTap: ((Int) -> Unit)? = null,
) {
    var blinkOn by remember { mutableStateOf(true) }
    if (caretAfter != null) {
        // Toggle twice a second rather than animating, so the caret costs two frames a second.
        LaunchedEffect(caretAfter, text) {
            blinkOn = true
            while (true) {
                delay(530)
                blinkOn = !blinkOn
            }
        }
    }
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, style, maxWidth) {
            var s = style
            if (constraints.hasBoundedWidth && s.fontSize.isSp) {
                while (measurer.measure(text, s).size.width > maxWidth && s.fontSize.value > 14f) {
                    s = s.copy(
                        fontSize = s.fontSize * 0.92f,
                        lineHeight = if (s.lineHeight.isSp) s.lineHeight * 0.92f else s.lineHeight,
                    )
                }
            }
            s
        }
        Row(Modifier.align(Alignment.Center)) {
            val chars = text.toList()
            chars.forEachIndexed { i, ch ->
                key(chars.size - i) {
                    val caretHere = when (caretAfter) {
                        null -> 0
                        -1 -> if (i == 0) -1 else 0
                        i -> 1
                        else -> 0
                    }
                    AnimatedContent(
                        modifier = Modifier
                            .then(if (onCharTap != null) Modifier.clickable(interactionSource = null, indication = null) { onCharTap(i) } else Modifier)
                            .drawWithContent {
                                drawContent()
                                if (caretHere != 0 && blinkOn) {
                                    val x = if (caretHere > 0) size.width else 0f
                                    val w = 3.dp.toPx()
                                    drawRoundRect(
                                        caretColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(x - w / 2, size.height * 0.14f),
                                        size = androidx.compose.ui.geometry.Size(w, size.height * 0.72f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2),
                                    )
                                }
                            },
                        targetState = ch,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { if (up) it else -it } + fadeIn(tween(180)))
                                .togetherWith(slideOutVertically(tween(180)) { if (up) -it else it } + fadeOut(tween(120)))
                                .using(SizeTransform(clip = true))
                        },
                        contentAlignment = Alignment.Center,
                        label = "roll",
                    ) { c -> Text(c.toString(), style = fitted, color = color, maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

/** Thin animated progress line; colour follows how close the value is to the limit. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = budgetColor(fraction), height: Dp = 6.dp) {
    val c = LocalAppColors.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 60f), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
        )
    }
}

@Composable
fun budgetColor(fraction: Float): Color {
    val c = LocalAppColors.current
    return when {
        fraction >= 1f -> c.negative
        fraction >= 0.8f -> c.warning
        else -> c.accent
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier.pressable(onAction),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glass(Modifier.size(64.dp), shape = RoundedCornerShape(22.dp)) {
            Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(28.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val isCurrent = month >= YearMonth.now()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Lucide.ChevronLeft, "Previous month", { onChange(month.minusMonths(1)) }, size = 40.dp)
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInVertically { if (forward) it / 2 else -it / 2 } + fadeIn())
                    .togetherWith(slideOutVertically { if (forward) -it / 2 else it / 2 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "month",
        ) { m ->
            Text(m.label(), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        RoundButton(Lucide.ChevronRight, "Next month", { onChange(month.plusMonths(1)) }, size = 40.dp, enabled = !isCurrent)
    }
}

/** Small glass card with a caption over a value. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = LocalAppColors.current.textSecondary,
    footnote: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Glass(modifier, onClick = onClick) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1)
            if (footnote != null) {
                Spacer(Modifier.height(2.dp))
                Text(footnote, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A row inside a grouped glass list. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 0.985f) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun RowDivider(inset: Dp = 74.dp) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(c.borderBottom),
    )
}

/** Helper for content that should clear the floating tab bar. */
val DockClearance = 120.dp
