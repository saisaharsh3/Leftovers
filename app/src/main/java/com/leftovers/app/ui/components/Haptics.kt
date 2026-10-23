package com.leftovers.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Short haptics for the moments that matter, each the phone's own effect for that kind of moment, so it
 * matches the rest of the system and follows the user's touch-feedback setting. Fired on the same frame as
 * the change it belongs to, and kept to real outcomes (saved, deleted, switched, chosen) so it stays meaningful.
 */
object Haptics {
    /** Something was done: an entry saved or deleted, money paid back. */
    fun confirm(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Something couldn't be done. */
    fun reject(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }

    /** A switch turned on or off. */
    fun toggle(view: View, on: Boolean) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    /** A choice among options: a segment, a chip. */
    fun select(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A small step, like Undo. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

/** [onChange] for a switch, with the switch's own click felt as it flips. */
@Composable
fun toggleHaptic(onChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val view = LocalView.current
    return { on ->
        Haptics.toggle(view, on)
        onChange(on)
    }
}
