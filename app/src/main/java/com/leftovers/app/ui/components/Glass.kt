package com.leftovers.app.ui.components

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 * Soft light behind every screen. It holds still, except while the tabs are swiped: then each patch of light
 * drifts a fraction as far as the pages do, a different fraction each, so the glass cards read as floating
 * above it. (Moving it re-runs the bars' blur, but only during a swipe, when the content moves anyway.)
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    // The shift is read while drawing, so a swipe redraws the light without recomposing anything.
    Canvas(modifier.fillMaxSize()) { drawAurora(c, AuroraShift.x) }
}

/** Draws the aurora for [shift] (0 = Home … 3 = Plan); also used for the copy the liquid tab bar bends. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(c: AppColors, shift: Float) {
    drawRect(c.background)
    val w = size.width
    val h = size.height
    val drift = 0.5f
    // Centred on the middle of the four tabs, so Home and Plan drift equally far either way.
    val s = shift - 1.5f
    fun blob(color: Color, x: Float, y: Float, radius: Float, alpha: Float, depth: Float) {
        val center = Offset(x - s * w * depth, y)
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
    val strength = if (c.isDark) 1f else 2.1f
    blob(c.auroraA, w * (0.1f + 0.3f * drift), h * (0.06f + 0.05f * drift), w * 1.0f, 0.42f * strength, 0.16f)
    blob(c.auroraB, w * (1.0f - 0.25f * drift), h * (0.32f + 0.12f * drift), w * 0.85f, 0.24f * strength, 0.26f)
    blob(c.auroraC, w * (0.2f + 0.35f * drift), h * (0.98f - 0.1f * drift), w * 0.8f, 0.14f * strength, 0.1f)
}

/** Fine grey grain, tiled over glass so dark gradients don't band and the surface reads as frosted. */
private val grain: androidx.compose.ui.graphics.ImageBitmap by lazy {
    val n = 96
    val random = java.util.Random(7)
    val pixels = IntArray(n * n) {
        val v = random.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
private val grainBrush by lazy {
    androidx.compose.ui.graphics.ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(grain, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated),
    )
}

fun glassBorder(c: AppColors) = Brush.verticalGradient(listOf(c.borderTop, c.borderBottom))

/** The tint liquid glass lays over what it bends: a little lighter than a frosted card's, as the glass does the rest. */
internal fun glassFillColor(fill: Color): Color = fill.copy(alpha = fill.alpha * 0.8f)

/** A see-through fill catches light at the top, like a pane of glass, in both themes; solid fills stay flat. */
internal fun glassFill(fill: Color): Brush =
    if (fill.alpha >= 0.9f) androidx.compose.ui.graphics.SolidColor(fill)
    else Brush.verticalGradient(listOf(fill.copy(alpha = (fill.alpha * 1.7f).coerceAtMost(1f)), fill))

/** Translucent card with a hairline top-lit border. Clickable cards gently shrink when pressed. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    /** A control (button) rather than content: drawn as liquid glass when that's on. Cards stay frosted. */
    control: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAppColors.current
    val fill = tint ?: if (strong) c.glassStrong else c.glass
    // Glass effects on: real glass touches. Off (solid cards): the plain card it was.
    val glassy = fill.alpha < 0.9f
    // Liquid glass bends the light behind it and brings its own rim and shadow; otherwise the frosted touches below.
    val liquid = control && glassy && liquidGlass()
    // The glass brightens under the finger, as if light hit it, and dims back as it lets go.
    var lit by remember { mutableStateOf(false) }
    val light by animateFloatAsState(
        if (lit) 1f else 0f,
        if (lit) spring(dampingRatio = 1f, stiffness = 1600f) else spring(dampingRatio = 1f, stiffness = 380f),
        label = "glassLight",
    )
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick, onPressedChange = { lit = it }) else Modifier)
            // Bigger, more important cards are thicker glass: a soft shadow lifts them off the background.
            .then(
                if (liquid) Modifier.liquid(shape, glassFillColor(fill))
                else if (strong && glassy) Modifier.dropShadow(
                    shape,
                    androidx.compose.ui.graphics.shadow.Shadow(
                        radius = 30.dp,
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 12.dp),
                        color = Color.Black.copy(alpha = if (c.isDark) 0.24f else 0.07f),
                    ),
                ) else Modifier,
            )
            .clip(shape)
            .then(if (liquid) Modifier else Modifier.background(glassFill(fill)))
            .then(
                if (glassy && !liquid) Modifier.drawBehind {
                    drawRect(grainBrush, alpha = if (c.isDark) 0.035f else 0.03f)
                    // A little shade gathers along the bottom, like the far edge of a pane.
                    drawRect(Brush.verticalGradient(0.68f to Color.Transparent, 1f to Color.Black.copy(alpha = if (c.isDark) 0.14f else 0.035f)))
                } else Modifier,
            )
            .then(if (liquid) Modifier else Modifier.border(1.dp, glassBorder(c), shape))
            .then(
                if (liquid) Modifier.drawWithContent {
                    drawContent()
                    if (light > 0f) drawRect(Color.White.copy(alpha = (if (c.isDark) 0.06f else 0.2f) * light))
                } else if (glassy) Modifier.drawWithContent {
                    drawContent()
                    // The rim: light catching the top edge, just inside it, fading down the sides.
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline,
                        Brush.verticalGradient(0f to Color.White.copy(alpha = if (c.isDark) 0.22f else 0.9f), 0.2f to Color.Transparent, endY = size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                    if (light > 0f) drawRect(Color.White.copy(alpha = (if (c.isDark) 0.06f else 0.2f) * light))
                } else Modifier,
            ),
        content = content,
    )
}

/** Click handling with a springy press-down scale instead of a ripple. */
fun Modifier.pressable(
    onClick: () -> Unit,
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    /** Told when the finger lands and lifts, for things that light up while pressed. */
    onPressedChange: ((Boolean) -> Unit)? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    // Watched straight from the touch, not from the click: inside a scrolling list a click only reports a press
    // after a short wait, and the press should show the moment the finger lands. If the touch turns into a scroll,
    // the list takes it and the press lets go.
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (down && enabled) pressedScale else 1f,
        // In quickly; out with a small springy bounce.
        if (down) spring(dampingRatio = 1f, stiffness = 1800f) else spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            down = true
            onPressedChange?.invoke(true)
            // The final pass sees whether a scrolling parent took the touch.
            waitForUpOrCancellation(PointerEventPass.Final)
            down = false
            onPressedChange?.invoke(false)
        }
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Fades and lifts an item into place the first time it appears, staggered by [index]. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { androidx.compose.animation.core.Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (shown) return@LaunchedEffect
        kotlinx.coroutines.delay(35L * index.coerceIn(0, 8))
        // Rises on a spring that overshoots a touch, so each section lands rather than slides.
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 240f))
        shown = true
    }
    // Starts half visible, not invisible: a tab opened for the first time never looks empty while it settles.
    graphicsLayer {
        val p = progress.value
        alpha = (0.5f + 0.5f * p).coerceIn(0f, 1f)
        translationY = (1f - p) * 22.dp.toPx()
        val k = 0.97f + 0.03f * p
        scaleX = k
        scaleY = k
    }
}
