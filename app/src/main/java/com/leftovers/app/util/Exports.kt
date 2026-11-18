package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files the app makes to hand over (the assistant's reports, sheets and charts): written into the app's own
 * cache, where nothing else can see them, and only leave it when the user taps Save or Share.
 */
object Exports {
    /** Kinds of file, with the type Android knows them by and how they're named to the user. */
    enum class Kind(val mime: String, val extension: String, val label: String) {
        PDF("application/pdf", "pdf", "PDF"),
        CSV("text/csv", "csv", "CSV"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", "Excel"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", "Word"),
        PNG("image/png", "png", "Image"),
        ;

        companion object {
            fun of(mime: String): Kind? = entries.firstOrNull { it.mime == mime }
        }
    }

    /** A name that's safe as a file name, keeping it readable: "Spending report, Oct 2026.xlsx". */
    fun fileName(title: String, kind: Kind): String {
        val base = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "Leftovers" }
        return "$base.${kind.extension}"
    }

    /** Writes [bytes] into the cache as [name] and gives the address other apps can be handed. */
    suspend fun write(context: Context, name: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** The share sheet for a file. */
    fun shareIntent(uri: Uri, kind: Kind, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = kind.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /** Copies a made file to where the user chose to save it. */
    suspend fun copy(context: Context, from: Uri, to: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(from) ?: throw IllegalStateException("The file is gone. Ask for it again.")
        input.use { i ->
            (context.contentResolver.openOutputStream(to, "wt") ?: throw IllegalStateException("Couldn't write there")).use { o -> i.copyTo(o) }
        }
    }

    /** How big a file is, for its card ("24 KB"). */
    fun size(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()?.takeIf { it >= 0 }?.let { b ->
        when {
            b < 1024 -> "$b bytes"
            b < 1024 * 1024 -> "${(b + 512) / 1024} KB"
            else -> String.format(java.util.Locale.getDefault(), "%.1f MB", b / (1024.0 * 1024.0))
        }
    }
}

/** The "Save as" screen for one file, asking where to keep it, starting with [Pair.first] as its name. */
class SaveAs : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}
