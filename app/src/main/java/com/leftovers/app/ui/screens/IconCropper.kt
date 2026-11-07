package com.leftovers.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.theme.LocalAppColors
import kotlin.math.max
import kotlin.math.min

/**
 * Crops a picture into a square icon: the picture fills the frame, and a drag moves it, a pinch zooms it. What's in
 * the frame becomes the icon. [onCrop] gets the square, in the picture's own pixels.
 */
@Composable
fun IconCropper(bitmap: Bitmap, onCancel: () -> Unit, onCrop: (left: Int, top: Int, side: Int) -> Unit) {
    val c = LocalAppColors.current
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val frameDp = 260.dp
    val frame = with(LocalDensity.current) { frameDp.toPx() }
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    // Filling the frame at zoom 1; the picture's centre sits [offset] from the frame's.
    val base = frame / min(w, h)
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun clamp(o: Offset, z: Float): Offset {
        val s = base * z
        val maxX = max(0f, (w * s - frame) / 2)
        val maxY = max(0f, (h * s - frame) / 2)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Drag and pinch to frame your icon", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        val shape = RoundedCornerShape(frameDp * 0.34f)
        Box(
            Modifier.size(frameDp).clip(shape).border(2.dp, c.textPrimary.copy(alpha = 0.6f), shape)
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                        offset = clamp(offset + pan, zoom)
                    }
                },
        ) {
            Canvas(Modifier.size(frameDp)) {
                val s = base * zoom
                drawImage(
                    image,
                    dstOffset = IntOffset((frame / 2 + offset.x - w * s / 2).toInt(), (frame / 2 + offset.y - h * s / 2).toInt()),
                    dstSize = IntSize((w * s).toInt(), (h * s).toInt()),
                    filterQuality = FilterQuality.High,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
            PrimaryButton("Use", {
                // The frame's corner in the picture's pixels, and how many of them the frame spans.
                val s = base * zoom
                val left = ((w * s / 2 - frame / 2 - offset.x) / s).toInt()
                val top = ((h * s / 2 - frame / 2 - offset.y) / s).toInt()
                onCrop(left, top, (frame / s).toInt())
            }, Modifier.weight(1f))
        }
    }
}

/**
 * The first tile in every icon picker: an icon from a picture of your own (a shop's logo from a screenshot, say).
 * Shows the one in use, or a camera. Tapped, a picture is picked and framed, and [onIcon] gets the new icon's key.
 */
@Composable
fun OwnIconTile(icon: String, onIcon: (String) -> Unit) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { cropping = com.leftovers.app.util.CustomIcons.open(context, it) }
    }
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier
            .size(46.dp)
            .clip(shape)
            .border(1.dp, c.borderBottom, shape)
            .then(
                Modifier.pressable({
                        pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, pressedScale = 0.9f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(com.leftovers.app.ui.icons.Lucide.Camera, contentDescription = "Icon from a photo", tint = c.textSecondary, modifier = Modifier.size(22.dp))
    }
    cropping?.let { bitmap ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { cropping = null }) {
            // A pop-up is a window of its own, so glass can't line up with the screen behind: a solid panel instead.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.background).border(1.dp, c.borderTop, RoundedCornerShape(30.dp))) {
                Column(Modifier.padding(20.dp)) {
                    IconCropper(bitmap, onCancel = { cropping = null }) { left, top, side ->
                        com.leftovers.app.util.CustomIcons.save(context, bitmap, left, top, side)?.let(onIcon)
                        cropping = null
                    }
                }
            }
        }
    }
}

/**
 * The photo tile followed by every icon the user has made before, kept on the phone to use again for any category,
 * account or goal. A long press offers to delete one.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OwnIcons(icon: String, color: Long, onIcon: (String) -> Unit) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf(com.leftovers.app.util.CustomIcons.all(context)) }
    var deleting by remember { mutableStateOf<String?>(null) }
    OwnIconTile(if (com.leftovers.app.util.CustomIcons.isCustom(icon) && icon !in saved) icon else "") { key ->
        saved = com.leftovers.app.util.CustomIcons.all(context)
        onIcon(key)
    }
    saved.forEach { key ->
        val selected = key == icon
        val shape = RoundedCornerShape(15.dp)
        Box(
            Modifier.size(46.dp).clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) androidx.compose.ui.graphics.Color(color) else c.borderBottom, shape)
                .combinedClickable(onClick = { onIcon(key) }, onLongClick = { deleting = key }),
        ) { com.leftovers.app.ui.components.CategoryIcon(key, color, size = 46.dp) }
    }
    deleting?.let { key ->
        GlassAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this icon?") },
            text = { Text("Anything still using it shows a plain icon instead.", color = c.textSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.leftovers.app.util.CustomIcons.delete(context, key)
                    saved = com.leftovers.app.util.CustomIcons.all(context)
                    if (key == icon) onIcon("tag")
                    deleting = null
                }) { Text("Delete", color = c.negative) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

/** A plain icon (no tile), as inside a goal's ring: a picture of the user's own round, or a line icon. */
@Composable
fun BareIcon(key: String, color: Long, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val custom = if (com.leftovers.app.util.CustomIcons.isCustom(key)) remember(key) { com.leftovers.app.util.CustomIcons.load(context, key) } else null
    if (custom != null) {
        androidx.compose.foundation.Image(custom, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(size * 1.4f).clip(androidx.compose.foundation.shape.CircleShape))
    } else {
        androidx.compose.material3.Icon(com.leftovers.app.ui.icons.CategoryIcons[key], contentDescription = null, tint = androidx.compose.ui.graphics.Color(color), modifier = Modifier.size(size))
    }
}
