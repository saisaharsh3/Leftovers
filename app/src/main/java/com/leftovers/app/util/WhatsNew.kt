package com.leftovers.app.util

import android.content.Context

/**
 * What changed, shown once on Home after the app updates. Release and Dev builds read the same notes:
 * keep them to the few changes worth telling, and rewrite them with each update.
 */
object WhatsNew {
    val notes = listOf(
        "A split bill shows as one entry, with its whole amount and each person's share",
        "Tap a person in a split to change just their share",
        "Add again logs the same entry for today, from its card",
        "Activity sorts by highest or lowest amount",
        "Sheets close with a drag down anywhere on them",
    )

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A fresh install has nothing new to tell: the app is new. */
    fun freshInstall(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime == it.lastUpdateTime } }.getOrDefault(false)
}
