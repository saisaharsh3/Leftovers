package com.leftovers.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/**
 * Liquid glass, over the app's aurora. Every surface bends the same picture, the light behind every screen:
 * a surface can't bend the screen it is part of, and the cards are see-through over that light anyway.
 * Null when liquid glass is off (Android older than 13, glass effects or liquid glass turned off), and then
 * each surface draws its frosted look instead.
 */
val LocalAuroraBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Whether surfaces draw as liquid glass. */
@Composable
fun liquidGlass(): Boolean = LocalAuroraBackdrop.current != null

/**
 * Draws this surface as liquid glass: what's behind it bends near its rounded edges (more on big surfaces,
 * a little on small ones), its colour is lifted a touch, and a bright rim catches the light. [surface] is the
 * tint over it. [lift] adds a soft shadow beneath, for the big cards. Does nothing when liquid glass is off.
 */
@Composable
fun Modifier.liquid(shape: Shape, surface: Color, lift: Boolean = false, refraction: Dp = 14.dp): Modifier {
    val backdrop = LocalAuroraBackdrop.current ?: return this
    val density = LocalDensity.current
    var size by remember { mutableStateOf(Size.Zero) }
    val bent = remember(shape, size, density) { liquidShape(shape, size, density) }
    return this
        .onSizeChanged { size = it.toSize() }
        .drawBackdrop(
            backdrop = backdrop,
            shape = { bent },
            effects = {
                vibrancy()
                // The bend is at most a third of the surface's shorter side, so small chips stay readable.
                val height = minOf(refraction.toPx(), size.minDimension / 3f)
                if (height > 0f) lens(height, height * 2f)
            },
            shadow = if (lift) {
                { Shadow(radius = 30.dp, offset = DpOffset(0.dp, 12.dp), color = Color.Black.copy(alpha = 0.22f)) }
            } else {
                null
            },
            // Kept inside the shape: without this the bent picture fills the whole square around a round button.
            layerBlock = {
                this.shape = bent
                clip = true
            },
            onDrawSurface = { drawRect(surface) },
        )
}

/** The lens needs to know the corner radius: a rounded shape becomes the library's own with the same corners. */
private fun liquidShape(shape: Shape, size: Size, density: Density): Shape = when (shape) {
    is RoundedCornerShape -> {
        val px = if (size.minDimension > 0f) shape.topStart.toPx(size, density) else 0f
        RoundedRectangle(with(density) { px.toDp() })
    }
    else -> shape
}
