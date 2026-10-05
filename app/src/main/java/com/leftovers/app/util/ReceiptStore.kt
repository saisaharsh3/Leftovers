package com.leftovers.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Keeps receipt photos in the app's private storage, downscaled to a sensible size. */
object ReceiptStore {
    private const val MAX_SIDE = 1600

    private fun dir(context: Context) = File(context.filesDir, "receipts").apply { mkdirs() }

    /** A temporary file the camera can write into. */
    fun newCameraUri(context: Context): Pair<Uri, File> {
        val file = File(dir(context), "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to file
    }

    /** Copies (and shrinks) the picked image into private storage; returns its path. */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val bitmap = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = context.contentResolver.openInputStream(source)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scaled = scaleAndRotate(bitmap, rotation)
            val out = File(dir(context), "receipt-${UUID.randomUUID()}.jpg")
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out.absolutePath
        }.getOrNull()
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Float): Bitmap {
        val ratio = MAX_SIDE.toFloat() / maxOf(src.width, src.height)
        val matrix = Matrix().apply {
            if (ratio < 1f) postScale(ratio, ratio)
            if (rotation != 0f) postRotate(rotation)
        }
        return if (matrix.isIdentity) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    suspend fun loadPreview(path: String, maxSide: Int = 600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
