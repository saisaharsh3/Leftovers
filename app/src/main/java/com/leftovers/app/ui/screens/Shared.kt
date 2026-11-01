package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.first
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import com.leftovers.app.ui.components.pressable
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun TransactionItem.timeLabel(): String =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalTime().format(com.leftovers.app.util.timeFormatter(com.leftovers.app.util.LocalUse24Hour.current))

@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    subtitle: String = item.timeLabel(),
    modifier: Modifier = Modifier,
    /** A split bill's whole amount, shown small under this entry's share of it. */
    wholeMinor: Long? = null,
) {
    val money = LocalMoney.current
    val c = LocalAppColors.current
    // The note's words as the title; its tags after the category, on their own.
    val text = com.leftovers.app.data.noteText(item.note)
    val tags = com.leftovers.app.data.hashtags(item.note)
    val hasNote = text.isNotBlank()
    ListRow(
        title = if (hasNote) text else item.categoryName,
        modifier = modifier,
        subtitle = listOfNotNull(item.categoryName.takeIf { hasNote }, subtitle, tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" })
            .joinToString(" · "),
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
                val photo = item.receiptPath != null
                if ((foreign != null && code != null) || photo || wholeMinor != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (photo) Icon(com.leftovers.app.ui.icons.Lucide.Camera, contentDescription = "Has a photo", tint = c.textTertiary, modifier = Modifier.size(13.dp))
                        if (foreign != null && code != null) {
                            if (photo) Spacer(Modifier.width(5.dp))
                            Text(com.leftovers.app.util.Money(code).format(foreign), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        } else if (wholeMinor != null) {
                            if (photo) Spacer(Modifier.width(5.dp))
                            Text("of ${money.format(wholeMinor)}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    }
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
    /** The keyboard's ✓ key does this, when given. */
    onDone: (() -> Unit)? = null,
) {
    // The text field keeps the actions it was first given; this always runs the latest one.
    val latestOnDone by androidx.compose.runtime.rememberUpdatedState(onDone)
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
                            keyboardType = if (password && keyboardType == KeyboardType.Text) KeyboardType.Password else keyboardType,
                            capitalization = if (keyboardType == KeyboardType.Text && !password) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                            autoCorrectEnabled = !password,
                            imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { latestOnDone?.invoke() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            }
        }
    }
}

/** Like [GlassTextField], but with the cursor and selection in [value], for fields that edit text around the cursor. */
@Composable
fun GlassTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    focusRequester: FocusRequester? = null,
) {
    val c = LocalAppColors.current
    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            if (value.text.isEmpty()) Text(placeholder, style = textStyle, color = c.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

@Composable
fun MoneyField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, large: Boolean = false, focusRequester: FocusRequester? = null, symbol: String? = null, onDone: (() -> Unit)? = null) {
    GlassTextField(
        value = value,
        onValueChange = { raw -> AmountInput.sanitize(raw)?.let(onValueChange) },
        placeholder = "0",
        label = label,
        prefix = symbol ?: LocalMoney.current.symbol,
        keyboardType = KeyboardType.Decimal,
        textStyle = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
        modifier = modifier,
        focusRequester = focusRequester,
        onDone = onDone,
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
        // Nearly solid even with the screen blurred: some phones say they blur but don't, and text must stay readable.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (blurs) 0.9f else 1f),
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
    // Solid: the year list is drawn over the days, which would show through a see-through calendar.
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
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
    // Liquid glass, like the tab bar and pop-ups: drawn on the app's own screen so it can bend it. Not from
    // inside a dialog, which is a window of its own and would be drawn over the sheet.
    val layer = com.leftovers.app.ui.components.LocalPopupLayer.current
    val liquid = com.leftovers.app.ui.components.LocalScreenBackdrop.current
    val inDialog = LocalView.current.parent is DialogWindowProvider
    if (layer != null && liquid != null && !inDialog) {
        LiquidSheet(layer, liquid, onDismiss, content)
        return
    }
    val c = LocalAppColors.current
    // Sheets hold forms, so they stay more solid than dialogs even with the screen blurred behind.
    val blurs = rememberWindowBlur()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Nearly solid: on phones that say they blur but don't, the screen behind would show through the form.
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (blurs) 0.96f else 1f),
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

/**
 * Growing eases in and settles slowly; shrinking starts slowly and speeds up into the tapped card.
 * Timed rather than a spring, so the motion is spread evenly over its frames instead of mostly in the first few.
 */
private val MorphGrow = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val MorphShrink = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Where an element sits on the screen, for [MorphCard] to grow out of. */
fun Modifier.trackScreenBounds(onBounds: (androidx.compose.ui.geometry.Rect) -> Unit): Modifier =
    this.then(Modifier.onGloballyPositioned { c ->
        val at = c.positionOnScreen()
        onBounds(androidx.compose.ui.geometry.Rect(at, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())))
    })

/**
 * A card in the middle of the screen that grows out of the card that was tapped ([from], in screen coordinates),
 * like a Saving for card growing into its goal, and shrinks back into it when closed. The screen behind blurs
 * in as it opens. [content] gets a `close` that plays the shrink, dismisses, then runs what it's given.
 */
@Composable
fun MorphCard(
    from: androidx.compose.ui.geometry.Rect?,
    onDismiss: () -> Unit,
    onReveal: () -> Unit = {},
    /** The card's first frame is on screen: the tapped card can step aside now, so it never leaves a gap first. */
    onShown: () -> Unit = {},
    content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit) {
    val c = LocalAppColors.current
    // Liquid glass, like the tab bar: the card bends the screen behind it, which is blurred only lightly so the
    // bend still shows. Otherwise frosted glass over a more blurred screen.
    val liquid = com.leftovers.app.ui.components.LocalScreenBackdrop.current
    val blurs = rememberWindowBlur()
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var closing by remember { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver
    // With animations turned off on the phone, the card just appears.
    val reduceMotion = remember { runCatching { android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) }
    fun close(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            var revealed = false
            // The tapped card comes back while the pop-up is still fading into it, so there's no empty gap.
            progress.animateTo(0f, tween(300, easing = MorphShrink)) {
                if (!revealed && value < 0.3f) {
                    revealed = true
                    onReveal()
                }
            }
            if (!revealed) onReveal()
            onDismiss()
            then()
        }
    }
    /** [dim]: the pop-up dims what's behind it itself (in its own window). */
    @Composable
    fun MorphBody(dim: Boolean = true) {
        Box(
            Modifier
                .fillMaxSize()
                // Read while drawing, not composing, so the fade doesn't rebuild the card every frame.
                .drawBehind { if (dim) drawRect(c.background.copy(alpha = (if (blurs) 0.28f else 0.6f) * progress.value.coerceIn(0f, 1f))) }
                .pointerInput(Unit) { detectTapGestures { close() } },
            contentAlignment = Alignment.Center,
        ) {
            val glassShape = RoundedCornerShape(30.dp)
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { co ->
                        val at = co.positionOnScreen()
                        target = androidx.compose.ui.geometry.Rect(at, androidx.compose.ui.geometry.Size(co.size.width.toFloat(), co.size.height.toFloat()))
                    }
                    .graphicsLayer {
                        val p = progress.value
                        val src = from
                        val dst = target
                        if (src != null && dst != null && dst.width > 0 && dst.height > 0 && reduceMotion > 0f) {
                            // Grows evenly from the tapped card's size and place to the centre, like a zoom, so the
                            // contents are never squashed. It fades in over the first part and out over the last, so
                            // no empty box is left over the tapped card.
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                            val from = minOf(src.width / dst.width, src.height / dst.height).coerceIn(0.2f, 1f)
                            val k = androidx.compose.ui.util.lerp(from, 1f, p)
                            scaleX = k
                            scaleY = k
                            translationX = androidx.compose.ui.util.lerp(src.center.x - dst.center.x, 0f, p)
                            translationY = androidx.compose.ui.util.lerp(src.center.y - dst.center.y, 0f, p)
                            alpha = (p / 0.3f).coerceIn(0f, 1f)
                        } else {
                            val k = 0.94f + 0.06f * p
                            scaleX = k
                            scaleY = k
                            alpha = p.coerceIn(0f, 1f)
                        }
                        shape = glassShape
                        clip = true
                    }
                    .then(
                        if (liquid != null) {
                            // What's behind bends at the card's rounded edges, a little colour splits at the rim,
                            // and it's tinted enough for its text to read over any screen.
                            Modifier.drawBackdrop(
                                backdrop = liquid,
                                shape = { com.kyant.shapes.RoundedRectangle(30.dp) },
                                effects = {
                                    vibrancy()
                                    // Frosted inside, so icons and switches behind never show through the text.
                                    blur(28.dp.toPx())
                                    lens(22.dp.toPx(), 44.dp.toPx(), chromaticAberration = true)
                                },
                                onDrawSurface = { drawRect(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF14141B).copy(alpha = 0.62f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.62f)) },
                            )
                        } else {
                            // The same glass as the cards on screen, over the blurred screen behind.
                            Modifier
                                .background(com.leftovers.app.ui.components.glassFill(c.glassStrong), glassShape)
                                .border(1.dp, com.leftovers.app.ui.components.glassBorder(c), glassShape)
                        },
                    )
                    // Taps on the card stay on the card.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                androidx.activity.compose.BackHandler { close() }
                content { then -> close(then) }
            }
        }
    }
    val layer = com.leftovers.app.ui.components.LocalPopupLayer.current
    if (layer != null) {
        // Drawn on the app's own screen: the screen behind is softened with the same glass as the tab bar.
        val locals = androidx.compose.runtime.currentCompositionLocalContext
        val key = remember { Any() }
        LaunchedEffect(Unit) {
            launch { progress.animateTo(1f, tween(400, easing = MorphGrow)) }
            // The tapped card steps aside once the pop-up has drawn its first frame over it.
            snapshotFlow { progress.value }.first { it > 0.05f }
            onShown()
        }
        val card: @Composable () -> Unit = {
            androidx.compose.runtime.CompositionLocalProvider(locals) {
                Box(Modifier.fillMaxSize()) {
                    // What's behind: blurred lightly (liquid glass phones) and dimmed, fading in with the card.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                            .then(
                                if (liquid != null) {
                                    Modifier.drawBackdrop(
                                        backdrop = liquid,
                                        shape = { androidx.compose.ui.graphics.RectangleShape },
                                        effects = { blur(10.dp.toPx()) },
                                        onDrawSurface = { drawRect(c.background.copy(alpha = 0.32f)) },
                                    )
                                } else {
                                    Modifier.background(c.background.copy(alpha = 0.6f))
                                },
                            ),
                    )
                    MorphBody(dim = false)
                }
            }
        }
        androidx.compose.runtime.SideEffect { layer.show(key, card) }
        androidx.compose.runtime.DisposableEffect(key) { onDispose { layer.hide(key) } }
        return
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window behind blurs and dims with the card, so it never arrives late.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val radius = with(LocalDensity.current) { (if (liquid != null) 10.dp else 24.dp).roundToPx() }
        LaunchedEffect(window) {
            if (window == null) {
                onShown()
                progress.animateTo(1f, tween(400, easing = MorphGrow))
                return@LaunchedEffect
            }
            // The window fills the screen before anything moves, so the card's end place doesn't shift mid-way.
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setWindowAnimations(0)
            window.setDimAmount(0f)
            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
            launch { progress.animateTo(1f, tween(400, easing = MorphGrow)) }
            // The blur starts with the card's first frame, so the screen never blurs with no card on it.
            snapshotFlow { progress.value }.first { it > 0f }
            onShown()
            if (blurs) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            // Set once: changing the window's blur each frame makes the system redo the window every frame.
            if (blurs && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.attributes = window.attributes.apply { blurBehindRadius = radius }
            }
        }
        MorphBody()
    }
}



/**
 * A bottom sheet on the app's own screen, in liquid glass: it slides up, the screen behind dims and blurs a
 * little, and a tap outside, Back or dragging the handle down closes it. It moves up above the keyboard.
 */
@Composable
private fun LiquidSheet(
    layer: com.leftovers.app.ui.components.PopupLayer,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val locals = androidx.compose.runtime.currentCompositionLocalContext
    val key = remember { Any() }
    // 0 = off screen below, 1 = in place; the drag moves it between.
    val shown = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(1f) }
    val latestDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            shown.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            latestDismiss()
        }
    }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
    val sheet: @Composable () -> Unit = {
        androidx.compose.runtime.CompositionLocalProvider(locals) {
            val c = LocalAppColors.current
            val top = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Box(Modifier.fillMaxSize()) {
                // The screen behind: dimmed and lightly blurred, fading with the sheet. A tap closes it. Over another
                // sheet or pop-up it only dims, so that one stays in sight (the blur would show the screen instead).
                val over = layer.cards.indexOfFirst { it.first == key } > 0
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                        .then(
                            if (over) Modifier.background(c.background.copy(alpha = 0.35f))
                            else Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { androidx.compose.ui.graphics.RectangleShape },
                                effects = { blur(8.dp.toPx()) },
                                onDrawSurface = { drawRect(c.background.copy(alpha = 0.35f)) },
                            ),
                        )
                        .pointerInput(Unit) { detectTapGestures { close() } },
                )
                // Room for the sheet: below the status bar, above the keyboard; a tall form scrolls inside it.
                Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 24.dp).imePadding()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { height = it.size.height.toFloat().coerceAtLeast(1f) }
                        .graphicsLayer { translationY = (1f - shown.value) * height + drag.value }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { top },
                            effects = {
                                vibrancy()
                                // Frosted well inside: a sheet is a form, and its text must read over anything.
                                blur(30.dp.toPx())
                                lens(20.dp.toPx(), 40.dp.toPx(), chromaticAberration = true)
                            },
                            onDrawSurface = {
                                drawRect(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF14141B).copy(alpha = 0.74f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f))
                            },
                        )
                        // Taps inside stay inside.
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Column {
                        // The handle: drag down to close.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } },
                                        onDragEnd = {
                                            if (drag.value > height * 0.25f) close()
                                            else scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f)) }
                                        },
                                    )
                                }
                                .padding(top = 12.dp, bottom = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.textTertiary))
                        }
                        // Dragging down anywhere on the sheet closes it too, once what's inside is scrolled to the top.
                        fun settle() {
                            if (drag.value > height * 0.25f) close()
                            else scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f)) }
                        }
                        val pull = remember {
                            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                    // Pulled down a little, then back up: the sheet goes back up before the form scrolls.
                                    if (available.y < 0 && drag.value > 0f) {
                                        val take = maxOf(available.y, -drag.value)
                                        scope.launch { drag.snapTo(drag.value + take) }
                                        return androidx.compose.ui.geometry.Offset(0f, take)
                                    }
                                    return androidx.compose.ui.geometry.Offset.Zero
                                }

                                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                    if (available.y > 0 && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                                        scope.launch { drag.snapTo(drag.value + available.y) }
                                        return androidx.compose.ui.geometry.Offset(0f, available.y)
                                    }
                                    return androidx.compose.ui.geometry.Offset.Zero
                                }

                                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                                    if (drag.value <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                                    settle()
                                    return available
                                }
                            }
                        }
                        Box(
                            Modifier
                                .navigationBarsPadding()
                                .nestedScroll(pull)
                                // A form that doesn't scroll: the drag reaches here directly.
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } },
                                        onDragEnd = { settle() },
                                    )
                                },
                        ) { content() }
                    }
                }
                }
            }
            androidx.activity.compose.BackHandler { close() }
        }
    }
    androidx.compose.runtime.SideEffect { layer.show(key, sheet) }
    androidx.compose.runtime.DisposableEffect(key) { onDispose { layer.hide(key) } }
}


/** One line in a list of entries: an entry, or a person's share of a bill split with people (#36). */
sealed interface BillLine {
    /** [ofBill]: a part of a split bill, shown on its own line with splits shown separately. */
    data class Entry(
        val item: TransactionItem,
        val part: Boolean,
        val parts: List<TransactionItem>,
        val shares: List<com.leftovers.app.data.Debt>,
        val ofBill: Boolean = false,
    ) : BillLine
    data class Share(val debt: com.leftovers.app.data.Debt, val main: TransactionItem) : BillLine
}

/**
 * [items] as cards: entries on their own share a card, and each split bill gets one of its own, the entry
 * first, then its parts and the people who owe for it. With [oneBill] off, every entry is on its own line.
 */
internal fun billCards(items: List<TransactionItem>, shares: Map<Long, List<com.leftovers.app.data.Debt>>, oneBill: Boolean): List<List<BillLine>> {
    // Separately, each entry has a line of its own, still marked as split, with the bill's whole amount (#36).
    if (!oneBill) return listOf(items.map { BillLine.Entry(it, false, items.filter { p -> p.splitOf == it.id }, shares[it.id].orEmpty(), ofBill = it.splitOf != null) })
        .filter { it.isNotEmpty() }
    val ordered = splitOrder(items)
    val ids = ordered.map { it.id }.toSet()
    val cards = mutableListOf<List<BillLine>>()
    var plain = mutableListOf<BillLine>()
    ordered.forEach { item ->
        if (item.splitOf != null && item.splitOf in ids) return@forEach
        // In the order they were added, as in the split sheet.
        val parts = ordered.filter { it.splitOf == item.id }.sortedBy { it.id }
        val owed = shares[item.id].orEmpty()
        if (parts.isEmpty() && owed.isEmpty()) {
            plain += BillLine.Entry(item, false, emptyList(), emptyList())
        } else {
            if (plain.isNotEmpty()) { cards += plain; plain = mutableListOf() }
            cards += listOf<BillLine>(BillLine.Entry(item, false, parts, owed)) +
                parts.map { BillLine.Entry(it, true, emptyList(), emptyList()) } +
                owed.map { BillLine.Share(it, item) }
        }
    }
    if (plain.isNotEmpty()) cards += plain
    return cards
}

/** What a split bill's entry says under its title: how it was split, and the whole amount. */
internal fun billSubtitle(whenText: String, line: BillLine.Entry): String = when {
    line.part -> "part of the split"
    line.ofBill -> "$whenText · part of a split"
    // The whole amount is on the right, under this entry's share, so the line stays short enough to read.
    line.shares.isNotEmpty() -> "$whenText · split with ${line.shares.size}"
    line.parts.isNotEmpty() -> "$whenText · split"
    else -> whenText
}

/** Whether [line] sits under a split bill: one of its parts, or a person's share. */
internal fun BillLine.under(): Boolean = this is BillLine.Share || (this is BillLine.Entry && part)

/**
 * A part of a split bill, or a person's share of it: a short line in a soft panel under the bill, lined up with
 * its title, so the bill and what it's made of read as one thing without lines or icons (#36). [card] and [i] say
 * where it sits: the panel rounds off at its first and last line, and a hairline sets the people apart from the parts.
 */
@Composable
internal fun BillPartRow(card: List<BillLine>, i: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val line = card[i]
    val first = card.getOrNull(i - 1)?.under() != true
    val last = card.getOrNull(i + 1)?.under() != true
    val round = 16.dp
    val shape = RoundedCornerShape(
        topStart = if (first) round else 0.dp, topEnd = if (first) round else 0.dp,
        bottomStart = if (last) round else 0.dp, bottomEnd = if (last) round else 0.dp,
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, bottom = if (last) 12.dp else 0.dp)
            .background(c.textPrimary.copy(alpha = 0.05f), shape),
    ) {
        if (line is BillLine.Share && card.getOrNull(i - 1).let { it is BillLine.Entry && it.part }) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.textPrimary.copy(alpha = 0.07f)))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .pressable(onClick, pressedScale = 0.98f)
                .padding(start = 14.dp, end = 14.dp, top = if (first) 12.dp else 9.dp, bottom = if (last) 12.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (line) {
                is BillLine.Entry -> {
                    val text = com.leftovers.app.data.noteText(line.item.note)
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(8.dp).background(androidx.compose.ui.graphics.Color(line.item.categoryColor), androidx.compose.foundation.shape.CircleShape))
                    }
                    Spacer(Modifier.width(5.dp))
                    // The note when it has one, with its category after it, quieter.
                    Text(
                        if (text.isBlank()) androidx.compose.ui.text.AnnotatedString(line.item.categoryName)
                        else androidx.compose.ui.text.buildAnnotatedString {
                            append(text)
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.textTertiary))
                            append("  " + line.item.categoryName)
                            pop()
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(money.format(line.item.amountMinor), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                is BillLine.Share -> {
                    val d = line.debt
                    val tint = if (d.settled) c.textTertiary else c.positive
                    // A person: their initial, so they never read as a category's dot.
                    Box(Modifier.size(22.dp).background(tint.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Text(d.person.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        d.person, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(
                        (if (d.settled) "settled " else "owes ") + money.format(d.amountMinor),
                        style = MaterialTheme.typography.bodyMedium, color = tint,
                    )
                }
            }
        }
    }
}

/** A split bill's whole amount, for the entry it's split from; null for anything else. */
internal fun BillLine.Entry.whole(): Long? =
    if (part || (parts.isEmpty() && shares.isEmpty())) null
    else item.amountMinor + parts.sumOf { it.amountMinor } + shares.sumOf { it.amountMinor }

