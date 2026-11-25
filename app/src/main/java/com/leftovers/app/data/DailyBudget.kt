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
    /** Subscriptions still to be charged before the budget month ends, already set aside from [monthLeft]. */
    val reserved: Long = 0,
) {
    val leftToday: Long get() = dailyLimit - spentToday
    val usedFraction: Float get() = if (dailyLimit > 0) spentToday.toFloat() / dailyLimit else 1f
    /**
     * Over today's allowance, what each day after today can be instead (#61): what's left of the budget, after
     * today's spending and the bills still due, spread over the days after today. Null when today is the last day,
     * or nothing is left.
     */
    fun newDailyLimit(daysLeft: Int): Long? = if (daysLeft > 1 && monthLeft > 0) monthLeft / (daysLeft - 1) else null

    /** The month's budget was used up before today: there's no daily allowance, and [monthLeft] says by how much it's over. */
    val monthUsedUp: Boolean get() = monthlyBudget > 0 && monthLeft + spentToday <= 0
}

fun dailyBudget(
    monthlyBudget: Long,
    monthItems: List<TransactionItem>,
    reservedMinor: Long = 0,
    today: LocalDate = LocalDate.now(),
    /** The last day of the budget month, when it isn't the calendar month's (#34). */
    periodEnd: LocalDate = YearMonth.from(today).atEndOfMonth(),
    /**
     * What subscriptions charged today ([billsOn]). It was set aside from the month while it was still due, so it
     * comes out of the month like that, not out of today's allowance (#74).
     */
    billsToday: Long = 0,
): DailyBudget? {
    if (monthlyBudget <= 0) return null
    val todayDay = today.toEpochDay()
    // Spending from an account left out of the budget (#33) doesn't use it up.
    val expenses = monthItems.filter { it.type == TxType.EXPENSE && !it.outOfBudget }
    val spentBefore = expenses.filter { it.epochDay < todayDay }.sumOf { it.amountMinor }
    val spentTodayAll = expenses.filter { it.epochDay == todayDay }.sumOf { it.amountMinor }
    val bills = billsToday.coerceIn(0, spentTodayAll)
    val daysLeft = (periodEnd.toEpochDay() - todayDay + 1).toInt().coerceAtLeast(1)
    val daily = (monthlyBudget - spentBefore - bills - reservedMinor).coerceAtLeast(0) / daysLeft
    return DailyBudget(daily, spentTodayAll - bills, monthlyBudget, monthlyBudget - spentBefore - spentTodayAll - reservedMinor, reservedMinor)
}

/**
 * Today's spending that a subscription logged (or a scheduled one-off): it was set aside from the month until today,
 * so [dailyBudget] keeps it out of today's allowance (#74).
 */
fun List<TransactionItem>.billsOn(day: LocalDate, recurring: List<RecurringItem>): Long {
    val bills = recurring.filter { it.type == TxType.EXPENSE }
    if (bills.isEmpty()) return 0
    return filter { t ->
        t.type == TxType.EXPENSE && !t.outOfBudget && t.epochDay == day.toEpochDay() &&
            bills.any { it.logged(t.type, t.categoryId, t.note, t.categoryName) }
    }.sumOf { it.amountMinor }
}
