package com.leftovers.app.util

import com.leftovers.app.data.Category
import com.leftovers.app.data.PersonBalance
import com.leftovers.app.data.TxType

/**
 * Small payments from bank SMS added straight away, when that's switched on, instead of waiting on Home to be
 * checked. Only spending, up to the amount chosen: money coming in, and anything that may be paying someone back,
 * still waits, so it can be settled in Money owed rather than counted.
 */
object AutoAdd {
    /** Whether a payment of [amountMinor] to [payee] may be added without asking. */
    fun allowed(on: Boolean, maxMinor: Long, amountMinor: Long, isIncome: Boolean, payee: String, owed: List<PersonBalance>): Boolean =
        on && !isIncome && amountMinor in 1..maxMinor && Payback.find(payee, amountMinor, incoming = false, owed = owed) == null

    /**
     * Its category: where the same place was filed last time, else "Other", else the first spending category; never
     * a hidden one. Null when there's no spending category at all.
     */
    fun category(payee: String, past: List<MerchantMemory.Past>, categories: List<Category>): Long? {
        val usable = categories.filter { it.type == TxType.EXPENSE && !it.hidden }
        MerchantMemory.categoryFor(payee, TxType.EXPENSE, past)?.takeIf { id -> usable.any { it.id == id } }?.let { return it }
        return (usable.firstOrNull { it.name.equals("Other", ignoreCase = true) } ?: usable.firstOrNull())?.id
    }
}
