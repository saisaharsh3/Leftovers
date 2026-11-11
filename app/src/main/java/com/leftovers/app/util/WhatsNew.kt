package com.leftovers.app.util

import android.content.Context

/**
 * What changed, shown once on Home after the app updates. Release and Dev builds read the same notes:
 * keep them to the few changes worth telling, and rewrite them with each update.
 */
object WhatsNew {
    /** One change: an icon (a [com.leftovers.app.ui.icons.Lucide] name), a short title and a line about it. */
    data class Note(val icon: String, val title: String, val text: String)

    val items = listOf(
        Note("handcoins", "Paid back, not income", "When a friend pays you back, it's spotted, even with their full name in the SMS, and settled in Money owed with one tap."),
        Note("link", "Share what's owed", "Remind someone and they get a link: if they use Leftovers too, one tap adds it to theirs."),
        Note("zap", "Small payments, added for you", "Turn on adding automatically, and bank-SMS spending up to your amount goes straight in."),
        Note("landmark", "Your bank's balance", "When a bank SMS says the balance, each account shows it, and can match it."),
        Note("palette", "Make it yours", "Your own photo behind liquid glass, an accent colour, your own icons, and categories you can hide."),
    )

    /** The changes in a line each, for the AI assistant. */
    val notes: List<String> get() = items.map { "${it.title}: ${it.text}" }

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A fresh install has nothing new to tell: the app is new. */
    fun freshInstall(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime == it.lastUpdateTime } }.getOrDefault(false)
}
