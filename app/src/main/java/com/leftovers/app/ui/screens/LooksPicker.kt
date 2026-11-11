package com.leftovers.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.AppSettings
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.Backdrop
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.ui.theme.accentChoices

/**
 * The app's look, in Appearance: what's behind every screen (a glow in a few colours, plain, or a photo of the
 * user's own) and the accent colour. Each shows straight away, as it will look.
 */
@Composable
fun LooksPicker(
    s: AppSettings,
    onBackdrop: (Backdrop) -> Unit,
    onPhoto: (android.net.Uri) -> Unit,
    onDim: (Float) -> Unit,
    onBlur: (Float) -> Unit,
    onText: (String) -> Unit,
    onAccent: (Long) -> Unit,
) {
    val c = LocalAppColors.current
    val current = Backdrop.of(s.backdrop)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPhoto) }
    // The photo chosen before stays, so going to Aurora and back keeps it; shown on its swatch.
    val context = androidx.compose.ui.platform.LocalContext.current
    val saved = remember(s.photoVersion) { com.leftovers.app.ui.theme.BackgroundPhoto.thumbnail(context) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Background", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Backdrop.entries.forEach { b ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val selected = b == current
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 72.dp)
                            .pressable({
                                // Your photo: back to the one chosen before; tapped again (or with none yet), pick a picture.
                                if (b == Backdrop.PHOTO && (saved == null || current == Backdrop.PHOTO)) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else onBackdrop(b)
                            }, pressedScale = 0.94f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when (b) {
                                    Backdrop.PLAIN -> Brush.verticalGradient(listOf(c.background, c.background))
                                    Backdrop.PHOTO -> Brush.verticalGradient(listOf(c.glassStrong, c.glass))
                                    else -> Brush.linearGradient(listOf(b.a, b.b, b.c))
                                },
                            )
                            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.borderTop, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (b == Backdrop.PHOTO) {
                            if (saved != null) androidx.compose.foundation.Image(saved, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Lucide.Camera, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(if (b == Backdrop.PHOTO && saved != null && b == current) "Change" else b.label, style = MaterialTheme.typography.labelSmall, color = if (selected) c.textPrimary else c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (current == Backdrop.PHOTO) {
            // The text colour sets the look over a photo (white: dark glass; black: light glass), in place of
            // the light/dark theme above.
            Text("Text on the photo", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            SegmentedToggle(listOf("AUTO", "WHITE", "BLACK"), s.photoText, { when (it) { "WHITE" -> "White"; "BLACK" -> "Black"; else -> "Auto" } }, onText, Modifier.fillMaxWidth())
            // A text colour that fights the photo (black on a dark one) is allowed, but said so.
            val context = androidx.compose.ui.platform.LocalContext.current
            val bright = remember(s.photoVersion) { com.leftovers.app.ui.theme.BackgroundPhoto.isBright(context) }
            val clash = (s.photoText == "BLACK" && bright == false) || (s.photoText == "WHITE" && bright == true)
            Text(
                when {
                    clash -> if (bright == false) "This photo is dark: white text, or Auto, reads better on it." else "This photo is light: black text, or Auto, reads better on it."
                    s.photoText == "AUTO" -> "Picked by how bright your photo is: black on light photos, white on dark ones."
                    else -> "Only the text changes; cards stay clear glass."
                },
                style = MaterialTheme.typography.bodySmall, color = if (clash) c.warning else c.textTertiary, modifier = Modifier.padding(start = 4.dp),
            )
            // Saved when the finger lifts, so the photo isn't blurred again on every step of the drag.
            var blur by remember(s.photoBlur) { mutableFloatStateOf(s.photoBlur) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Blur", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
                Text(" · " + if (blur < 0.05f) "Sharp" else if (blur < 0.5f) "Soft" else "Very soft", style = MaterialTheme.typography.labelMedium, color = c.textTertiary, modifier = Modifier.weight(1f))
                // Back to how the photo looked when it was chosen.
                if (kotlin.math.abs(s.photoBlur - DEFAULT_BLUR) > 0.01f || kotlin.math.abs(s.photoDim - DEFAULT_DIM) > 0.01f) {
                    Row(
                        Modifier.pressable({ blur = DEFAULT_BLUR; onBlur(DEFAULT_BLUR); onDim(DEFAULT_DIM) }, pressedScale = 0.95f).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.RotateCcw, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                        Text(" Reset", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    }
                }
            }
            Slider(
                value = blur,
                onValueChange = { blur = it },
                onValueChangeFinished = { onBlur(blur) },
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.glassStrong),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Photo dimming", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            Slider(
                value = s.photoDim.coerceIn(0f, 0.7f),
                onValueChange = onDim,
                // From none: text keeps a soft halo over the photo, so dimming is a matter of taste.
                valueRange = 0f..0.7f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.glassStrong),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Accent colour", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Default: lime in the dark theme, ink in the light one, or the wallpaper's when that's on.
            Swatch(Brush.sweepGradient(accentChoices.map { it.second } + accentChoices.first().second), s.accentColor == 0L, "Default") { onAccent(0L) }
            accentChoices.forEach { (name, color) ->
                val argb = color.toArgb().toLong() and 0xFFFFFFFFL
                Swatch(Brush.verticalGradient(listOf(color, color)), s.accentColor == argb, name) { onAccent(argb) }
            }
        }
    }
}

@Composable
private fun Swatch(fill: Brush, selected: Boolean, name: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .size(38.dp)
            .pressable(onClick, pressedScale = 0.9f)
            .border(2.dp, if (selected) c.textPrimary else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
            .semantics { contentDescription = name },
    )
}

private const val DEFAULT_BLUR = 0.25f
private const val DEFAULT_DIM = 0.1f
