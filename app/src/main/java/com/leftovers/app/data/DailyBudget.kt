package com.leftovers.app.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * How much can be spent today: what's left of the month's budget (before today, minus
 * subscriptions still to be charged this month), spread evenly over the remaining days.
 */
data class DailyBudget(
    val dailyLimit: Long,
    val spentToday: Long,
    /** The month's budget, and what's left of it after everything spent and the subscriptions still due. */
    val monthlyBudget: Long = 0,
    val monthLeft: Long = 0,
) {
    val leftToday: Long get() = dailyLimit - spentToday
    val usedFraction: Float get() = if (dailyLimit > 0) spentToday.toFloat() / dailyLimit else 1f
    /** The month's budget was used up before today: there's no daily allowance, and [monthLeft] says by how much it's over. */
    val monthUsedUp: Boolean get() = monthlyBudget > 0 && monthLeft + spentToday <= 0
}

fun dailyBudget(
    monthlyBudget: Long,
    monthItems: List<TransactionItem>,
    reservedMinor: Long = 0,
    today: LocalDate = LocalDate.now(),
): DailyBudget? {
    if (monthlyBudget <= 0) return null
    val todayDay = today.toEpochDay()
    // Spending from an account left out of the budget (#33) doesn't use it up.
    val expenses = monthItems.filter { it.type == TxType.EXPENSE && !it.outOfBudget }
    val spentBefore = expenses.filter { it.epochDay < todayDay }.sumOf { it.amountMinor }
    val spentToday = expenses.filter { it.epochDay == todayDay }.sumOf { it.amountMinor }
    val daysLeft = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1
    val daily = (monthlyBudget - spentBefore - reservedMinor).coerceAtLeast(0) / daysLeft
    return DailyBudget(daily, spentToday, monthlyBudget, monthlyBudget - spentBefore - spentToday - reservedMinor)
}
