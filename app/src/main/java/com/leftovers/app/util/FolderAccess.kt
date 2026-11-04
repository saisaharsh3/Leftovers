package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Keeping access to the folders picked for backups and sync. Android lets an app keep only so many, and a folder it
 * wouldn't keep still worked until the app was next closed, so backups failed later with "can no longer open that
 * folder" however often it was chosen again (#41).
 */
object FolderAccess {
    private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Keeps [tree] for good, first letting go of folders picked before and no longer used, so the limit isn't reached.
     * [inUse]: the other feature's folder, which stays. False when Android still won't keep it.
     */
    fun keep(context: Context, tree: Uri, inUse: Collection<String?>): Boolean {
        val resolver = context.contentResolver
        val wanted = (inUse.filterNotNull() + tree.toString()).toSet()
        resolver.persistedUriPermissions.filter { it.uri.toString() !in wanted }
            .forEach { runCatching { resolver.releasePersistableUriPermission(it.uri, FLAGS) } }
        return runCatching { resolver.takePersistableUriPermission(tree, FLAGS) }.isSuccess
    }

    /** Lets go of [dir], unless the other feature still uses the same folder ([stillUsed]). */
    fun release(context: Context, dir: String?, stillUsed: Collection<String?>) {
        if (dir == null || dir in stillUsed) return
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(dir), FLAGS) }
    }
}
