package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import com.leftovers.app.data.PersonBalance

/** A friendly reminder to someone who owes you, shared through WhatsApp, SMS or any app the person picks. */
object DebtReminder {
    /** "Hi Arjun, a quick reminder about ₹2,500 for Goa stay share. Thanks!", saying what it was for when the entries do. */
    fun message(balance: PersonBalance, format: (Long) -> String): String {
        val notes = balance.entries
            .filter { !it.repayment && it.amountMinor > 0 }
            .map { it.note.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        val what = when (notes.size) {
            0 -> ""
            1 -> " for ${notes[0]}"
            2 -> " for ${notes[0]} and ${notes[1]}"
            else -> " for ${notes[0]}, ${notes[1]} and ${notes.size - 2} more"
        }
        val name = balance.person.trim().substringBefore(' ')
        return "Hi $name, a quick reminder about ${format(balance.netMinor)}$what. Thanks!"
    }

    /**
     * The reminder with a link under it: a friend who also uses Leftovers taps it and the entries are added to
     * their Money owed, the other way round. The entries travel in the link itself, not through a server.
     */
    suspend fun withLink(context: Context, balance: PersonBalance, currency: String, format: (Long) -> String): String {
        val device = (context.applicationContext as com.leftovers.app.LeftoversApp).container.settings.syncDevice()
        val link = SharedOwed.link(device, currency, balance.entries) ?: return message(balance, format)
        return message(balance, format) + "\n\nUse Leftovers too? Tap to add this to yours:\n" + link
    }

    /** Opens the share sheet with [text]; nothing is sent until the user picks an app and sends it there. */
    fun share(context: Context, text: String, person: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Remind $person").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
