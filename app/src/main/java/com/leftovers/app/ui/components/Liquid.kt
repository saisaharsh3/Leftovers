package com.leftovers.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * The screen as the liquid glass tab bar sees it: the aurora and the current screen, recorded apart from what's
 * shown. Pop-ups ([com.leftovers.app.ui.screens.MorphCard]) bend it too, so they're the same glass as the bar.
 * Null when liquid glass is off (Android older than 13, or Glass effects off); then pop-ups are frosted.
 */
val LocalScreenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
