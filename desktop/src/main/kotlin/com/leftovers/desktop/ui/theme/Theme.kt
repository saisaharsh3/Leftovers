package com.leftovers.desktop.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.leftovers.desktop.data.Theme

/**
 * The desktop's colours. Calm by default: a quiet ground, plain cards with a hairline edge, and colour kept for
 * money (green in, red over) and the one thing to press. [glass] swaps the cards for frosted ones over a soft
 * colour field, like the phone's look.
 */
@Immutable
data class Colors(
    val dark: Boolean,
    val glass: Boolean,
    /** The window's ground, and the sidebar's. */
    val ground: Color,
    val sidebar: Color,
    /** Cards and sheets. */
    val surface: Color,
    /** Something raised on a card: a hovered row, a field, a chip. */
    val raised: Color,
    val hover: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    /** The one thing to press. */
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    /** The colour field behind glass. */
    val fieldA: Color,
    val fieldB: Color,
    val fieldC: Color,
    val scrim: Color,
)

private val Light = Colors(
    dark = false, glass = false,
    ground = Color(0xFFF4F4F7), sidebar = Color(0xFFEDEDF2),
    surface = Color(0xFFFFFFFF), raised = Color(0xFFF2F2F6), hover = Color(0x0A0E0E12), line = Color(0xFFE3E3EA),
    text = Color(0xFF0E0E12), text2 = Color(0xFF5B5B66), text3 = Color(0xFF8A8A95),
    accent = Color(0xFF16161B), onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF1F9D61), negative = Color(0xFFD94848), warning = Color(0xFFC08A12),
    fieldA = Color(0xFF8C82FF), fieldB = Color(0xFF5FD0C4), fieldC = Color(0xFFC4E35F),
    scrim = Color(0x660E0E12),
)

private val Dark = Colors(
    dark = true, glass = false,
    ground = Color(0xFF0C0C10), sidebar = Color(0xFF101015),
    surface = Color(0xFF16161C), raised = Color(0xFF1E1E26), hover = Color(0x0FFFFFFF), line = Color(0xFF25252E),
    text = Color(0xFFF5F5F7), text2 = Color(0xFFA1A1AA), text3 = Color(0xFF7C7C87),
    accent = Color(0xFFCDF46F), onAccent = Color(0xFF151A04),
    positive = Color(0xFF7FD9A8), negative = Color(0xFFF58A8A), warning = Color(0xFFF2C46D),
    fieldA = Color(0xFF5048E5), fieldB = Color(0xFF0E9F9A), fieldC = Color(0xFF8BB82B),
    scrim = Color(0x99000000),
)

private fun Colors.asGlass() = copy(
    glass = true,
    sidebar = if (dark) Color(0x99101015) else Color(0x8CF4F4F8),
    surface = if (dark) Color(0x8C1A1A22) else Color(0xB3FFFFFF),
    raised = if (dark) Color(0x14FFFFFF) else Color(0x99FFFFFF),
    line = if (dark) Color(0x1FFFFFFF) else Color(0x99FFFFFF),
)

val LocalColors = staticCompositionLocalOf { Light }

private val manrope: FontFamily by lazy {
    fun face(w: Int) = Font("fonts/manrope.ttf", FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    FontFamily(face(400), face(500), face(600), face(700), face(800))
}

/** The type scale: tight and heavy for figures and titles, a touch of tracking on small text. */
@Immutable
data class Type(
    val hero: TextStyle,
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
)

private fun style(size: Int, weight: Int, tracking: TextUnit, line: Float) =
    TextStyle(fontFamily = manrope, fontSize = size.sp, fontWeight = FontWeight(weight), letterSpacing = tracking, lineHeight = (size * line).sp)

private val TypeScale = Type(
    hero = style(52, 700, (-0.035).em, 1.05f),
    display = style(32, 700, (-0.025).em, 1.1f),
    title = style(22, 700, (-0.015).em, 1.2f),
    heading = style(15, 700, (-0.005).em, 1.3f),
    body = style(14, 500, 0.em, 1.45f),
    bodyStrong = style(14, 650, 0.em, 1.45f),
    small = style(12, 500, 0.01.em, 1.4f),
    label = style(11, 700, 0.06.em, 1.3f),
)

val LocalType = staticCompositionLocalOf { TypeScale }

/** Springs, as the phone uses them: no bounce for things that just appear, a little for things moved. */
object Motion {
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f)
    fun <T> quick(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow * 2.5f)
    fun <T> moved(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)
    val offset = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f, visibilityThreshold = IntOffset(1, 1))
}

object T {
    val c: Colors @Composable get() = LocalColors.current
    val type: Type @Composable get() = LocalType.current
}

@Composable
fun LeftoversTheme(theme: Theme, glass: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) {
        Theme.SYSTEM -> isSystemInDarkTheme()
        Theme.LIGHT -> false
        Theme.DARK -> true
    }
    val base = if (dark) Dark else Light
    val colors = if (glass) base.asGlass() else base
    val scheme = if (dark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, background = colors.ground, surface = colors.surface,
        onSurface = colors.text, onBackground = colors.text, outline = colors.line, error = colors.negative,
        surfaceContainer = Dark.surface, surfaceContainerHigh = Dark.raised, surfaceContainerLow = Dark.surface,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, background = colors.ground, surface = colors.surface,
        onSurface = colors.text, onBackground = colors.text, outline = colors.line, error = colors.negative,
        surfaceContainer = Light.surface, surfaceContainerHigh = Light.surface, surfaceContainerLow = Light.surface,
    )
    CompositionLocalProvider(LocalColors provides colors, LocalType provides TypeScale) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.copy(bodyLarge = TypeScale.body, bodyMedium = TypeScale.body, labelLarge = TypeScale.bodyStrong), content = content)
    }
}
