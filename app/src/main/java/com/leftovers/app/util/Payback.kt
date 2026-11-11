package com.leftovers.app.util

import com.leftovers.app.data.PersonBalance
import kotlin.math.abs

/**
 * A detected payment that is someone paying you back, or you paying someone back, rather than income or spending:
 * a split bill's share, or money lent. Money owed is often saved under a short name ("Ravi", "Priya S") while the
 * bank or the payment app writes the full one ("RAVI KUMAR SHARMA", "Mr. Ravi K", "ravisharma@okaxis"), so names
 * are compared word by word, allowing initials and a UPI ID run together. Two people never share a match: when more
 * than one could fit, the amount decides, and when that doesn't either, nothing is suggested.
 */
object Payback {
    /** [person] as saved in Money owed; [owedMinor] what's open with them, positive when they owe you. */
    data class Match(val person: String, val owedMinor: Long, val byName: Boolean)

    // Words around a name in bank messages, and titles, which aren't part of whose name it is.
    private val noise = setOf(
        "mr", "mrs", "ms", "miss", "dr", "shri", "sri", "smt", "kumari", "km", "master", "md", "mohd", "mohammed".takeIf { false },
        "upi", "imps", "neft", "rtgs", "ift", "vpa", "ac", "acct", "a", "c", "from", "to", "by", "the", "and", "ref", "txn",
        "paytm", "phonepe", "gpay", "googlepay", "bhim", "ybl", "ibl", "axl", "okaxis", "okhdfcbank", "okicici", "oksbi",
        "apl", "upi", "pay", "payment", "transfer", "sent", "received",
    ).filterNotNull().toSet()

    /** The words of a name, lower case, without titles or bank words; a UPI ID gives the part before the @, split. */
    fun words(name: String): List<String> {
        val handle = name.substringBefore('@').takeIf { '@' in name }
        val text = (handle ?: name).lowercase()
            .replace(Regex("""[0-9]+"""), " ")
            .replace(Regex("""[^\p{L}]+"""), " ")
        return text.split(' ').filter { it.isNotBlank() && it !in noise }
    }

    /**
     * Whether [saved] (as in Money owed) names the same person as [payer] (as in the message): each of its words is
     * one of the payer's, an initial stands for a word starting with it, and a UPI ID's run-together word
     * ("ravisharma") may hold several of them. Its first word must be a whole word or the start of the run-together
     * one, so "Ravi" is never "Ravindra".
     */
    fun sameName(saved: String, payer: String): Boolean {
        val mine = words(saved)
        val theirs = words(payer)
        if (mine.isEmpty() || theirs.isEmpty()) return false
        // A lone initial says too little to go on, either side.
        if (mine.all { it.length == 1 } || theirs.all { it.length == 1 }) return false
        // Either may have fewer words: "Ravi" saved, "RAVI KUMAR" sent; or "Priya Das" saved, just "PRIYA" sent.
        return within(mine, theirs) || within(theirs, mine) || runTogether(mine, theirs)
    }

    /** Every word of [few] is one of [many]'s, an initial standing for a word that starts with it. */
    private fun within(few: List<String>, many: List<String>): Boolean {
        val left = many.toMutableList()
        for (w in few) {
            val i = left.indexOfFirst { t -> t == w || (w.length == 1 && t.startsWith(w)) || (t.length == 1 && w.startsWith(t)) }
            if (i < 0) return false
            left.removeAt(i)
        }
        // Initials alone don't make a match: at least one whole word must agree.
        return few.any { w -> w.length > 1 && w in many }
    }

    /**
     * A UPI ID with the name run together ("priyanair") holding every saved word, in order, and nothing else
     * ("Priya Nair"). Not a single saved word with more after it: "ravindra" isn't "ravi" plus letters.
     */
    private fun runTogether(mine: List<String>, theirs: List<String>): Boolean {
        if (mine.size < 2) return false
        return theirs.any { t -> t == mine.joinToString("") }
    }

    /** A UPI ID whose run-together word starts with the saved first name: maybe them, if the amount agrees. */
    private fun maybe(saved: String, payer: String): Boolean {
        if ('@' !in payer) return false
        val first = words(saved).firstOrNull()?.takeIf { it.length >= 3 } ?: return false
        return words(payer).any { it.length > first.length && it.startsWith(first) }
    }

    /**
     * Who a detected payment of [amountMinor] is paying back, from [payer] (the name the message gives, may be
     * empty) and the [owed] balances: money in from someone who owes you ([incoming]), or out to someone you owe.
     * By name first, the amount settling a tie; with no name to go on, an amount matching exactly one person's
     * open balance or one of their entries.
     */
    fun find(payer: String, amountMinor: Long, incoming: Boolean, owed: List<PersonBalance>): Match? {
        if (amountMinor <= 0) return null
        val candidates = owed.filter { if (incoming) it.netMinor > 0 else it.netMinor < 0 }
        if (candidates.isEmpty()) return null
        fun exact(b: PersonBalance) = abs(b.netMinor) == amountMinor ||
            b.entries.any { !it.repayment && abs(it.amountMinor) == amountMinor }
        val byName = if (payer.isBlank()) emptyList() else candidates.filter { sameName(it.person, payer) }
        when {
            byName.size == 1 -> return Match(byName.single().person, byName.single().netMinor, byName = true)
            byName.size > 1 -> {
                val one = byName.filter(::exact).singleOrNull() ?: return null
                return Match(one.person, one.netMinor, byName = true)
            }
        }
        // A UPI ID that may be one of them ("priyanair@" for a saved "Priya"): only when the amount agrees too.
        val maybes = candidates.filter { maybe(it.person, payer) }
        if (maybes.isNotEmpty()) {
            val one = maybes.filter(::exact).singleOrNull() ?: return null
            return Match(one.person, one.netMinor, byName = false)
        }
        // A name that names nobody here is someone else: don't pin it on whoever owes that amount.
        if (words(payer).isNotEmpty()) return null
        val one = candidates.filter(::exact).singleOrNull() ?: return null
        return Match(one.person, one.netMinor, byName = false)
    }
}
