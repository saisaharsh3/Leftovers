package com.leftovers.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One undoable action: what happened, and how to take it back. */
class UndoRequest(val message: String, val undo: suspend () -> Unit)

/**
 * App-wide Undo. It lives above the screens, so the message stays for its full time even when the user
 * swipes to another tab straight after deleting something.
 */
class UndoState(private val scope: CoroutineScope) {
    var current by mutableStateOf<UndoRequest?>(null)
        private set

    fun show(message: String, undo: suspend () -> Unit) {
        current = UndoRequest(message, undo)
    }

    fun undo(request: UndoRequest) {
        if (current === request) current = null
        scope.launch { request.undo() }
    }

    fun dismiss(request: UndoRequest) {
        if (current === request) current = null
    }

    companion object {
        const val VISIBLE_MS = 8_000L
    }
}

val LocalUndo = staticCompositionLocalOf<UndoState> { error("No UndoState provided") }

/** Frosted glass pill like the app's other notices: message, a lime Undo, and a close button. */
@Composable
fun UndoBar(request: UndoRequest, onUndo: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    // Clip before blurring so the frost follows the pill shape instead of filling a rectangle.
    Glass(shape = CircleShape, strong = true, modifier = modifier.widthIn(max = 420.dp).clip(CircleShape).frosted()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Trash2, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                request.message,
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Undo",
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .pressable(onUndo, pressedScale = 0.92f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            RoundButton(Lucide.X, "Dismiss", onDismiss, size = 34.dp, tint = c.textSecondary)
        }
    }
}
