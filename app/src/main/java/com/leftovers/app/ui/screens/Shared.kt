package com.leftovers.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun TransactionItem.timeLabel(): String =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)

@Composable
fun TransactionRow(item: TransactionItem, onClick: () -> Unit, subtitle: String = item.timeLabel()) {
    val money = LocalMoney.current
    val c = LocalAppColors.current
    val hasNote = item.note.isNotBlank()
    ListRow(
        title = if (hasNote) item.note else item.categoryName,
        subtitle = if (hasNote) "${item.categoryName} · $subtitle" else subtitle,
        leading = { CategoryIcon(item.categoryEmoji, item.categoryColor, size = 42.dp) },
        trailing = {
            Text(
                money.signed(item.amountMinor, item.type),
                style = MaterialTheme.typography.titleSmall,
                color = if (item.type == TxType.INCOME) c.positive else c.textPrimary,
            )
        },
        onClick = onClick,
    )
}

/** Borderless input on a glass capsule. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    focusRequester: FocusRequester? = null,
) {
    val c = LocalAppColors.current
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        }
        Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prefix != null) {
                    Text(prefix, style = textStyle, color = c.textSecondary)
                    Spacer(Modifier.width(6.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.textTertiary)
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = textStyle.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
fun MoneyField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, large: Boolean = false, focusRequester: FocusRequester? = null) {
    GlassTextField(
        value = value,
        onValueChange = { raw -> AmountInput.sanitize(raw)?.let(onValueChange) },
        placeholder = "0",
        label = label,
        prefix = LocalMoney.current.symbol,
        keyboardType = KeyboardType.Decimal,
        textStyle = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
        modifier = modifier,
        focusRequester = focusRequester,
    )
}

/** Amount entry dialog, shared by category limits, goals and subscriptions. */
@Composable
fun AmountDialog(
    title: String,
    initialMinor: Long?,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
    label: String = "Limit per month",
    removable: Boolean = initialMinor != null,
    removeLabel: String = "Remove",
) {
    val c = LocalAppColors.current
    var text by rememberSaveable { mutableStateOf(initialMinor?.let(AmountInput::fromMinor).orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val minor = AmountInput.toMinor(text) ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { MoneyField(text, { text = it }, label = label, large = true, focusRequester = focus) },
        confirmButton = {
            TextButton(onClick = { onSave(minor) }, enabled = minor > 0) { Text("Save", color = if (minor > 0) c.textPrimary else c.textTertiary) }
        },
        dismissButton = {
            Row {
                if (removable) TextButton(onClick = { onSave(null) }) { Text(removeLabel, color = c.negative) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) }
            }
        },
    )
}

/** Bottom sheet with the app's dark glass styling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        scrimColor = c.background.copy(alpha = 0.6f),
        modifier = Modifier.border(
            1.dp,
            com.leftovers.app.ui.components.glassBorder(c),
            RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        ),
    ) {
        Box(Modifier.navigationBarsPadding()) { content() }
    }
}

fun ordinal(n: Int): String = when {
    n in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}
