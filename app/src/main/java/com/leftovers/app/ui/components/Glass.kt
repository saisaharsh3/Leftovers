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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
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
 * fades out over its lower part instead of ending in a hard edge, for bars that sit over the top of a screen;
 * [fadeTop] does the same at the top, for button bars at the bottom of a screen.
 */
@Composable
fun Modifier.frosted(colors: AppColors = LocalAppColors.current, fadeBottom: Boolean = false, fadeTop: Boolean = false): Modifier = hazeBlur(
    input = HazeInput.Sources(LocalHazeState.current),
    style = HazeBlurStyle {
        blurRadius(28.dp)
        noiseFactor(0.04f)
        colorEffects(listOf(HazeColorEffect.tint(colors.barTint)))
        if (fadeBottom) mask(Brush.verticalGradient(0f to Color.Black, 0.74f to Color.Black, 1f to Color.Transparent))
        if (fadeTop) mask(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color.Black, 1f to Color.Black))
    },
)

/**
 * Soft light behind every screen. It stays still: the frosted bars blur it, so animating it would
 * re-run that blur on every frame even when nothing else moves.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Canvas(modifier.fillMaxSize()) { drawAurora(c) }
}

/** Draws the aurora; also used for the copy of the screen the liquid tab bar bends. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(c: AppColors) {
    drawRect(c.background)
    // The user's photo: filling the screen (cropped, not stretched), already blurred when it was loaded,
    // and dimmed towards the background so text on it stays readable.
    c.photo?.let { img ->
        val scale = maxOf(size.width / img.width, size.height / img.height)
        val w = img.width * scale
        val h = img.height * scale
        drawImage(
            img,
            dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(w.toInt() + 1, h.toInt() + 1),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
        drawRect(c.background.copy(alpha = c.photoDim))
        return
    }
    val w = size.width
    val h = size.height
    val drift = 0.5f
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

/**
 * Frosted glass in front of the user's photo: the card shows the photo's much softer copy, lined up with the
 * photo behind it, then the same dimming. It moves with the card as it scrolls, as if the photo were behind
 * glass, and costs one small image per card rather than a live blur.
 */
private fun Modifier.frosted(c: AppColors): Modifier = composed {
    var at by remember { mutableStateOf(Offset.Zero) }
    var root by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    this
        .then(
            Modifier.onPlaced { coords ->
                at = coords.positionInRoot()
                root = coords.findRootCoordinates().size
            },
        )
        .drawBehind {
            val img = c.frost ?: return@drawBehind
            if (root.width == 0) return@drawBehind
            val scale = maxOf(root.width.toFloat() / img.width, root.height.toFloat() / img.height)
            val w = img.width * scale
            val h = img.height * scale
            drawImage(
                img,
                dstOffset = androidx.compose.ui.unit.IntOffset(((root.width - w) / 2 - at.x).toInt(), ((root.height - h) / 2 - at.y).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt() + 1, h.toInt() + 1),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
            drawRect(c.background.copy(alpha = c.photoDim))
        }
}

fun glassBorder(c: AppColors) = Brush.verticalGradient(listOf(c.borderTop, c.borderBottom))

/** A see-through fill catches light at the top, like a pane of glass, in both themes; solid fills stay flat. */
internal fun glassFill(fill: Color): Brush =
    if (fill.alpha >= 0.9f) androidx.compose.ui.graphics.SolidColor(fill)
    else Brush.verticalGradient(listOf(fill.copy(alpha = (fill.alpha * 1.7f).coerceAtMost(1f)), fill))

/**
 * The background (the photo) recorded for glass in front of it, when cards can be liquid glass: a photo
 * background, glass effects on, Android 13 or newer. Null otherwise, and cards are frosted instead.
 */
val LocalBackgroundBackdrop = staticCompositionLocalOf<com.kyant.backdrop.backdrops.LayerBackdrop?> { null }

/**
 * True inside a pop-up, sheet or dialog. What's behind its buttons and fields is the pop-up itself, not the
 * background photo, so they're plain frosted glass there rather than windows onto the photo.
 */
val LocalInOverlay = staticCompositionLocalOf { false }

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
            .glassSurface(shape, strong, tint)
            .clip(shape)
            .border(1.dp, glassBorder(c), shape),
        content = content,
    )
}

/**
 * A card's glass, the same everywhere so cards look alike on every screen. Over a photo (Android 13+): liquid glass,
 * the photo behind bent at the rounded edges ([lens]; off for pieces of one card, whose inner edges join up), under
 * a faint tint for the text: a little lighter under black text, darker under white, as Apple's Liquid Glass adapts.
 * No blur on it: blurring every card as it scrolls made lists lag. Otherwise the usual see-through fill.
 */
@Composable
fun Modifier.glassSurface(shape: Shape, strong: Boolean = false, tint: Color? = null, lens: Boolean = true): Modifier {
    val c = LocalAppColors.current
    val overlay = LocalInOverlay.current
    // Frosted glass (chosen in Appearance) leaves no backdrop: the photo's ready-blurred copy instead.
    val backdrop = LocalBackgroundBackdrop.current.takeIf { !overlay }
    return if (backdrop != null) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                if (lens) lens(14.dp.toPx(), 28.dp.toPx(), chromaticAberration = true)
            },
            onDrawSurface = {
                drawRect(tint ?: if (c.textPrimary.luminance() < 0.5f) Color.White.copy(alpha = if (strong) 0.26f else 0.2f)
                else Color.Black.copy(alpha = if (strong) 0.26f else 0.2f))
            },
        )
    } else {
        clip(shape)
            .then(if (c.frost != null && !overlay) Modifier.frosted(c) else Modifier)
            .background(
                // Over the photo, the same faint tint for the text as live glass has.
                if (c.frost != null && !overlay && tint == null) androidx.compose.ui.graphics.SolidColor(
                    if (c.textPrimary.luminance() < 0.5f) Color.White.copy(alpha = if (strong) 0.26f else 0.2f) else Color.Black.copy(alpha = if (strong) 0.26f else 0.2f),
                ) else glassFill(tint ?: if (strong) c.glassStrong else c.glass),
            )
    }
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
        animationSpec = tween(380, delayMillis = 30 * index.coerceIn(0, 8), easing = FastOutSlowInEasing),
        label = "appear",
    )
    LaunchedEffect(Unit) { shown = true }
    // Starts half visible, not invisible: a tab opened for the first time never looks empty while it settles.
    graphicsLayer {
        alpha = 0.5f + 0.5f * progress
        translationY = (1f - progress) * 18.dp.toPx()
    }
}
