package com.leftovers.app.util

import com.leftovers.app.data.TxType

/**
 * Remembers where a payee's money went: a detected payment to "SWIGGY" opens under the category the last
 * Swiggy entry was filed in. Names are compared without case, digits or punctuation, and a note that was
 * added to ("Swiggy dinner") still counts as the same place.
 */
object MerchantMemory {
    /** One past entry, as far as remembering its category goes. */
    data class Past(val note: String, val type: TxType, val categoryId: Long, val epochDay: Long, val createdAt: Long)

    /** "SWIGGY*ORDER 1234" → "swiggy order". */
    fun key(text: String): String =
        text.lowercase().replace(Regex("""[^\p{L}]+"""), " ").trim().replace(Regex("""\s+"""), " ")

    /** Whether a past entry's [note] is about the same place as [merchant]. */
    fun samePlace(merchant: String, note: String): Boolean {
        val a = key(merchant)
        val b = key(note)
        if (a.length < 3 || b.length < 3) return false
        return a == b || b.startsWith("$a ") || a.startsWith("$b ") || a.split(' ').first() == b.split(' ').first() && a.split(' ').first().length >= 4
    }

    /** The category of the most recent entry of [type] about the same place as [merchant], if any. */
    fun categoryFor(merchant: String, type: TxType, history: List<Past>): Long? =
        history.asSequence()
            .filter { it.type == type && samePlace(merchant, it.note) }
            .maxWithOrNull(compareBy<Past> { it.epochDay }.thenBy { it.createdAt })
            ?.categoryId
}
