package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType

/**
 * Entries added at once, as an import does: ten or more saved within moments of each other. Found again later so
 * a file that came in wrong can be taken back as a whole. Entries typed in one by one are never that close.
 */
object ImportBatches {
    /** One import: the entries saved from [from] to [to] (milliseconds), how many, and what they add up to. */
    data class Batch(val from: Long, val to: Long, val count: Int, val spent: Long, val received: Long)

    /** An import saves its entries in one go; two seconds between them means something else. */
    private const val GAP_MS = 2_000L
    private const val AT_LEAST = 10

    fun find(items: List<TransactionItem>): List<Batch> {
        val sorted = items.sortedBy { it.createdAt }
        val groups = mutableListOf<MutableList<TransactionItem>>()
        for (t in sorted) {
            val last = groups.lastOrNull()
            if (last != null && t.createdAt - last.last().createdAt <= GAP_MS) last += t else groups += mutableListOf(t)
        }
        return groups.filter { it.size >= AT_LEAST }.map { g ->
            Batch(
                g.first().createdAt, g.last().createdAt, g.size,
                g.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
                g.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
            )
        }.sortedByDescending { it.from }
    }

    fun inBatch(t: TransactionItem, b: Batch) = t.createdAt in b.from..b.to
}
