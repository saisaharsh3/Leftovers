package com.leftovers.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leftovers.app.LeftoversApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Weekly backup into a folder the user picked once; keeps the newest [KEEP] files. */
object AutoBackup {
    private const val WORK_NAME = "auto_backup"
    private const val PREFIX = "leftovers-auto-"
    private const val KEEP = 4

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Writes a dated backup into [tree] and removes older automatic ones. */
    suspend fun runNow(context: Context, tree: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "$PREFIX${LocalDate.now()}.json"
            existing(context, tree).filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
            val file = DocumentsContract.createDocument(resolver, folder, "application/json", name)
                ?: error("Couldn't create a file in that folder")
            val count = (context.applicationContext as LeftoversApp).container.backup.export(file).getOrThrow()
            existing(context, tree)
                .sortedByDescending { it.second }
                .drop(KEEP)
                .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.first) } }
            count
        }
    }

    /** Automatic backups already in the folder, as (document uri, file name). */
    private fun existing(context: Context, tree: Uri): List<Pair<Uri, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val found = mutableListOf<Pair<Uri, String>>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(PREFIX)) found += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)) to name
            }
        }
        return found
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = (applicationContext as LeftoversApp).container.settings
        val dir = settings.settings.first().autoBackupDir ?: return Result.success()
        return AutoBackup.runNow(applicationContext, Uri.parse(dir)).fold(
            {
                settings.setAutoBackupDone(System.currentTimeMillis())
                Result.success()
            },
            { Result.retry() },
        )
    }
}
