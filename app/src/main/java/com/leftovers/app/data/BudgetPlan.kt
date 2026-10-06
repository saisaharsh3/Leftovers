package com.leftovers.app.data

import java.time.YearMonth

enum class BudgetMode { MONTHLY, YEARLY }

/** What happens to money left unspent when a month ends. Overspending is never carried. */
enum class CarryMode(val label: String) { ASK("Ask me"), ALWAYS("Always"), NEVER("Never") }

enum class YearSplit(val title: String, val description: String) {
    EVEN("Equal every month", "Your yearly budget ÷ 12. Simple and predictable."),
    SMART("Smart rollover", "What's left of the year ÷ months left. Spend less this month and next month gets more."),
    CUSTOM("Custom per month", "Set each month yourself, e.g. more for festival or holiday months."),
}

data class BudgetPlan(
    val mode: BudgetMode = BudgetMode.MONTHLY,
    val monthlyMinor: Long = 0,
    val yearlyMinor: Long = 0,
    val split: YearSplit = YearSplit.EVEN,
    /** Custom amounts keyed by month number (1 = January). */
    val custom: Map<Int, Long> = emptyMap(),
    val carry: CarryMode = CarryMode.ASK,
    /** First month whose leftovers can carry forward; set when the plan is first saved. */
    val carryFrom: YearMonth? = null,
    /** In [CarryMode.ASK], the user's answer for each finished month: true = carry into the next. */
    val carryChoices: Map<YearMonth, Boolean> = emptyMap(),
) {
    val isSet: Boolean
        get() = when (mode) {
            BudgetMode.MONTHLY -> monthlyMinor > 0
            BudgetMode.YEARLY -> yearlyMinor > 0
        }

    val customTotal: Long get() = custom.values.sum()

    /** Smart rollover already moves unspent money forward within the year. */
    val supportsCarry: Boolean get() = !(mode == BudgetMode.YEARLY && split == YearSplit.SMART)

    private fun carries(month: YearMonth): Boolean = supportsCarry && when (carry) {
        CarryMode.ALWAYS -> true
        CarryMode.NEVER -> false
        CarryMode.ASK -> carryChoices[month] == true
    }

    /**
     * The total spending budget for [month]: the planned amount, plus income from categories that
     * add to the budget, plus anything carried over. [items] must contain all transactions.
     */
    fun budgetFor(month: YearMonth, items: List<TransactionItem>): Long = breakdownFor(month, items).total

    fun breakdownFor(month: YearMonth, items: List<TransactionItem>): MonthBudget {
        val byMonth = items.groupBy { YearMonth.from(it.date) }
        fun income(m: YearMonth) = byMonth[m].orEmpty().filter { it.type == TxType.INCOME && it.addsToBudget }.sumOf { it.amountMinor }
        fun spent(m: YearMonth) = byMonth[m].orEmpty().filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }

        var carried = 0L
        val start = carryFrom
        if (start != null) {
            var m: YearMonth = start
            while (m < month) {
                val left = (baseFor(m, items) + income(m) + carried - spent(m)).coerceAtLeast(0)
                carried = if (carries(m)) left else 0
                m = m.plusMonths(1)
            }
        }
        return MonthBudget(baseFor(month, items), income(month), carried)
    }

    /** What was left unspent in [month] (never negative). */
    fun leftoverOf(month: YearMonth, items: List<TransactionItem>): Long {
        val spent = items.filter { it.type == TxType.EXPENSE && YearMonth.from(it.date) == month }.sumOf { it.amountMinor }
        return (budgetFor(month, items) - spent).coerceAtLeast(0)
    }

    /** Last month's leftover, when the user still needs to decide whether to carry it into [now]. */
    fun pendingCarry(now: YearMonth, items: List<TransactionItem>): Long? {
        val prev = now.minusMonths(1)
        val start = carryFrom ?: return null
        if (!isSet || !supportsCarry || carry != CarryMode.ASK || prev < start || prev in carryChoices) return null
        return leftoverOf(prev, items).takeIf { it > 0 }
    }

    /** The planned amount for [month] alone, before income and carry-over. */
    fun baseFor(month: YearMonth, items: List<TransactionItem>): Long = when (mode) {
        BudgetMode.MONTHLY -> monthlyMinor
        BudgetMode.YEARLY -> when {
            yearlyMinor <= 0 -> 0
            split == YearSplit.EVEN -> yearlyMinor / 12
            split == YearSplit.CUSTOM -> custom[month.monthValue] ?: 0
            else -> {
                val spentEarlier = items
                    .filter { it.type == TxType.EXPENSE && it.date.year == month.year && it.date.monthValue < month.monthValue }
                    .sumOf { it.amountMinor }
                val monthsLeft = 12 - month.monthValue + 1
                (yearlyMinor - spentEarlier).coerceAtLeast(0) / monthsLeft
            }
        }
    }

    companion object {
        fun encodeChoices(choices: Map<YearMonth, Boolean>): String =
            choices.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${if (it.value) 1 else 0}" }

        fun decodeChoices(raw: String?): Map<YearMonth, Boolean> =
            raw.orEmpty().split(';').mapNotNull { part ->
                val (k, v) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val month = runCatching { YearMonth.parse(k) }.getOrNull() ?: return@mapNotNull null
                month to (v == "1")
            }.toMap()

        fun encodeCustom(custom: Map<Int, Long>): String =
            custom.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

        fun decodeCustom(raw: String?): Map<Int, Long> =
            raw.orEmpty().split(';').mapNotNull { part ->
                val (k, v) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val month = k.toIntOrNull() ?: return@mapNotNull null
                val amount = v.toLongOrNull() ?: return@mapNotNull null
                month to amount
            }.toMap()
    }
}

/** A month's budget split into where the money comes from. */
data class MonthBudget(val planned: Long, val income: Long, val carried: Long) {
    val total: Long get() = planned + income + carried
}
