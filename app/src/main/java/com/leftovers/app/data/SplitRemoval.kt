package com.leftovers.app.data

/**
 * Someone taken off a split bill, keeping its total: their amount goes to you, or is shared out evenly between you
 * and the others who still owe theirs. Anyone who's paid already, or whose amount isn't known yet, keeps theirs.
 */
object SplitRemoval {
    /** Your share after it, and the others' shares that changed. */
    data class Plan(val mineMinor: Long, val shares: List<Debt>)

    fun plan(mineMinor: Long, removedMinor: Long, others: List<Debt>, spread: Boolean): Plan {
        if (removedMinor <= 0) return Plan(mineMinor, emptyList())
        val open = if (spread) others.filter { !it.settled && !it.isPending && it.amountMinor > 0 } else emptyList()
        val each = removedMinor / (open.size + 1)
        // What doesn't divide evenly stays with you, so the total is exact.
        return Plan(mineMinor + removedMinor - each * open.size, open.map { it.copy(amountMinor = it.amountMinor + each) })
    }
}
