package com.leftovers.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T

/**
 * A panel that comes in from the right edge over the dimmed window and goes back the same way: for adding and
 * changing things. Esc closes it, Ctrl+Enter does [onSave]. Its content is only composed while it's shown.
 */
@Composable
fun SidePanel(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    width: Dp = 460.dp,
    onSave: (() -> Unit)? = null,
    footer: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    val c = T.c
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state, enter = fadeIn(Motion.settle()), exit = fadeOut(Motion.quick())) {
            Box(Modifier.fillMaxSize().background(c.scrim.copy(alpha = c.scrim.alpha * 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        }
        AnimatedVisibility(
            state, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(Motion.offset) { it } + fadeIn(Motion.settle()),
            exit = slideOutHorizontally(Motion.offset) { it } + fadeOut(Motion.quick()),
        ) {
            Column(
                Modifier.width(width).fillMaxHeight().padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (c.dark) Color(0xFF16161C) else Color.White)
                    .border(1.dp, c.line, RoundedCornerShape(20.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            it.key == Key.Escape -> { onClose(); true }
                            it.key == Key.Enter && it.isCtrlPressed && onSave != null -> { onSave(); true }
                            else -> false
                        }
                    },
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = T.type.title, color = c.text, modifier = Modifier.weight(1f))
                    IconButton(Lucide.X, onClose, tooltip = "Close")
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = footer)
            }
        }
    }
}
