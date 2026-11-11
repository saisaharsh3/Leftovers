package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import com.leftovers.app.data.PersonBalance
import kotlinx.coroutines.flow.first

/** A friendly reminder to someone who owes you, shared through WhatsApp, SMS or any app the person picks. */
object DebtReminder {
    /** A bill split with a group: what it came to in all, and how many shared it, the sender included. */
    data class Split(val totalMinor: Long, val people: Int)

    /**
     * "Hi Arjun, a quick reminder about ₹2,500 for Goa stay share. Thanks!", saying what it was for when the entries
     * do. A share of a group bill ([splits], by the expense it was split from) says so: "your share of Dinner is
     * ₹700 (a ₹2,800 bill split 4 ways)". Only the total and the head count, never who else was in it.
     */
    fun message(balance: PersonBalance, format: (Long) -> String, splits: Map<Long, Split> = emptyMap()): String {
        val name = balance.person.trim().substringBefore(' ')
        val owed = balance.entries.filter { !it.repayment && it.amountMinor > 0 }
        val single = owed.singleOrNull()
        val split = single?.splitOf?.let { splits[it] }?.takeIf { it.people > 1 }
        // One share of a group bill, and nothing paid back yet: say it was split, and how.
        if (split != null && single.amountMinor == balance.netMinor) {
            val bill = "a ${format(split.totalMinor)} bill split ${split.people} ways"
            val note = single.note.trim()
            return if (note.isEmpty()) "Hi $name, a quick reminder: your share of $bill is ${format(balance.netMinor)}. Thanks!"
            else "Hi $name, a quick reminder: your share of $note is ${format(balance.netMinor)} ($bill). Thanks!"
        }
        val notes = owed
            .map { d -> d.note.trim().let { n -> if (n.isNotEmpty() && d.splitOf?.let { splits[it] }?.let { it.people > 1 } == true) "$n (split ${splits[d.splitOf]!!.people} ways)" else n } }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        val what = when (notes.size) {
            0 -> ""
            1 -> " for ${notes[0]}"
            2 -> " for ${notes[0]} and ${notes[1]}"
            else -> " for ${notes[0]}, ${notes[1]} and ${notes.size - 2} more"
        }
        return "Hi $name, a quick reminder about ${format(balance.netMinor)}$what. Thanks!"
    }

    /**
     * The reminder with a link under it: a friend who also uses Leftovers taps it and the entries are added to
     * their Money owed, the other way round. The entries travel in the link itself, not through a server.
     */
    suspend fun withLink(context: Context, balance: PersonBalance, currency: String, format: (Long) -> String): String {
        val container = (context.applicationContext as com.leftovers.app.LeftoversApp).container
        val settings = container.settings
        val device = settings.syncDevice()
        // Only when the user has chosen to put them in reminders.
        val pay = if (settings.payShare()) settings.payDetails() else SharedOwed.Pay()
        val splits = splitsOf(balance, container)
        val text = message(balance, format, splits)
        val ways = payLines(pay, currency)
        val link = SharedOwed.link(device, currency, balance.entries, pay, splits) ?: return text + ways
        return text + ways + "\n\nSee it, and add it to your Leftovers with a tap:\n" + link
    }

    /**
     * The message for a group chat about a bill split with it: what it was, its total and how many shared it, then
     * each person still to pay with their share. "Dinner: ₹3,000, split 4 ways. Rahul ₹750, Priya ₹750..."
     */
    fun groupMessage(note: String, split: Split, shares: List<com.leftovers.app.data.Debt>, format: (Long) -> String): String {
        val open = shares.filter { it.amountMinor > 0 && !it.settled && !it.repayment }
        val what = note.trim().ifEmpty { "The bill" }
        val lines = open.joinToString("\n") { "${it.person.trim()}: ${format(it.amountMinor)}" }
        return "$what: ${format(split.totalMinor)}, split ${split.people} ways.\n\n$lines"
    }

    /** The group message with the payment details and one link everyone in it can open; null when nobody owes. */
    suspend fun groupWithLink(context: Context, billId: Long, currency: String, format: (Long) -> String): String? {
        val container = (context.applicationContext as com.leftovers.app.LeftoversApp).container
        val settings = container.settings
        val bill = container.repository.getTransaction(billId) ?: return null
        val shares = container.planning.debts.first().filter { it.splitOf == billId && !it.repayment }
        val split = Split(bill.amountMinor + shares.sumOf { kotlin.math.abs(it.amountMinor) }, shares.size + 1)
        val pay = if (settings.payShare()) settings.payDetails() else SharedOwed.Pay()
        val note = shares.firstOrNull { it.note.isNotBlank() }?.note ?: com.leftovers.app.data.noteText(bill.note)
        val link = SharedOwed.groupLink(settings.syncDevice(), currency, shares, split, pay) ?: return null
        return groupMessage(note, split, shares, format) + payLines(pay, currency) + "\n\nTap your name to see your share, pay, or add it to your Leftovers:\n" + link
    }

    /** The group bills [balance]'s entries were split from: each one's total, and how many shared it. */
    private suspend fun splitsOf(balance: PersonBalance, container: com.leftovers.app.AppContainer): Map<Long, Split> {
        val ids = balance.entries.mapNotNull { it.splitOf }.toSet()
        if (ids.isEmpty()) return emptyMap()
        val all = container.planning.debts.first()
        return ids.mapNotNull { id ->
            // Your own share is the expense itself; everyone else's is a money-owed entry split from it.
            val mine = container.repository.getTransaction(id)?.amountMinor ?: return@mapNotNull null
            val others = all.filter { it.splitOf == id && !it.repayment }
            id to Split(mine + others.sumOf { kotlin.math.abs(it.amountMinor) }, others.size + 1)
        }.toMap()
    }

    /**
     * "Pay by UPI: asha@okaxis", and the payment link, under the message, for anyone who doesn't open the link. UPI
     * only for amounts in rupees, the one currency it pays in.
     */
    fun payLines(pay: SharedOwed.Pay, currency: String = "INR"): String {
        val clean = SharedOwed.clean(pay)
        val upi = clean.upi.isNotEmpty() && (currency.isBlank() || currency == "INR")
        val lines = listOfNotNull(
            clean.upi.takeIf { upi }?.let { "Pay by UPI: $it" },
            clean.link.takeIf { it.isNotEmpty() }?.let { "${if (!upi) "Pay" else "Or pay"} here: $it" },
            clean.bank.takeIf { it.isNotEmpty() }?.let { "Bank: " + it.replace("\n", ", ") },
        )
        return if (lines.isEmpty()) "" else "\n\n" + lines.joinToString("\n")
    }

    /** Opens the share sheet with [text]; nothing is sent until the user picks an app and sends it there. */
    fun share(context: Context, text: String, person: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Remind $person").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
