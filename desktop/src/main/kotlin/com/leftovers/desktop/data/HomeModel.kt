package com.leftovers.desktop.data

import com.leftovers.app.data.AccountWithBalance
import com.leftovers.app.data.DailyBudget
import com.leftovers.app.data.Debt
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.PersonBalance
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.dailyBudget
import com.leftovers.app.data.forecastMonthEnd
import com.leftovers.app.data.openBalances
import com.leftovers.app.data.totalOf
import java.time.LocalDate
import java.time.YearMonth

/** A subscription paid by hand whose day has come: which [month]'s, and its [date]. */
data class DueBill(val item: RecurringItem, val month: YearMonth, val date: LocalDate)

/** What Home shows, worked out as the phone's Home works it out. */
data class HomeModel(
    val month: YearMonth,
    val budgetSet: Boolean,
    val today: DailyBudget?,
    val daysLeft: Int,
    val monthSpent: Long,
    val monthIncome: Long,
    val upcoming: List<RecurringItem>,
    val due: List<DueBill>,
    val goals: List<GoalWithSaved>,
    val owed: List<PersonBalance>,
    val recent: List<TransactionItem>,
    /** What people owe for bills split with them, by the entry they came from. */
    val shares: Map<Long, List<Debt>>,
    val carryPrompt: Long?,
    val balance: Long,
    val forecast: Long?,
    val accounts: List<AccountWithBalance>,
    val selectedAccount: AccountWithBalance?,
    val newDailyLimit: Boolean,
) {
    companion object {
        fun of(view: View, todayDate: LocalDate = LocalDate.now()): HomeModel {
            val book = view.book
            val plan = book.plan
            val all = view.entries
            val month = YearMonth.from(todayDate)
            val items = all.filter { YearMonth.from(it.date) == month }
            // The budget month, which can start on payday instead of the 1st.
            val cycle = plan.cycleOf(todayDate)
            val budgetItems = all.filter { it.date in cycle }
            val budget = plan.budgetFor(cycle.month, all)
            val selected = view.accounts.firstOrNull { it.id == book.prefs.homeAccountId }
            val shownAll = if (selected == null) all else all.filter { it.accountId == selected.id }
            val shownMonth = if (selected == null) items else items.filter { it.accountId == selected.id }
            val inTotal = view.accounts.filterNot { it.outOfTotal }.sumOf { it.balanceMinor }
            val recurring = view.recurring
            return HomeModel(
                month = month,
                budgetSet = plan.isSet,
                today = dailyBudget(budget, budgetItems, if (plan.envelope) 0L else recurring.pendingExpenses(cycle, todayDate), todayDate, periodEnd = cycle.end),
                daysLeft = cycle.daysLeft(todayDate),
                monthSpent = shownMonth.totalOf(TxType.EXPENSE),
                monthIncome = shownMonth.totalOf(TxType.INCOME),
                upcoming = recurring.filter { it.active && !(it.manual && it.toRecurring().dueMonth(todayDate) != null) }
                    .sortedBy { it.toRecurring().nextChargeDate(todayDate) }
                    .filter { it.toRecurring().nextChargeDate(todayDate) <= todayDate.plusDays(30) }
                    .take(6),
                due = recurring.filter { it.active && it.manual }.mapNotNull { r ->
                    r.toRecurring().dueMonth(todayDate)?.let { m -> DueBill(r, m, r.toRecurring().chargeDate(m)) }
                }.sortedBy { it.date },
                goals = view.goals.filter { !it.reached }.take(5),
                owed = book.debts.openBalances().filter { it.open },
                recent = recentOf(shownAll, book.prefs.splitsAsOneBill, 8),
                shares = book.debts.filter { it.splitOf != null && !it.repayment }.groupBy { it.splitOf!! },
                carryPrompt = plan.pendingCarry(cycle.month, all),
                balance = selected?.balanceMinor ?: inTotal,
                forecast = if (selected == null) forecastMonthEnd(inTotal, all, recurring, todayDate) else null,
                accounts = view.accounts,
                selectedAccount = selected,
                newDailyLimit = book.prefs.showNewDailyLimit,
            )
        }

        /** The newest [count] entries, a split bill counting as one with its parts under it. */
        fun recentOf(all: List<TransactionItem>, oneBill: Boolean, count: Int): List<TransactionItem> {
            if (!oneBill) return all.take(count)
            val mains = all.filter { it.splitOf == null || all.none { m -> m.id == it.splitOf } }.take(count)
            return splitOrder(mains + all.filter { p -> p.splitOf != null && mains.any { it.id == p.splitOf } })
        }
    }
}

/** Each split bill followed by its parts, newest bill first. */
fun splitOrder(items: List<TransactionItem>): List<TransactionItem> {
    val ids = items.map { it.id }.toSet()
    val partsOf = items.filter { it.splitOf != null && it.splitOf in ids }.groupBy { it.splitOf }
    return items.filter { it.splitOf == null || it.splitOf !in ids }.flatMap { listOf(it) + partsOf[it.id].orEmpty() }
}
