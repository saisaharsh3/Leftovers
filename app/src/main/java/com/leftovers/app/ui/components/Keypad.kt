package com.leftovers.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors

sealed interface KeypadKey {
    data class Digit(val value: Char) : KeypadKey
    data object Dot : KeypadKey
    data object DoubleZero : KeypadKey
    data object Erase : KeypadKey
}

private val keyHeight = 60.dp
private val gap = 10.dp

@Composable
fun Keypad(
    onKey: (KeypadKey) -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Add",
) {
    val c = LocalAppColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { ch ->
                        Key(Modifier.weight(1f), onClick = { onKey(KeypadKey.Digit(ch)) }) { KeyLabel(ch.toString()) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Key(Modifier.weight(1f), onClick = { onKey(KeypadKey.Dot) }) { KeyLabel(".") }
                Key(Modifier.weight(1f), onClick = { onKey(KeypadKey.Digit('0')) }) { KeyLabel("0") }
                Key(Modifier.weight(1f), onClick = { onKey(KeypadKey.DoubleZero) }) { KeyLabel("00") }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
            Key(
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Erase. Long press to clear" },
                onClick = { onKey(KeypadKey.Erase) },
                onLongClick = onClear,
            ) {
                Icon(Lucide.Delete, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(24.dp))
            }
            val bg by animateColorAsState(if (confirmEnabled) c.accent else c.glass, label = "confirmBg")
            val fg by animateColorAsState(if (confirmEnabled) c.onAccent else c.textTertiary, label = "confirmFg")
            Key(
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = confirmLabel },
                height = keyHeight * 3 + gap * 2,
                fill = bg,
                bordered = !confirmEnabled,
                onClick = onConfirm,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Lucide.Check, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
                    Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontSize = if (text.length > 1) 20.sp else 26.sp,
        color = LocalAppColors.current.textPrimary,
    )
}

@Composable
private fun Key(
    modifier: Modifier,
    onClick: () -> Unit,
    height: Dp = keyHeight,
    fill: Color? = null,
    bordered: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "keyScale")
    val glow by animateColorAsState(
        if (pressed) c.glassStrong.copy(alpha = c.glassStrong.alpha * 2.2f) else fill ?: c.glass,
        label = "keyGlow",
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (fill != null && !pressed) fill else glow)
            .then(if (bordered) Modifier.border(1.dp, glassBorder(c), shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        it()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}
