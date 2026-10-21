package com.leftovers.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Whether the phone has animations turned off (Accessibility → Remove animations, or a 0× animation scale). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        runCatching { android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) == 0f
    }
}

/** Where a flick would come to rest, the way iOS scrolling decelerates: [velocity] in px/s, the answer in px. */
fun projectFling(velocity: Float, deceleration: Float = 0.998f): Float = velocity / 1000f * deceleration / (1f - deceleration)

/**
 * Short haptics for the moments that matter. Each is the phone's own effect for that kind of moment,
 * so it matches the rest of the system and follows the user's touch-feedback setting.
 */
object Haptics {
    /** Something was done: an entry saved, a debt paid back. */
    fun confirm(view: View) = view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)

    /** Something couldn't be done. */
    fun reject(view: View) = view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    /** A switch turned on or off. */
    fun toggle(view: View, on: Boolean) = view.performHapticFeedback(
        when {
            Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CLOCK_TICK
        },
    )

    /** A drag crossed the point where letting go would act (delete, close). */
    fun threshold(view: View) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** A small step: a page settling, a value snapping. */
    fun tick(view: View) = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** How many pop-up cards are open; the tab bar steps aside while any is, so it doesn't glow through the blur. */
object Overlays {
    var open by mutableIntStateOf(0)
}

/**
 * A full-screen sheet that can be pulled down to close. It follows the finger exactly, resists when pulled up,
 * and on letting go it closes if the pull (carried on by the flick's speed) would take it a third of the way
 * down; otherwise it springs back, keeping the finger's speed so there's no jolt. Content that scrolls
 * vertically keeps its own scroll: the sheet only moves once that content is back at its top.
 */
@Composable
fun DragToDismiss(onDismiss: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    var offset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    var settling by remember { mutableFloatStateOf(0f) } // 1 once it's closing, so drags stop
    val job = remember { arrayOfNulls<Job>(1) }
    var armed by remember { mutableIntStateOf(0) } // 1 while letting go now would close

    fun move(delta: Float): Float {
        if (settling > 0f) return 0f
        job[0]?.cancel()
        // Pulled up past the start it gives a little, and less the further it goes.
        val next = if (offset + delta < 0f) offset + delta * 0.3f else offset + delta
        val used = next - offset
        offset = next.coerceAtLeast(-height * 0.08f)
        val wouldClose = offset > height / 3f
        if (wouldClose != (armed == 1)) {
            armed = if (wouldClose) 1 else 0
            if (wouldClose) Haptics.threshold(view)
        }
        return used
    }

    fun settle(velocity: Float) {
        if (settling > 0f) return
        val landing = offset + projectFling(velocity, 0.99f)
        val close = offset > 0f && (landing > height / 3f || velocity > 2500f)
        job[0]?.cancel()
        armed = 0
        if (close) {
            settling = 1f
            if (reduceMotion) {
                onDismiss()
                return
            }
            job[0] = scope.launch {
                animate(offset, height, velocity.coerceAtLeast(0f), spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
                onDismiss()
            }
        } else {
            // A thrown sheet overshoots a touch on its way back; a slow one settles without a bounce.
            val bouncy = abs(velocity) > 800f
            job[0] = scope.launch {
                animate(offset, 0f, velocity, spring(dampingRatio = if (bouncy) 0.82f else 1f, stiffness = 500f)) { v, _ -> offset = v }
            }
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            // Pulled down and now moving back up: the sheet returns before the content scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (enabled && source == NestedScrollSource.UserInput && available.y < 0f && offset > 0f) Offset(0f, move(maxOf(available.y, -offset))) else Offset.Zero

            // The content is at its top and the finger keeps pulling down: the sheet moves.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (enabled && source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, move(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset != 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .nestedScroll(connection)
            .draggable(
                state = rememberDraggableState { move(it) },
                orientation = Orientation.Vertical,
                enabled = enabled,
                onDragStopped = { settle(it) },
            )
            .graphicsLayer {
                translationY = offset
                // Steps back very slightly as it goes, so it reads as leaving, not just sliding.
                val p = (offset / height).coerceIn(0f, 1f)
                alpha = 1f - p * 0.25f
            },
    ) { content() }
}
