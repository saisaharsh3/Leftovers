package com.leftovers.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID

/**
 * Icons people make from their own pictures (a shop's logo from a screenshot, say), cropped square and kept in the
 * app's private files. A category stores one as "img:<file>" where it would store an icon's name. Nothing is
 * bundled or fetched: the picture is theirs.
 */
object CustomIcons {
    const val PREFIX = "img:"
    private const val SIZE = 192

    fun isCustom(key: String) = key.startsWith(PREFIX)

    private fun dir(context: Context) = File(context.filesDir, "icons").apply { mkdirs() }

    private val cache = android.util.LruCache<String, ImageBitmap>(48)

    /** The picture at [uri], no wider than [max] px, the right way up. */
    fun open(context: Context, uri: Uri, max: Int = 1600): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, max.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    }.getOrNull()

    /** Keeps the square [left]/[top]/[side] (in [bitmap]'s pixels) as an icon; returns its key. */
    fun save(context: Context, bitmap: Bitmap, left: Int, top: Int, side: Int): String? = runCatching {
        val s = side.coerceIn(1, minOf(bitmap.width, bitmap.height))
        val x = left.coerceIn(0, bitmap.width - s)
        val y = top.coerceIn(0, bitmap.height - s)
        val square = Bitmap.createScaledBitmap(Bitmap.createBitmap(bitmap, x, y, s, s), SIZE, SIZE, true)
        val name = UUID.randomUUID().toString().take(12) + ".png"
        File(dir(context), name).outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
        PREFIX + name
    }.getOrNull()

    /** The pictures behind [keys], by file name, for a backup: small, so they always go in. */
    fun export(context: Context, keys: Collection<String>): org.json.JSONObject = org.json.JSONObject().also { o ->
        keys.filter(::isCustom).distinct().forEach { key ->
            val f = File(dir(context), key.removePrefix(PREFIX).substringAfterLast('/'))
            if (f.exists()) o.put(f.name, java.util.Base64.getEncoder().encodeToString(f.readBytes()))
        }
    }

    /** Puts a backup's icon pictures back, under the same names its categories use. */
    fun restore(context: Context, icons: org.json.JSONObject?) {
        icons ?: return
        icons.keys().forEach { name ->
            // Only a plain file name, never a path out of the icons folder.
            val safe = name.substringAfterLast('/').substringAfterLast('\\')
            if (safe.isBlank() || safe.startsWith(".")) return@forEach
            runCatching { File(dir(context), safe).writeBytes(java.util.Base64.getDecoder().decode(icons.getString(name))) }
        }
        cache.evictAll()
    }

    /** Every icon the user has made, newest first: they can be used again for any category, account or goal. */
    fun all(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".png") }.orEmpty().sortedByDescending { it.lastModified() }.map { PREFIX + it.name }

    /** Removes a saved icon. Whatever still uses it shows a plain icon instead. */
    fun delete(context: Context, key: String) {
        if (!isCustom(key)) return
        File(dir(context), key.removePrefix(PREFIX).substringAfterLast('/')).delete()
        cache.remove(key)
    }

    /** The icon for [key], or null when it isn't a custom one or its file is gone (another phone, say). */
    fun load(context: Context, key: String): ImageBitmap? {
        if (!isCustom(key)) return null
        cache.get(key)?.let { return it }
        val f = File(dir(context), key.removePrefix(PREFIX).substringAfterLast('/'))
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { cache.put(key, it) }
    }
}
