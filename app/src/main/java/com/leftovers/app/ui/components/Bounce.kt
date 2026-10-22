package com.leftovers.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Scrolling that bounces at its ends, like iOS, instead of Android's stretch. Pulled past the top or bottom
 * (or either side), the content follows the finger with more and more resistance; let go, it springs back
 * carrying the finger's speed. A fling that reaches an end with speed to spare overshoots a little and
 * settles back. Provided app-wide, so every list and pager behaves the same.
 */
class BounceOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = BounceOverscroll()
    override fun equals(other: Any?) = other is BounceOverscrollFactory
    override fun hashCode() = 7
}

private class BounceOverscroll : OverscrollEffect {
    // How far the finger has pulled past the end; what's shown is this with resistance applied.
    private var rawX by mutableFloatStateOf(0f)
    private var rawY by mutableFloatStateOf(0f)
    private var animating: Job? = null
    private val layout = BounceNode(this)

    override val node: DelegatableNode get() = layout

    override val isInProgress: Boolean get() = rawX != 0f || rawY != 0f || animating?.isActive == true

    /** The shown offset: resistance grows the further it goes, so it slows before it stops. */
    fun shown(raw: Float, dimension: Float): Float {
        if (raw == 0f || dimension <= 0f) return 0f
        val c = 0.55f
        val a = abs(raw)
        return sign(raw) * (a * dimension * c) / (dimension + c * a)
    }

    val offsetX get() = shown(rawX, layout.width)
    val offsetY get() = shown(rawY, layout.height)

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (source == NestedScrollSource.UserInput) animating?.cancel()
        // Pulled out and now moving back: the bounce gives way first, before the content scrolls.
        val backX = relax(delta.x, rawX)
        val backY = relax(delta.y, rawY)
        rawX += backX
        rawY += backY
        val left = Offset(delta.x - backX, delta.y - backY)
        val consumed = performScroll(left)
        val leftover = left - consumed
        // At an end: the finger's pull goes into the bounce. A fling's leftover is handled in applyToFling.
        if (source == NestedScrollSource.UserInput) {
            rawX += leftover.x
            rawY += leftover.y
            return delta
        }
        return Offset(backX, backY) + consumed
    }

    private fun relax(d: Float, raw: Float): Float = when {
        raw > 0f && d < 0f -> maxOf(d, -raw)
        raw < 0f && d > 0f -> minOf(d, -raw)
        else -> 0f
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        coroutineScope {
            if (rawX != 0f || rawY != 0f) {
                // Let go while pulled out: spring back, keeping the finger's speed so there's no jolt.
                settle(velocity.x, velocity.y)
                return@coroutineScope
            }
            val left = performFling(velocity)
            // The fling reached an end with speed to spare: it overshoots a little, then comes back.
            if (abs(left.x) > 50f || abs(left.y) > 50f) settle(left.x * 0.35f, left.y * 0.35f)
        }
    }

    private suspend fun settle(vx: Float, vy: Float) = coroutineScope {
        animating?.cancel()
        animating = launch {
            val spec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            launch { animate(rawX, 0f, vx, spec) { v, _ -> rawX = v } }
            launch { animate(rawY, 0f, vy, spec) { v, _ -> rawY = v } }
        }
        animating?.join()
    }

    private operator fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
}

/** Moves the scrolled content by the bounce, read while placing so a bounce never recomposes anything. */
private class BounceNode(private val effect: BounceOverscroll) : LayoutModifierNode, Modifier.Node() {
    var width = 0f
    var height = 0f

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        width = placeable.width.toFloat()
        height = placeable.height.toFloat()
        return layout(placeable.width, placeable.height) {
            placeable.placeRelativeWithLayer(IntOffset(effect.offsetX.roundToInt(), effect.offsetY.roundToInt()))
        }
    }
}
