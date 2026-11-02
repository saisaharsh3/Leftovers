package com.leftovers.app.util

import android.content.Context

/**
 * What changed, shown once on Home after the app updates. Release and Dev builds read the same notes:
 * keep them to the few changes worth telling, and rewrite them with each update.
 */
object WhatsNew {
    val notes = listOf(
        "Split bills: one entry, with where the money went and each person's share in Money owed",
        "Money owed: save it before you know the amount, and pay back one entry on its own",
        "50/30/20 budgeting, and a budget for dates you choose, like a trip",
        "The budget month can start on payday, and income can be interest",
        "Home screen widgets in Glass, Dark or Light, at any size",
        "The bell on Home keeps what's new, and tells you about new versions",
    )

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A fresh install has nothing new to tell: the app is new. */
    fun freshInstall(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime == it.lastUpdateTime } }.getOrDefault(false)
}
