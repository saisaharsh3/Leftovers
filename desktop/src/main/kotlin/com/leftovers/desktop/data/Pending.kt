package com.leftovers.desktop.data

import com.leftovers.app.data.BudgetCycle
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TxType
import java.time.LocalDate
import java.time.YearMonth

// As in the phone's PlanningRepository.kt, which can't be shared as it holds the database code.

/** Subscriptions still to be charged from [today] up to the last day of the budget month. */
fun List<RecurringItem>.pendingExpenses(cycle: BudgetCycle, today: LocalDate = LocalDate.now()): Long =
    if (cycle.start.dayOfMonth == 1) pendingExpenses(cycle.month)
    else filter { it.type == TxType.EXPENSE && it.active }.sumOf { r ->
        val rec = r.toRecurring()
        generateSequence(YearMonth.from(today)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(cycle.end) }
            .filter { m -> rec.isPendingIn(m) && rec.chargeDate(m).let { it >= today && it <= cycle.end } }
            .count() * r.amountMinor
    }

fun List<RecurringItem>.pendingExpenses(month: YearMonth): Long =
    filter { it.type == TxType.EXPENSE && it.toRecurring().isPendingIn(month) }.sumOf { it.amountMinor }
