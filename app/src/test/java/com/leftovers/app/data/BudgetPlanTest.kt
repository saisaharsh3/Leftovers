package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetPlanTest {
    private val aug = YearMonth.of(2026, 8)
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)

    private var nextId = 1L
    private fun tx(type: TxType, amount: Long, month: YearMonth, addsToBudget: Boolean = false) = TransactionItem(
        id = nextId++, amountMinor = amount, type = type, categoryId = 1, epochDay = LocalDate.of(month.year, month.month, 10).toEpochDay(),
        note = "", createdAt = 0, categoryName = "", categoryEmoji = "", categoryColor = 0, accountId = null, receiptPath = null,
        addsToBudget = addsToBudget,
    )

    private val plan = BudgetPlan(monthlyMinor = 10_000, carry = CarryMode.ALWAYS, carryFrom = aug)

    @Test fun leftoversCarryForward() {
        val items = listOf(tx(TxType.EXPENSE, 6_000, aug), tx(TxType.EXPENSE, 9_000, sep))
        // Aug leaves 4,000; Sep has 14,000 and spends 9,000, leaving 5,000 for Oct.
        assertEquals(MonthBudget(10_000, 0, 5_000), plan.breakdownFor(oct, items))
    }

    @Test fun overspendingIsNotCarried() {
        val items = listOf(tx(TxType.EXPENSE, 15_000, sep))
        assertEquals(10_000, plan.copy(carryFrom = sep).budgetFor(oct, items))
    }

    @Test fun extraIncomeAddsToItsMonthAndCarries() {
        val items = listOf(
            tx(TxType.INCOME, 2_000, sep, addsToBudget = true),
            tx(TxType.INCOME, 50_000, sep, addsToBudget = false), // salary
            tx(TxType.EXPENSE, 11_000, sep),
            tx(TxType.INCOME, 500, oct, addsToBudget = true),
        )
        assertEquals(12_000, plan.copy(carryFrom = sep).budgetFor(sep, items))
        assertEquals(MonthBudget(10_000, 500, 1_000), plan.copy(carryFrom = sep).breakdownFor(oct, items))
    }

    @Test fun nothingCarriesBeforeStartOrWhenOff() {
        val items = listOf(tx(TxType.EXPENSE, 1_000, aug))
        assertEquals(10_000, plan.copy(carryFrom = sep).budgetFor(sep, items))
        assertEquals(10_000, plan.copy(carry = CarryMode.NEVER).budgetFor(oct, items))
    }

    @Test fun smartYearlySplitDoesNotDoubleCarry() {
        val smart = BudgetPlan(mode = BudgetMode.YEARLY, yearlyMinor = 120_000, split = YearSplit.SMART, carryFrom = aug)
        val items = listOf(tx(TxType.EXPENSE, 0, aug))
        // Jan–Sep spent nothing, so Oct gets 120,000 / 3 months left; no extra carry on top.
        assertEquals(40_000, smart.budgetFor(oct, items))
    }

    @Test fun askModeCarriesOnlyMonthsTheUserAccepted() {
        val ask = plan.copy(carry = CarryMode.ASK)
        val items = listOf(tx(TxType.EXPENSE, 6_000, aug), tx(TxType.EXPENSE, 7_000, sep))
        // Nothing answered yet: no carry, and September's 3,000 is waiting for an answer.
        assertEquals(10_000, ask.budgetFor(oct, items))
        assertEquals(3_000L, ask.pendingCarry(oct, items))
        // Declined August, accepted September.
        val answered = ask.copy(carryChoices = mapOf(aug to false, sep to true))
        assertEquals(MonthBudget(10_000, 0, 3_000), answered.breakdownFor(oct, items))
        assertEquals(null, answered.pendingCarry(oct, items))
    }

    @Test fun choicesRoundTrip() {
        val choices = mapOf(aug to true, sep to false)
        assertEquals(choices, BudgetPlan.decodeChoices(BudgetPlan.encodeChoices(choices)))
    }
}
