package com.leftovers.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * The screen as the liquid glass tab bar sees it: the aurora and the current screen, recorded apart from what's
 * shown. Pop-ups ([com.leftovers.app.ui.screens.MorphCard]) bend it too, so they're the same glass as the bar.
 * Null when liquid glass is off (Android older than 13, or Glass effects off); then pop-ups are frosted.
 */
val LocalScreenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Pop-up cards ([com.leftovers.app.ui.screens.MorphCard]) are drawn here, on top of the app's own screen, rather
 * than in a window of their own. On some phones a window that blurs what's behind it stops the app beneath it
 * from redrawing until the next touch, so a tapped card wouldn't step aside, or wouldn't come back.
 */
class PopupLayer {
    internal val cards = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()

    fun show(key: Any, card: @Composable () -> Unit) {
        val at = cards.indexOfFirst { it.first == key }
        if (at >= 0) cards[at] = key to card else cards += key to card
    }

    fun hide(key: Any) {
        cards.removeAll { it.first == key }
    }

    @Composable
    fun Content() {
        cards.toList().forEach { (_, card) -> card() }
    }
}

val LocalPopupLayer = staticCompositionLocalOf<PopupLayer?> { null }
