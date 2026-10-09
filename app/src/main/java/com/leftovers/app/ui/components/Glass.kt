package com.leftovers.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.theme.AppColors
import com.leftovers.app.ui.theme.LocalAppColors
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** Shared blur state: the aurora and scrolling content are sources, bars and the dock blur them. */
val LocalHazeState = staticCompositionLocalOf { HazeState() }

/**
 * Frosted-glass effect for anything floating above content (headers, tab bar). With [fadeBottom] the frost
 * fades out over its lower part instead of ending in a hard edge, for bars that sit over the top of a screen.
 */
@Composable
fun Modifier.frosted(colors: AppColors = LocalAppColors.current, fadeBottom: Boolean = false): Modifier = hazeBlur(
    input = HazeInput.Sources(LocalHazeState.current),
    style = HazeBlurStyle {
        blurRadius(28.dp)
        noiseFactor(0.04f)
        colorEffects(listOf(HazeColorEffect.tint(colors.barTint)))
        if (fadeBottom) mask(Brush.verticalGradient(0f to Color.Black, 0.74f to Color.Black, 1f to Color.Transparent))
    },
)

/**
 * Soft light behind every screen. It stays still: the frosted bars blur it, so animating it would
 * re-run that blur on every frame even when nothing else moves.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val drift = 0.5f
    Canvas(
        modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        val w = size.width
        val h = size.height
        fun blob(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
        val strength = if (c.isDark) 1f else 2.1f
        blob(c.auroraA, w * (0.1f + 0.3f * drift), h * (0.06f + 0.05f * drift), w * 1.0f, 0.42f * strength)
        blob(c.auroraB, w * (1.0f - 0.25f * drift), h * (0.32f + 0.12f * drift), w * 0.85f, 0.24f * strength)
        blob(c.auroraC, w * (0.2f + 0.35f * drift), h * (0.98f - 0.1f * drift), w * 0.8f, 0.14f * strength)
    }
}

fun glassBorder(c: AppColors) = Brush.verticalGradient(listOf(c.borderTop, c.borderBottom))

/**
 * A see-through fill catches light at the top, like a pane of glass; solid fills stay flat. Light glass gets a
 * stronger shine, since a pale card needs more highlight to read as glass.
 */
private fun glassFill(fill: Color, light: Boolean): Brush = when {
    fill.alpha >= 0.9f -> androidx.compose.ui.graphics.SolidColor(fill)
    light -> Brush.verticalGradient(
        0f to fill.copy(alpha = (fill.alpha * 5f).coerceAtMost(0.75f)),
        0.35f to fill.copy(alpha = (fill.alpha * 1.6f).coerceAtMost(1f)),
        1f to fill,
    )
    else -> Brush.verticalGradient(listOf(fill.copy(alpha = (fill.alpha * 1.7f).coerceAtMost(1f)), fill))
}

/** Translucent card with a hairline top-lit border. Clickable cards gently shrink when pressed. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(shape)
            .background(glassFill(tint ?: if (strong) c.glassStrong else c.glass, light = !c.isDark))
            // Light glass gets a slightly thicker rim: on a pale screen the bright edge is what reads as glass.
            .border(if (c.isDark) 1.dp else 1.5.dp, glassBorder(c), shape),
        content = content,
    )
}

/** Click handling with a springy press-down scale instead of a ripple. */
fun Modifier.pressable(onClick: () -> Unit, pressedScale: Float = 0.97f, enabled: Boolean = true): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Fades and lifts an item into place the first time it appears, staggered by [index]. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(520, delayMillis = 45 * index.coerceIn(0, 8), easing = FastOutSlowInEasing),
        label = "appear",
    )
    LaunchedEffect(Unit) { shown = true }
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 28.dp.toPx()
    }
}
