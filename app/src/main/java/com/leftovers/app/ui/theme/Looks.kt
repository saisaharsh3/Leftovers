package com.leftovers.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.io.File

/** What's behind every screen: the glow in a choice of colours, nothing, or the user's own photo. */
enum class Backdrop(val label: String, val a: Color, val b: Color, val c: Color) {
    AURORA("Aurora", Color(0xFF5048E5), Color(0xFF0E9F9A), Color(0xFF8BB82B)),
    OCEAN("Ocean", Color(0xFF1E6FD9), Color(0xFF13B5C8), Color(0xFF2E4BD8)),
    SUNSET("Sunset", Color(0xFFE5486B), Color(0xFFF08A3E), Color(0xFF8F3FD1)),
    FOREST("Forest", Color(0xFF1F8A5B), Color(0xFF6BAA2E), Color(0xFF0E7C86)),
    ROSE("Rose", Color(0xFFC2408F), Color(0xFF7B4BE0), Color(0xFFE06D9A)),
    PLAIN("Plain", Color.Transparent, Color.Transparent, Color.Transparent),
    PHOTO("Your photo", Color.Transparent, Color.Transparent, Color.Transparent);

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: AURORA
    }
}

/** Accent colours to choose from; Default is lime in the dark theme and ink in the light one. */
val accentChoices: List<Pair<String, Color>> = listOf(
    "Lime" to Color(0xFFCDF46F),
    "Mint" to Color(0xFF7FD9A8),
    "Sky" to Color(0xFF7CC4FF),
    "Violet" to Color(0xFFB4A7FF),
    "Pink" to Color(0xFFFF9CC8),
    "Peach" to Color(0xFFFFB37A),
    "Amber" to Color(0xFFF2C46D),
    "Coral" to Color(0xFFF58A8A),
)

/** [accent] made to work in the theme: darker on a light background so text in it stays readable. */
internal fun themedAccent(accent: Color, dark: Boolean): Pair<Color, Color> {
    val shown = if (dark) accent else lerp(accent, Color.Black, 0.45f)
    val on = if (shown.luminance() > 0.45f) Color(0xFF111114) else Color.White
    return shown to on
}

/** The user's background photo, kept in the app's private files; nothing else sees it. */
object BackgroundPhoto {
    private fun file(context: Context) = File(context.filesDir, "background.jpg")

    /** Copies the chosen picture in, scaled down: it's shown blurred, so a small one is enough. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        file(context).delete()
    }

    /**
     * The photo for drawing, as soft as [softness] says (0 sharp, 1 very soft). Dimming keeps text readable either
     * way; a little softness keeps busy photos from fighting with it.
     */
    /** The background as drawn, and a far softer copy that glass in front of it shows through. */
    /** [bright]: a light photo, which reads best with black text. */
    class Loaded(val photo: ImageBitmap, val frost: ImageBitmap, val bright: Boolean)

    fun loadWithFrost(context: Context, softness: Float): Loaded? = runCatching {
        val photo = load(context, softness) ?: return null
        val f = file(context)
        val full = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 }) ?: return null
        val w = 120
        val small = Bitmap.createScaledBitmap(full, w, (full.height * w / full.width.toFloat()).toInt().coerceAtLeast(1), true)
        // How bright the photo is on average (its perceived lightness), to choose black or white text.
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val light = px.sumOf { c -> 0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255) } / (px.size * 255.0)
        Loaded(photo, blur(small, radius = 9).asImageBitmap(), bright = light > 0.55)
    }.getOrNull()

    /** A small copy of the saved photo for its swatch, or null when none was chosen. */
    fun thumbnail(context: Context): ImageBitmap? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
    }.getOrNull()

    /** Whether the photo is light (black text reads best), dark, or null when there's none. */
    fun isBright(context: Context): Boolean? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 16 }) ?: return null
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        px.sumOf { c -> 0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255) } / (px.size * 255.0) > 0.55
    }.getOrNull()

    fun load(context: Context, softness: Float): ImageBitmap? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val full = BitmapFactory.decodeFile(f.path) ?: return null
        // Sharp keeps the photo's detail; softer blurs a smaller copy (three box passes look like a Gaussian).
        val w = 540
        val copy = Bitmap.createScaledBitmap(full, w, (full.height * w / full.width.toFloat()).toInt().coerceAtLeast(1), true)
        val radius = Math.round(softness.coerceIn(0f, 1f) * 18)
        (if (radius == 0) copy else blur(copy, radius)).asImageBitmap()
    }.getOrNull()

    /** Box blur, three passes each way, over [src]'s pixels. */
    internal fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** One running-sum box pass along rows or columns; edges repeat the outermost pixel. */
    private fun pass(from: IntArray, to: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val span = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int) = from[if (horizontal) line * w + i.coerceIn(0, len - 1) else i.coerceIn(0, len - 1) * w + line]
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) { val c = at(i); rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255 }
            for (i in 0 until len) {
                to[if (horizontal) line * w + i else i * w + line] = (0xFF shl 24) or ((rs / span) shl 16) or ((gs / span) shl 8) or (bs / span)
                val add = at(i + r + 1)
                val drop = at(i - r)
                rs += ((add shr 16) and 255) - ((drop shr 16) and 255)
                gs += ((add shr 8) and 255) - ((drop shr 8) and 255)
                bs += (add and 255) - (drop and 255)
            }
        }
    }
}
