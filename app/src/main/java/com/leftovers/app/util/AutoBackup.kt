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
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Daily, weekly or monthly backup into a folder the user picked once; keeps the newest few files. */
object AutoBackup {
    private const val WORK_NAME = "auto_backup"
    private const val PREFIX = "leftovers-auto-"
    /** [days] between backups (1, 7 or 30); a change of interval replaces the schedule. */
    fun schedule(context: Context, enabled: Boolean, days: Int = 7) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days.coerceIn(1, 30).toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Makes the automatic backup if one is due. Runs from the weekly (or daily, monthly) job and also each time
     * the app opens: some phones (HyperOS, MIUI and others) never run an app's background work unless it's
     * allowed to autostart, so backups would otherwise stop without a word (#35). A failure is kept to show in
     * Settings until a backup works again.
     */
    suspend fun runIfDue(context: Context): kotlin.Result<Int>? = running.withLock {
        val settings = (context.applicationContext as LeftoversApp).container.settings
        val s = settings.settings.first()
        val dir = s.autoBackupDir ?: return null
        // A little under the interval, so a run that starts slightly early still counts.
        val gap = TimeUnit.DAYS.toMillis(s.autoBackupDays.toLong()) - TimeUnit.HOURS.toMillis(4)
        if (System.currentTimeMillis() - s.autoBackupLast < gap) return@withLock null
        backUpLocked(context, Uri.parse(dir), s.autoBackupKeep)
    }

    /** Makes a backup now and records how it went. */
    suspend fun backUp(context: Context, tree: Uri, keep: Int): kotlin.Result<Int> = running.withLock { backUpLocked(context, tree, keep) }

    // One at a time: turning backups on and the job's first run can start together, and wrote two files.
    private val running = kotlinx.coroutines.sync.Mutex()

    private suspend fun backUpLocked(context: Context, tree: Uri, keep: Int): kotlin.Result<Int> {
        val settings = (context.applicationContext as LeftoversApp).container.settings
        return runNow(context, tree, keep)
            .onSuccess { settings.setAutoBackupDone(System.currentTimeMillis()) }
            .onFailure { settings.setAutoBackupError(problem(it)) }
    }

    /** What went wrong, in a few words. */
    fun problem(error: Throwable): String = when (error) {
        is SecurityException -> "The app can no longer open that folder. Choose it again"
        is java.io.FileNotFoundException -> "That folder isn't there any more. Choose it again"
        is OutOfMemoryError -> "The backup was too big. Try turning off Include photos"
        else -> error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    /** Writes a dated backup into [tree] and removes older automatic ones. */
    suspend fun runNow(context: Context, tree: Uri, keep: Int = 4): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "$PREFIX${LocalDate.now()}.json"
            // Today's backup replaces any made earlier today, a copy a cloud folder renamed ("… (1).json") too.
            existing(context, tree).filter { it.second.startsWith(name.removeSuffix(".json")) }
                .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.first) } }
            val file = DocumentsContract.createDocument(resolver, folder, "application/json", name)
                ?: error("Couldn't create a file in that folder")
            val count = (context.applicationContext as LeftoversApp).container.backup.export(file).getOrThrow()
            // Newest first by the date in the name; a copy a cloud folder renamed ("… (1).json") counts too.
            existing(context, tree)
                .sortedByDescending { it.second.removePrefix(PREFIX).take(10) }
                .drop(keep.coerceIn(1, 5))
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
    override suspend fun doWork(): Result =
        // Turning backups on makes one straight away, and the job's first run starts at the same moment;
        // runIfDue skips it then, so the two don't write duplicate files.
        if (AutoBackup.runIfDue(applicationContext)?.isFailure == true) Result.retry() else Result.success()
}
