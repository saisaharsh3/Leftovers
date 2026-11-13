package com.leftovers.app.util

import android.content.Context

/**
 * What changed, shown once on Home after the app updates. Release and Dev builds read the same notes:
 * keep them to the few changes worth telling, and rewrite them with each update.
 */
object WhatsNew {
    /** One change: an icon (a [com.leftovers.app.ui.icons.Lucide] name), a short title and a line about it. */
    data class Note(val icon: String, val title: String, val text: String)

    /** The big changes, each a section of Highlights: its kicker, title and line. */
    data class Headline(val kicker: String, val title: String, val text: String)

    val headlines = listOf(
        Headline("Money owed, settled by itself", "A friend paying you back isn't income.", "The bank text is recognised as theirs, however the bank writes the name, and one tap settles it in Money owed instead of counting it as money in."),
        Headline("Getting paid", "Pay me back, in one tap.", "Add your UPI ID, or for friends where UPI isn't used, a payment link or bank details. Reminders carry them, and Pay with UPI opens their UPI app with the amount filled in. Never a PIN."),
        Headline("Group splits", "One message for the whole group.", "Share a split bill with the group chat: everyone's share in one message, and a link where each taps their own name to pay you or add it to their Leftovers."),
        Headline("Subscriptions", "Logged when you really pay.", "A subscription can wait for its bank text: it's logged that day, for that amount. A salary a few days late, or the gym paid at the end of the month, moves the entry instead of adding a second. No message? It waits under Due."),
        Headline("Balances from SMS", "The balance your bank sees.", "When a bank text gives your balance, the account shows it next to its own, with Match when they differ. Small payments up to an amount you choose can be added for you."),
        Headline("Budgets", "A fresh start after a big day.", "Over today? Show the new daily limit for the days left. The budget's own spending sits on its screen, and budgets of any length are set with a tap."),
        Headline("The assistant", "Chats that remember.", "Chats are kept on the phone to come back to, switching AI keeps the whole conversation, and you can attach a statement, a CSV or a PDF to ask about. The phone's own AI answers better too."),
        Headline("Settings", "Arranged like your phone's.", "Search any setting by name or a word for it, or open a category to see just its own."),
        Headline("Looks", "Any picture, under liquid glass.", "Your own photo or a wallpaper behind the app, liquid or frosted glass, an accent colour, your own icons, and categories you can hide."),
    )

    /** The smaller changes, listed after the big ones. */
    val items = listOf(
        Note("receipt", "Details on an entry", "The note is its name; details hold the rest, shown when it's opened. Imports read a statement's purpose line into them."),
        Note("link", "Shorter links", "Links for money owed are about half as long, and say what each share is part of."),
        Note("bot", "The assistant, smoother", "Messages glide in, Try again after a failure, and hold a reply to copy it."),
        Note("search", "Help & features first", "At the top of Settings, where it's easy to find."),
        Note("palette", "Fits larger text", "Buttons stay on one line with bigger text and display sizes."),
    )

    /** The changes in a line each, for the AI assistant. */
    val notes: List<String> get() = headlines.map { "${it.kicker}: ${it.title} ${it.text}" } + items.map { "${it.title}: ${it.text}" }

    /**
     * The feature version Highlights is about: "1.10" for 1.10.0, its pre-releases and every Dev build of it. Highlights
     * opens by itself once for each, the first time the app is opened after updating to it.
     */
    fun release(version: String): String = version.substringBefore("-").split('.').take(2).joinToString(".")

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A fresh install has nothing new to tell: the app is new. */
    fun freshInstall(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime == it.lastUpdateTime } }.getOrDefault(false)
}
