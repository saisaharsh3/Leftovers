package com.leftovers.app.data

import java.time.YearMonth

enum class BudgetMode { MONTHLY, YEARLY }

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
) {
    val isSet: Boolean
        get() = when (mode) {
            BudgetMode.MONTHLY -> monthlyMinor > 0
            BudgetMode.YEARLY -> yearlyMinor > 0
        }

    val customTotal: Long get() = custom.values.sum()

    /**
     * The spending budget for [month]. [items] must contain at least that year's transactions
     * (only used by the smart split, which looks at what was spent earlier in the year).
     */
    fun budgetFor(month: YearMonth, items: List<TransactionItem>): Long = when (mode) {
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
