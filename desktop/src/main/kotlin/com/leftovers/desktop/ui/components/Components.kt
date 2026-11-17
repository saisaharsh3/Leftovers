package com.leftovers.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image as SkiaImage

val CardShape = RoundedCornerShape(16.dp)
val FieldShape = RoundedCornerShape(10.dp)

/** A card: a plain surface with a hairline edge, or frosted glass with a bright edge when glass is on. */
@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = T.c
    Column(
        modifier
            .clip(CardShape)
            .background(c.surface)
            .border(BorderStroke(1.dp, c.line), CardShape)
            .padding(padding),
        content = content,
    )
}

/**
 * Press feedback the moment the pointer goes down, a hover tint, and a hand cursor: everything clickable on the
 * desktop goes through this, so it all feels the same.
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true, scaleTo: Float = 0.97f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scaleTo else 1f, Motion.quick())
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(source, indication = null, enabled = enabled, onClick = onClick)
}

/** A row that tints on hover and press: list items, menu items. */
@Composable
fun HoverRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    /** Tints on hover even with no [onClick], for rows whose clicks are handled outside (selection). */
    hoverTint: Boolean = onClick != null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = T.c
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            selected -> c.raised
            pressed -> c.hover.copy(alpha = (c.hover.alpha * 2).coerceAtMost(1f))
            hovered && hoverTint -> c.hover
            else -> Color.Transparent
        },
        Motion.quick(),
    )
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .hoverable(source)
            .then(if (onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(source, null, onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

enum class ButtonKind { Primary, Secondary, Quiet, Danger }

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = T.c
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.accent to c.onAccent
        ButtonKind.Secondary -> c.raised to c.text
        ButtonKind.Quiet -> Color.Transparent to c.text2
        ButtonKind.Danger -> c.negative.copy(alpha = 0.12f) to c.negative
    }
    val tint by animateColorAsState(
        when {
            !enabled -> bg.copy(alpha = bg.alpha * 0.5f)
            hovered && kind == ButtonKind.Quiet -> c.hover
            hovered -> lerpOver(bg, if (c.dark) Color.White else Color.Black, 0.06f)
            else -> bg
        },
        Motion.quick(),
    )
    Row(
        modifier
            .hoverable(source)
            .pressable(enabled, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(tint)
            .padding(horizontal = if (kind == ButtonKind.Quiet) 10.dp else 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = if (enabled) fg else fg.copy(alpha = 0.5f))
        Text(text, style = T.type.bodyStrong, color = if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

private fun lerpOver(base: Color, over: Color, amount: Float): Color {
    if (base.alpha == 0f) return over.copy(alpha = amount)
    return Color(
        base.red + (over.red - base.red) * amount,
        base.green + (over.green - base.green) * amount,
        base.blue + (over.blue - base.blue) * amount,
        base.alpha,
    )
}

/** A round icon button: close, more, back. */
@Composable
fun IconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = T.c.text2, size: Dp = 32.dp, tooltip: String? = null) {
    val c = T.c
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) c.hover else Color.Transparent, Motion.quick())
    Box(
        modifier.size(size).hoverable(source).pressable(onClick = onClick, scaleTo = 0.9f).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, tooltip, Modifier.size(size * 0.5f), tint = tint)
    }
}

/** A text field in the app's style: a soft filled box that shows a ring when focused. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    style: TextStyle = T.type.body,
    singleLine: Boolean = true,
    password: Boolean = false,
    leading: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onEnter: (() -> Unit)? = null,
    minLines: Int = 1,
) {
    val c = T.c
    var focused by remember { mutableStateOf(false) }
    val ring by animateColorAsState(if (focused) c.text.copy(alpha = 0.35f) else Color.Transparent, Motion.quick())
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = style.copy(color = c.text),
        cursorBrush = SolidColor(c.text),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (onEnter != null && it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) { onEnter(); true } else false
            },
        decorationBox = { inner ->
            Row(
                Modifier.clip(FieldShape).background(c.raised).border(1.5.dp, ring, FieldShape).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, Modifier.size(16.dp), tint = c.text3)
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = c.text3, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** A category's or account's icon on a soft disc of its colour, or the picture the user made for it. */
@Composable
fun IconBadge(key: String, color: Long, size: Dp = 36.dp, icons: Map<String, String> = emptyMap()) {
    val tint = Color(color)
    val picture = if (key.startsWith("img:")) rememberPicture(icons[key.removePrefix("img:").substringAfterLast('/')]) else null
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (T.c.dark) 0.18f else 0.22f)), contentAlignment = Alignment.Center) {
        if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(CategoryIcons[key], null, Modifier.size(size * 0.5f), tint = if (T.c.dark) tint else darken(tint))
    }
}

private fun darken(c: Color) = Color(c.red * 0.62f, c.green * 0.62f, c.blue * 0.62f, 1f)

@Composable
private fun rememberPicture(base64: String?): ImageBitmap? = remember(base64) {
    base64?.let { runCatching { SkiaImage.makeFromEncoded(java.util.Base64.getDecoder().decode(it)).toComposeImageBitmap() }.getOrNull() }
}

/** A small uppercase heading over a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.type.label, color = T.c.text3, modifier = modifier)
}

/** A choice of a few: a pill that slides to the one picked. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = com.leftovers.desktop.ui.theme.T.c
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(c.raised).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) c.surface.copy(alpha = 1f) else Color.Transparent, Motion.quick())
            val fg by animateColorAsState(if (on) c.text else c.text2, Motion.quick())
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (on && c.glass) (if (c.dark) Color(0xFF2A2A33) else Color.White) else bg)
                    .pressable(scaleTo = 0.96f) { onSelect(value) }.padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = com.leftovers.desktop.ui.theme.T.type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = fg, maxLines = 1) }
        }
    }
}

/** A switch that slides, with a little give. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = T.c
    val x by animateFloatAsState(if (checked) 1f else 0f, Motion.moved())
    val track by animateColorAsState(if (checked) c.positive else c.line, Motion.quick())
    Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(track).pressable(scaleTo = 0.94f) { onChange(!checked) }.padding(3.dp)) {
        Box(Modifier.padding(start = (16 * x).dp).size(18.dp).clip(CircleShape).background(Color.White))
    }
}

/** A row in a settings list: what it is, a line about it, and its control. */
@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    HoverRow(onClick, Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = T.type.bodyStrong, color = T.c.text)
            if (subtitle != null) Text(subtitle, style = T.type.small, color = T.c.text2)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/**
 * A dialog over a dimmed window: it grows in from a little smaller and fades, and goes back the same way. Esc and
 * a click on the dim close it.
 */
@Composable
fun Dialog(visible: Boolean, onDismiss: () -> Unit, width: Dp = 460.dp, content: @Composable ColumnScope.() -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    val c = T.c
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(state, enter = fadeIn(Motion.settle()), exit = fadeOut(Motion.quick())) {
                Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
            }
            AnimatedVisibility(
                state,
                enter = fadeIn(Motion.settle()) + scaleIn(Motion.settle(), initialScale = 0.94f),
                exit = fadeOut(Motion.quick()) + scaleOut(Motion.quick(), targetScale = 0.96f),
            ) {
                val solid = if (c.dark) androidx.compose.ui.graphics.Color(0xFF18181E) else Color.White
                Column(
                    Modifier.widthIn(max = width).fillMaxWidth().padding(24.dp)
                        .clip(RoundedCornerShape(20.dp)).background(solid).border(1.dp, c.line, RoundedCornerShape(20.dp))
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .padding(24.dp),
                    content = content,
                )
            }
        }
    }
}

/** What just happened, with Undo, at the bottom of the window for a few seconds. */
data class Notice(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

@Composable
fun BoxScope.NoticeBar(notice: Notice?, onDone: () -> Unit) {
    var shown by remember { mutableStateOf<Notice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            shown = notice
            delay(if (notice.undo != null) 6000 else 3500)
            onDone()
        }
    }
    val c = T.c
    AnimatedVisibility(
        notice != null,
        Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        enter = fadeIn(Motion.settle()) + slideInVertically(Motion.offset) { it / 2 },
        exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.offset) { it / 2 },
    ) {
        val n = shown ?: return@AnimatedVisibility
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(if (c.dark) Color(0xFF26262E) else Color(0xFF18181D))
                .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(n.text, style = T.type.bodyStrong, color = Color.White, modifier = Modifier.padding(vertical = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.undo != null) {
                Text(
                    "Undo", style = T.type.bodyStrong, color = Color(0xFFCDF46F),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { n.undo.invoke(); onDone() }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else Spacer(Modifier.width(10.dp))
        }
    }
}

/** A thin bar of how much of something is used: green, then amber near the end, red over. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, color: Color? = null) {
    val c = T.c
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.settle())
    val tint = color ?: when {
        fraction > 1f -> c.negative
        fraction > 0.85f -> c.warning
        else -> c.positive
    }
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.raised)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(tint))
    }
}

/** A gentle hint where a list is empty: an icon, a line, and what to do. */
@Composable
fun Empty(icon: ImageVector, title: String, line: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = T.c
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.text2)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = T.type.heading, color = c.text)
        Text(line, style = T.type.body, color = c.text2)
        if (action != null) { Spacer(Modifier.height(8.dp)); action() }
    }
}
