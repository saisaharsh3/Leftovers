package com.leftovers.app.util

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leftovers.app.LeftoversApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Sync between your own phones, with no account and no server of ours: each phone keeps a full backup of its
 * data as leftovers-sync-<phone>.json, encrypted when a backup password is set, either in a folder the user
 * picked (one that Syncthing, Nextcloud's app or similar keeps the same on every phone) or on their own
 * WebDAV server (see [SyncPlace]). A sync first merges in
 * the other phones' files (adding what's new, and removing what they deleted or changed), then writes this
 * phone's own file. Money owed, goals and subscriptions are only added, never removed.
 */
object DeviceSync {
    private const val WORK_NAME = "device_sync"
    const val PREFIX = "leftovers-sync-"
    private val running = Mutex()

    /** What a sync did. [unreadable]: other phones' files that couldn't be opened, as with a different backup password. */
    data class Outcome(val added: Int, val removed: Int, val phones: Int, val unreadable: Int)

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DeviceSyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Syncs with the folder or server in Settings, if sync is on; null when it's off. */
    suspend fun runIfOn(context: Context): Result<Outcome>? {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val place = when {
            s.syncServer != null -> WebDavPlace(s.syncServer, s.syncUser.orEmpty(), container.syncLogin.get().orEmpty())
            s.syncDir != null -> FolderPlace(context, Uri.parse(s.syncDir))
            else -> return null
        }
        return runNow(context, place)
    }

    suspend fun runNow(context: Context, place: SyncPlace): Result<Outcome> = running.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val container = (context.applicationContext as LeftoversApp).container
                val own = "$PREFIX${container.settings.syncDevice()}.json"
                // Syncthing's temporary files and anything else in the folder are left alone.
                val others = place.list().filter { it.startsWith(PREFIX) && it.endsWith(".json") && it != own }
                var added = 0
                var removed = 0
                var phones = 0
                var unreadable = 0
                others.forEach { name ->
                    runCatching { place.read(name) }
                        .fold({ container.backup.importBytes(it, sync = true, mine = container.syncRemoved.all()) }, { Result.failure(it) })
                        .fold(
                            {
                                added += it.entries
                                removed += it.removed
                                phones++
                            },
                            { unreadable++ },
                        )
                }
                place.write(own, container.backup.exportBytes(container.syncRemoved.all()).getOrThrow().first)
                container.settings.setSyncDone(System.currentTimeMillis())
                Outcome(added, removed, phones, unreadable)
            }
        }
    }

    /** "Added 3 entries from your other phone", or what went wrong, for Sync now. */
    fun describe(outcome: Outcome): String {
        val parts = buildList {
            if (outcome.phones == 0) {
                if (outcome.unreadable == 0) add("No other phones in this folder yet")
            } else {
                val from = if (outcome.phones == 1) "your other phone" else "${outcome.phones} other phones"
                add(
                    if (outcome.added == 0 && outcome.removed == 0) "Up to date with $from"
                    else listOfNotNull(
                        outcome.added.takeIf { it > 0 }?.let { "added $it ${if (it == 1) "entry" else "entries"}" },
                        outcome.removed.takeIf { it > 0 }?.let { "removed $it" },
                    ).joinToString(" and ").replaceFirstChar { it.uppercase() } + " from $from",
                )
            }
            if (outcome.unreadable > 0) add("${outcome.unreadable} ${if (outcome.unreadable == 1) "phone's file" else "phones' files"} couldn't be opened. Use the same backup password on every phone")
        }
        return parts.joinToString(". ")
    }
}

class DeviceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when (DeviceSync.runIfOn(applicationContext)?.isSuccess) {
            false -> Result.retry()
            else -> Result.success()
        }
}
