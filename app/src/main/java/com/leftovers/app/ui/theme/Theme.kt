package com.leftovers.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leftovers.app.R

/** Design tokens for the glass look. Everything else reads from MaterialTheme. */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    /** Translucent fills for glass surfaces. */
    val glass: Color,
    val glassStrong: Color,
    /** Hairline borders: lighter at the top edge, fading towards the bottom. */
    val borderTop: Color,
    val borderBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    /** Slow-moving light in the background. */
    val auroraA: Color,
    val auroraB: Color,
    val auroraC: Color,
    /** Bar tint applied over blurred content. */
    val barTint: Color,
    /** The user's own background photo (already tiny, so it draws blurred), and how much it's dimmed. */
    val photo: androidx.compose.ui.graphics.ImageBitmap? = null,
    val photoDim: Float = 0.55f,
    /** A much softer copy of the photo that glass cards show through, like frosted glass in front of it. */
    val frost: androidx.compose.ui.graphics.ImageBitmap? = null,
)

private val DarkTokens = AppColors(
    isDark = true,
    background = Color(0xFF09090C),
    glass = Color.White.copy(alpha = 0.055f),
    glassStrong = Color.White.copy(alpha = 0.09f),
    borderTop = Color.White.copy(alpha = 0.14f),
    borderBottom = Color.White.copy(alpha = 0.03f),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1AA),
    textTertiary = Color(0xFF8E8E99),
    accent = Color(0xFFCDF46F),
    onAccent = Color(0xFF151A04),
    positive = Color(0xFF7FD9A8),
    negative = Color(0xFFF58A8A),
    warning = Color(0xFFF2C46D),
    auroraA = Color(0xFF5048E5),
    auroraB = Color(0xFF0E9F9A),
    auroraC = Color(0xFF8BB82B),
    barTint = Color(0xFF0E0E13).copy(alpha = 0.46f),
)

private val LightTokens = AppColors(
    isDark = false,
    background = Color(0xFFEDEEF4),
    // Built like the dark glass: a panel a little brighter than what's behind it, lit at the top edge,
    // with the rim fading down the sides. Stronger values, so it reads on a light background.
    glass = Color.White.copy(alpha = 0.18f),
    glassStrong = Color.White.copy(alpha = 0.3f),
    borderTop = Color.White.copy(alpha = 0.7f),
    borderBottom = Color.White.copy(alpha = 0.12f),
    textPrimary = Color(0xFF0E0E12),
    textSecondary = Color(0xFF5B5B66),
    textTertiary = Color(0xFF6E6E79),
    accent = Color(0xFF16161B),
    onAccent = Color.White,
    positive = Color(0xFF1F9D61),
    negative = Color(0xFFD94848),
    warning = Color(0xFFC08A12),
    // Richer than pastel, so light glass has colour to show through.
    auroraA = Color(0xFF8C82FF),
    auroraB = Color(0xFF5FD0C4),
    auroraC = Color(0xFFC4E35F),
    barTint = Color.White.copy(alpha = 0.55f),
)

val LocalAppColors = staticCompositionLocalOf { DarkTokens }

/**
 * Glass effects off: cards become near-solid with a quiet edge instead of see-through glass with a lit rim.
 * Bars, dialogs and sheets keep their frosted blur.
 */
private fun AppColors.solid(): AppColors = if (isDark) {
    copy(
        glass = Color(0xFF17171E),
        glassStrong = Color(0xFF1E1E27),
        borderTop = Color.White.copy(alpha = 0.06f),
        borderBottom = Color.White.copy(alpha = 0.03f),
    )
} else {
    copy(
        glass = Color.White.copy(alpha = 0.94f),
        glassStrong = Color.White,
        borderTop = Color.Black.copy(alpha = 0.05f),
        borderBottom = Color.Black.copy(alpha = 0.05f),
    )
}

/**
 * True black for OLED screens: the background and bars are black and the coloured glow behind every screen is
 * off, so unlit pixels stay off. Cards keep their faint glass so they still stand out.
 */
private fun AppColors.pureBlack(): AppColors = copy(
    background = Color.Black,
    barTint = Color.Black.copy(alpha = 0.6f),
    auroraA = Color.Black,
    auroraB = Color.Black,
    auroraC = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = DarkTokens.accent,
    onPrimary = DarkTokens.onAccent,
    primaryContainer = Color(0xFF2B331A),
    onPrimaryContainer = DarkTokens.accent,
    secondary = Color(0xFFB4B4FF),
    secondaryContainer = Color(0xFF26263A),
    onSecondaryContainer = Color(0xFFE2E2FF),
    background = DarkTokens.background,
    onBackground = DarkTokens.textPrimary,
    surface = DarkTokens.background,
    onSurface = DarkTokens.textPrimary,
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = DarkTokens.textSecondary,
    surfaceContainerLowest = Color(0xFF0C0C10),
    surfaceContainerLow = Color(0xFF131318),
    surfaceContainer = Color(0xFF17171D),
    surfaceContainerHigh = Color(0xFF1C1C23),
    surfaceContainerHighest = Color(0xFF24242C),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF26262E),
    error = DarkTokens.negative,
    onError = Color(0xFF2A0606),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFD6D6),
    inverseSurface = DarkTokens.textPrimary,
    inverseOnSurface = DarkTokens.background,
)

private val LightScheme = lightColorScheme(
    primary = LightTokens.accent,
    onPrimary = LightTokens.onAccent,
    primaryContainer = Color(0xFFE6E6EE),
    onPrimaryContainer = LightTokens.textPrimary,
    secondary = Color(0xFF4F4FC9),
    secondaryContainer = Color(0xFFE4E4FF),
    background = LightTokens.background,
    onBackground = LightTokens.textPrimary,
    surface = LightTokens.background,
    onSurface = LightTokens.textPrimary,
    surfaceVariant = Color(0xFFE9E9EF),
    onSurfaceVariant = LightTokens.textSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF6F6F9),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEDEDF2),
    outline = Color(0xFFCFCFD8),
    outlineVariant = Color(0xFFE3E3EA),
    error = LightTokens.negative,
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = FontFamily(
    Font(R.font.manrope, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))),
)

private val Bold = manrope(700)
private val SemiBold = manrope(600)
private val Medium = manrope(500)

/** Tabular figures keep amounts from jittering while they animate. */
private fun style(family: FontFamily, size: Int, line: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
)

private val AppTypography = Typography(
    displayLarge = style(Bold, 64, 68, -2.2f),
    displayMedium = style(Bold, 46, 52, -1.4f),
    displaySmall = style(Bold, 34, 40, -1f),
    headlineLarge = style(Bold, 30, 36, -0.8f),
    headlineMedium = style(Bold, 26, 32, -0.6f),
    headlineSmall = style(SemiBold, 22, 28, -0.4f),
    titleLarge = style(SemiBold, 19, 26, -0.3f),
    titleMedium = style(SemiBold, 16, 22, -0.1f),
    titleSmall = style(SemiBold, 14, 20),
    bodyLarge = style(Medium, 16, 23),
    bodyMedium = style(Medium, 14, 20),
    bodySmall = style(Medium, 12, 16),
    labelLarge = style(SemiBold, 14, 20),
    labelMedium = style(SemiBold, 12, 16, 0.2f),
    labelSmall = style(SemiBold, 11, 14, 0.4f),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun LeftoversTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    glassEffects: Boolean = true,
    /** Dark theme only: black background and no glow, for OLED screens. */
    pureBlack: Boolean = false,
    /** What's behind every screen, and the photo for [Backdrop.PHOTO]. */
    backdrop: Backdrop = Backdrop.AURORA,
    photo: androidx.compose.ui.graphics.ImageBitmap? = null,
    photoDim: Float = 0.55f,
    frost: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** Over a photo: black text rather than white. */
    blackText: Boolean = false,
    /** A chosen accent colour; null for the default (or the wallpaper's, when that's on). */
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val black = darkTheme && pureBlack
    val tokens = (if (darkTheme) DarkTokens else LightTokens)
        .let { t ->
            when (backdrop) {
                Backdrop.AURORA -> t
                Backdrop.PLAIN -> t.copy(auroraA = Color.Transparent, auroraB = Color.Transparent, auroraC = Color.Transparent)
                // Over a photo, like Apple's clear Liquid Glass: the picture in its own colours, cards of the same clear
                // glass whatever the text colour, and the text itself white or black ([blackText]). Pop-ups and bars
                // behind text take the opposite tone, so their text reads too.
                Backdrop.PHOTO -> if (photo == null) t else {
                    val ink = if (blackText) Color(0xFF0E0E12) else Color(0xFFF7F7F9)
                    t.copy(
                        photo = photo, photoDim = photoDim, frost = frost,
                        textPrimary = ink, textSecondary = ink.copy(alpha = 0.78f), textTertiary = ink.copy(alpha = 0.62f),
                        // Red and green follow the text: deep with black text, light with white.
                        // Far from mid-tones, so they stand out on any colour in the photo (even green on green).
                        negative = if (blackText) Color(0xFF9B1219) else Color(0xFFFFC2C2),
                        positive = if (blackText) Color(0xFF07502B) else Color(0xFFC2F7DA),
                        glass = Color.White.copy(alpha = 0.1f), glassStrong = Color.White.copy(alpha = 0.16f),
                        borderTop = Color.White.copy(alpha = 0.5f), borderBottom = Color.White.copy(alpha = 0.12f),
                        // Bars stay nearly clear over a photo, like the cards: just enough tint for their text.
                        barTint = if (blackText) Color.White.copy(alpha = 0.22f) else Color(0xFF0E0E13).copy(alpha = 0.2f),
                    )
                }
                // Lighter on the light theme, like the standard glow.
                else -> if (darkTheme) t.copy(auroraA = backdrop.a, auroraB = backdrop.b, auroraC = backdrop.c)
                else t.copy(auroraA = lerp(backdrop.a, Color.White, 0.35f), auroraB = lerp(backdrop.b, Color.White, 0.35f), auroraC = lerp(backdrop.c, Color.White, 0.35f))
            }
        }
        .let { if (glassEffects) it else it.solid() }// Pure black is for the glow backgrounds; a chosen photo is shown as it is, with its own text colour.
        .let { if (black && it.photo == null) it.pureBlack() else it }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Keep the glass background; borrow only the accent from the wallpaper.
            (if (darkTheme) DarkScheme else LightScheme).copy(primary = dynamic.primary, onPrimary = dynamic.onPrimary)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }.let { if (black) it.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black, inverseOnSurface = Color.Black) else it }
        // A chosen accent wins over the wallpaper's.
        .let { s -> accent?.let { themedAccent(it, darkTheme) }?.let { (a, on) -> s.copy(primary = a, onPrimary = on) } ?: s }
    val resolvedTokens = if (scheme.primary != tokens.accent) tokens.copy(accent = scheme.primary, onAccent = scheme.onPrimary) else tokens
    val typography = AppTypography
    CompositionLocalProvider(LocalAppColors provides resolvedTokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = AppShapes, content = content)
    }
}
