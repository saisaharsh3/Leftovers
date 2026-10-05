package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.leftovers.app.data.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

object CsvExporter {
    suspend fun writeCsv(context: Context, items: List<TransactionItem>): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "leftovers_${LocalDate.now()}.csv")
        file.bufferedWriter().use { out ->
            out.appendLine("Date,Type,Category,Amount,Note")
            items.forEach { item ->
                out.appendLine(
                    listOf(
                        item.date.toString(),
                        item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        item.categoryName,
                        BigDecimal.valueOf(item.amountMinor, 2).toPlainString(),
                        item.note,
                    ).joinToString(",") { escape(it) },
                )
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Leftovers export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Export transactions")
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
