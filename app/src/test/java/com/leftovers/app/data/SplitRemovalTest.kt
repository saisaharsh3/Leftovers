package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Someone taken off a split bill: the bill keeps its total. Made-up data only. */
class SplitRemovalTest {
    private fun share(id: Long, person: String, amount: Long, settled: Boolean = false, pending: Int = 0) =
        Debt(id = id, person = person, amountMinor = amount, splitOf = 1, settled = settled, pending = pending)

    @Test fun sharedOutBetweenYouAndTheOthersWhoStillOwe() {
        // ₹1,000 split 4 ways: you, Asha, Ben and Chitra each ₹250. Chitra is taken off.
        val plan = SplitRemoval.plan(250_00, 250_00, listOf(share(2, "Asha", 250_00), share(3, "Ben", 250_00)), spread = true)
        assertEquals(333_34L, plan.mineMinor)
        assertEquals(listOf(333_33L, 333_33L), plan.shares.map { it.amountMinor })
        // Still ₹1,000 in all.
        assertEquals(1_000_00L, plan.mineMinor + plan.shares.sumOf { it.amountMinor })
    }

    @Test fun orAllOfItBecomesYours() {
        val plan = SplitRemoval.plan(250_00, 250_00, listOf(share(2, "Asha", 250_00)), spread = false)
        assertEquals(500_00L, plan.mineMinor)
        assertEquals(emptyList<Debt>(), plan.shares)
    }

    @Test fun someoneWhoPaidOrIsStillPendingKeepsTheirs() {
        val others = listOf(share(2, "Asha", 250_00, settled = true), share(3, "Ben", 0, pending = 1), share(4, "Dev", 250_00))
        val plan = SplitRemoval.plan(250_00, 250_00, others, spread = true)
        assertEquals(listOf(4L), plan.shares.map { it.id })
        assertEquals(375_00L, plan.shares.single().amountMinor)
        assertEquals(375_00L, plan.mineMinor)
    }
}
