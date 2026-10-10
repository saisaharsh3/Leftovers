package com.leftovers.app.ui.screens

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money.signed(item.amountMinor, item.type),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.type == TxType.INCOME) c.positive else c.textPrimary,
                )
                // Spent abroad: what was paid there, in that currency.
                val foreign = item.foreignMinor
                val code = item.foreignCurrency
                if (foreign != null && code != null) {
                    Text(com.leftovers.app.util.Money(code).format(foreign), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
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
    /** Masks the text and keeps it out of keyboard suggestions, for secrets like API keys. */
    password: Boolean = false,
    singleLine: Boolean = true,
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
                        singleLine = singleLine,
                        maxLines = if (singleLine) 1 else 5,
                        textStyle = textStyle.copy(color = c.textPrimary),
                        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (password) KeyboardType.Password else keyboardType,
                            capitalization = if (keyboardType == KeyboardType.Text && !password) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                            autoCorrectEnabled = !password,
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
    GlassAlertDialog(
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

/**
 * A confirmation dialog in the app's glass style: a see-through panel with the same light edge as cards and
 * sheets, over the screen blurred behind it. A dialog is its own window, so the app's frosted blur can't reach
 * the screen beneath; Android 12+ blurs it for us instead. Where window blur is off (older Android, battery
 * saver, some phones) the panel stays nearly solid so text never sits over sharp content.
 * Same parameters as Material's AlertDialog; [containerColor] and [shape] are accepted for compatibility and ignored.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(30.dp),
) {
    val c = LocalAppColors.current
    val glassShape = RoundedCornerShape(30.dp)
    val blurs = rememberWindowBlur()
    val appear = remember { Animatable(if (blurs) 0f else 1f) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlurBehindDialog(blurs, appear)
            confirmButton()
        },
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = glassShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (blurs) 0.62f else 1f),
        tonalElevation = 0.dp,
        modifier = modifier
            .appearWith(appear)
            .border(1.dp, com.leftovers.app.ui.components.glassBorder(c), glassShape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.glassStrong, androidx.compose.ui.graphics.Color.Transparent)), glassShape),
    )
}

/** Whether this phone blurs what's behind a dialog or sheet window: Android 12+ with window blur turned on. */
@Composable
private fun rememberWindowBlur(): Boolean {
    val context = LocalContext.current
    return remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
}

/** A calendar dialog in the same glass style as [GlassAlertDialog]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    val glassShape = RoundedCornerShape(30.dp)
    val blurs = rememberWindowBlur()
    val container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (blurs) 0.7f else 1f)
    val appear = remember { Animatable(if (blurs) 0f else 1f) }
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlurBehindDialog(blurs, appear)
            confirmButton()
        },
        dismissButton = dismissButton,
        shape = glassShape,
        tonalElevation = 0.dp,
        colors = DatePickerDefaults.colors(containerColor = container),
        modifier = Modifier
            .appearWith(appear)
            .border(1.dp, com.leftovers.app.ui.components.glassBorder(c), glassShape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.glassStrong, androidx.compose.ui.graphics.Color.Transparent)), glassShape),
        content = content,
    )
}

/** Fades and slightly grows a dialog panel in, driven by the same animation as the blur behind it. */
private fun Modifier.appearWith(appear: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    alpha = appear.value
    val scale = 0.94f + 0.06f * appear.value
    scaleX = scale
    scaleY = scale
}

/**
 * Blurs and lightly dims the screen behind the dialog or sheet window this is placed in (Android 12+).
 * Runs once per window, not on every redraw (each change makes the window lay out again). With [appear], the
 * system's own fade is switched off and the panel and blur come in together, so the blur never arrives late.
 */
@Composable
private fun BlurBehindDialog(enabled: Boolean, appear: Animatable<Float, AnimationVector1D>? = null) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val radius = with(LocalDensity.current) { 24.dp.roundToPx() }
    LaunchedEffect(window, enabled) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            appear?.snapTo(1f)
            return@LaunchedEffect
        }
        if (appear != null) window.setWindowAnimations(0)
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val startDim = window.attributes.dimAmount
        (appear ?: Animatable(0f)).animateTo(1f, tween(200, easing = FastOutSlowInEasing)) {
            window.attributes = window.attributes.apply {
                blurBehindRadius = (radius * value).toInt()
                // The blur already separates the dialog, so the dim eases to a lighter one to keep the frosted colour.
                dimAmount = startDim + (0.35f - startDim) * value
            }
        }
    }
}

/** Bottom sheet with the app's dark glass styling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    // Sheets hold forms, so they stay more solid than dialogs even with the screen blurred behind.
    val blurs = rememberWindowBlur()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (blurs) 0.84f else 1f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        scrimColor = c.background.copy(alpha = if (blurs) 0.3f else 0.6f),
        // Only the top edge gets the glass highlight. A full border follows the sheet's measured size,
        // which can end above its content (e.g. after the keyboard closes) and left a line across the form.
        modifier = Modifier.drawWithContent {
            drawContent()
            val r = 32.dp.toPx()
            val w = 1.dp.toPx()
            val edge = Path().apply {
                moveTo(w / 2, r)
                arcTo(Rect(w / 2, w / 2, 2 * r, 2 * r), 180f, 90f, false)
                lineTo(size.width - r, w / 2)
                arcTo(Rect(size.width - 2 * r, w / 2, size.width - w / 2, 2 * r), 270f, 90f, false)
            }
            drawPath(edge, c.borderTop, style = Stroke(w))
        },
    ) {
        BlurBehindDialog(blurs)
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
