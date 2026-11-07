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
        Note("palette", "Make it yours", "A background of your choice, even your own photo behind liquid glass, and an accent colour."),
        Note("party", "Greetings and a weekly note", "Festival wishes where you live, and a short note on your week every Sunday."),
        Note("search", "Help & features", "Find any feature and where it is, in Settings, or just ask the AI assistant."),
        Note("receipt", "Better statement imports", "Payment app statements read right, and an import can be undone or removed."),
        Note("bot", "A smarter assistant", "PDF statements, CSV exports and goals from the chat, and switch AIs without a new key."),
    )

    /** The changes in a line each, for the AI assistant. */
    val notes: List<String> get() = items.map { "${it.title}: ${it.text}" }

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A fresh install has nothing new to tell: the app is new. */
    fun freshInstall(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime == it.lastUpdateTime } }.getOrDefault(false)
}
