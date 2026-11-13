package com.leftovers.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class PlanningRepository(private val database: AppDatabase) {
    private val recurringDao = database.recurringDao()
    private val goalDao = database.goalDao()
    private val transactionDao = database.transactionDao()
    private val debtDao = database.debtDao()

    val recurring: Flow<List<RecurringItem>> = recurringDao.observeAll().sharedLive()
    val goals: Flow<List<GoalWithSaved>> = goalDao.observeAll().sharedLive()

    val debts: Flow<List<Debt>> = debtDao.observeAll().sharedLive()

    fun deposits(goalId: Long): Flow<List<GoalDeposit>> = goalDao.observeDeposits(goalId)
    val allDeposits: Flow<List<GoalDeposit>> = goalDao.observeAllDeposits()

    suspend fun saveRecurring(recurring: Recurring) = recurringDao.upsert(recurring)
    suspend fun deleteRecurring(recurring: Recurring) = recurringDao.delete(recurring)

    suspend fun saveGoal(goal: Goal) = goalDao.upsert(goal)
    suspend fun deleteGoal(goal: Goal) = goalDao.delete(goal)
    suspend fun saveDeposit(deposit: GoalDeposit) = goalDao.upsertDeposit(deposit)
    suspend fun deleteDeposit(deposit: GoalDeposit) = goalDao.deleteDeposit(deposit)

    suspend fun saveDebt(debt: Debt) = debtDao.upsert(debt)

    /**
     * A payment that pays [person] back, or them paying you: recorded against what's open with them (see [settleUp]),
     * never as income or spending. Returns how to take it back, for Undo: their entries as they were.
     */
    suspend fun recordPayback(person: String, amountMinor: Long, accountId: Long?, epochDay: Long): suspend () -> Unit {
        val key = person.trim().lowercase()
        val before = debtDao.getAll().filter { it.person.trim().lowercase() == key }
        settleUp(person, amountMinor, accountId, chosen = accountId != null, epochDay = epochDay)
        return {
            database.withTransaction {
                val keep = before.map { it.id }.toSet()
                debtDao.getAll().filter { it.person.trim().lowercase() == key && it.id !in keep }.forEach { debtDao.delete(it) }
                before.forEach { debtDao.upsert(it) }
            }
        }
    }
    suspend fun deleteDebt(debt: Debt) {
        debtDao.delete(debt)
        debt.photos.forEach { com.leftovers.app.util.ReceiptStore.delete(it) }
    }
    /**
     * Records [amountMinor] paid back by or to [person], into or out of [accountId] (unless [chosen], the account of
     * their latest entry; none when their entries have none). Paying all of it settles them; paying more than is
     * owed counts as paying it all.
     */
    suspend fun settleUp(
        person: String,
        amountMinor: Long? = null,
        accountId: Long? = null,
        chosen: Boolean = false,
        epochDay: Long = LocalDate.now().toEpochDay(),
    ) {
        database.withTransaction {
            val open = debtDao.openFor(person)
            val net = open.sumOf { it.amountMinor }
            if (net == 0L) {
                debtDao.settle(person, epochDay)
                return@withTransaction
            }
            val paid = (amountMinor ?: kotlin.math.abs(net)).coerceIn(1, kotlin.math.abs(net))
            debtDao.upsert(
                Debt(
                    person = open.first().person.trim(),
                    amountMinor = if (net > 0) -paid else paid,
                    note = "Paid back",
                    epochDay = epochDay,
                    accountId = if (chosen) accountId else open.firstNotNullOfOrNull { it.accountId },
                    repayment = true,
                ),
            )
            if (paid == kotlin.math.abs(net)) debtDao.settle(person, epochDay)
        }
    }

    /**
     * One entry paid back in full, apart from the rest with that person: it's settled, and the money moves back
     * the other way through its account, as a settled repayment so it doesn't count again.
     */
    suspend fun settleEntry(debt: Debt, epochDay: Long = LocalDate.now().toEpochDay()) {
        database.withTransaction {
            debtDao.upsert(debt.copy(settled = true, settledOn = epochDay))
            debtDao.upsert(
                Debt(
                    person = debt.person.trim(),
                    amountMinor = -debt.amountMinor,
                    note = "Paid back",
                    epochDay = epochDay,
                    accountId = debt.accountId,
                    repayment = true,
                    settled = true,
                    settledOn = epochDay,
                ),
            )
        }
    }

    /**
     * Whether [item] is [r]'s charge for [date], logged some other way: the same kind, about the amount (a salary
     * or a bill isn't always exactly the same), within a week of it, and in its category or with its name in the
     * note. Then the subscription isn't logged again. Paid on the 1st instead of the 29th still counts.
     */
    internal fun alreadyLogged(item: TransactionItem, r: Recurring, amount: Long, date: LocalDate): Boolean =
        item.type == r.type && SubscriptionMatch.closeAmount(item.amountMinor, amount) && kotlin.math.abs(item.epochDay - date.toEpochDay()) <= 7 &&
            (item.categoryId == r.categoryId || (r.name.isNotBlank() && item.note.contains(r.name.trim(), ignoreCase = true)))

    /** Marks [r]'s [month] paid with an entry for what was really paid, and when (#64). */
    suspend fun payRecurring(r: Recurring, month: YearMonth, amountMinor: Long, epochDay: Long, accountId: Long?): Long {
        var id = 0L
        database.withTransaction {
            id = transactionDao.upsert(
                Transaction(amountMinor = amountMinor, type = r.type, categoryId = r.categoryId, epochDay = epochDay, note = r.name, accountId = accountId ?: r.accountId),
            )
            recurringDao.setLastPosted(r.id, month.toString())
        }
        return id
    }

    /** Skips [r]'s [month]: nothing logged, and it's no longer due. */
    suspend fun skipRecurring(r: Recurring, month: YearMonth) = recurringDao.setLastPosted(r.id, month.toString())

    /** Undoes [payRecurring] or [skipRecurring]: that month is due again, and its entry (if any) goes. */
    suspend fun unpayRecurring(r: Recurring, entryId: Long?) {
        database.withTransaction {
            entryId?.let { id -> transactionDao.getById(id)?.let { transactionDao.delete(it) } }
            recurringDao.setLastPosted(r.id, r.lastPostedMonth)
        }
    }

    /** Everything with [person] paid back in full. */
    suspend fun settle(person: String) = settleUp(person)

    /** Removes every lent or borrowed entry, with its photos. */
    suspend fun deleteAllDebts() {
        val all = debtDao.getAll()
        debtDao.deleteAll()
        all.flatMap { it.photos }.forEach { com.leftovers.app.util.ReceiptStore.delete(it) }
    }

    /**
     * Logs every recurring charge whose date has arrived but hasn't been recorded yet,
     * catching up on months the app wasn't opened. Returns the transactions it created.
     */
    suspend fun postDueRecurring(accountId: Long?, today: LocalDate = LocalDate.now()): List<Transaction> {
        val posted = mutableListOf<Transaction>()
        // Interest is worked out on each account's balance as it is when it's logged (#32).
        val balances = database.accountDao().observeWithBalance().first().associate { it.id to it.balanceMinor }.toMutableMap()
        database.withTransaction {
            val currentMonth = YearMonth.from(today)
            // Entries already there, to see whether a bill was logged some other way first.
            val existing = transactionDao.getAll().toMutableList()
            // Marked paid by hand (#64): they wait on Home as due instead.
            recurringDao.getActive().filterNot { it.manual }.forEach { r ->
                var month = r.lastPostedMonth?.let { YearMonth.parse(it).plusMonths(1) } ?: YearMonth.parse(r.startMonth)
                var lastPosted: YearMonth? = null
                while (month <= currentMonth) {
                    // Yearly bills are only charged in their billing month.
                    if (!r.isDueIn(month)) {
                        month = month.plusMonths(1)
                        continue
                    }
                    val date = r.chargeDate(month)
                    if (date > today) break
                    val into = r.accountId ?: accountId
                    val amount = r.interestOn(into?.let { balances[it] } ?: 0L) ?: r.amountMinor
                    if (amount <= 0) {
                        // No balance, no interest this time.
                        lastPosted = month
                        month = month.plusMonths(1)
                        continue
                    }
                    // Already logged by hand, or from its SMS or email, around its date: that's this month's.
                    if (existing.any { alreadyLogged(it, r, amount, date) }) {
                        lastPosted = month
                        month = month.plusMonths(1)
                        continue
                    }
                    into?.let { balances[it] = (balances[it] ?: 0L) + amount }
                    val tx = Transaction(
                        amountMinor = amount,
                        type = r.type,
                        categoryId = r.categoryId,
                        epochDay = date.toEpochDay(),
                        note = r.name,
                        accountId = r.accountId ?: accountId,
                    )
                    transactionDao.upsert(tx)
                    posted += tx
                    lastPosted = month
                    month = month.plusMonths(1)
                }
                lastPosted?.let { recurringDao.setLastPosted(r.id, it.toString()) }
            }
        }
        return posted
    }
}

/** Expense subscriptions that will still be charged later in [month]. */
/** Subscriptions still to be charged from [today] up to [end], the last day of the budget month (#34). */
fun List<RecurringItem>.pendingExpenses(cycle: BudgetCycle, today: java.time.LocalDate = java.time.LocalDate.now()): Long =
    if (cycle.start.dayOfMonth == 1) pendingExpenses(cycle.month)
    else filter { it.type == TxType.EXPENSE && it.active }.sumOf { r ->
        // Each month the budget month touches, once: charged on a day from today to the end, and not logged yet.
        val rec = r.toRecurring()
        generateSequence(YearMonth.from(today)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(cycle.end) }
            .filter { m -> rec.isPendingIn(m) && rec.chargeDate(m).let { it >= today && it <= cycle.end } }
            .count() * r.amountMinor
    }

fun List<RecurringItem>.pendingExpenses(month: YearMonth): Long =
    filter { it.type == TxType.EXPENSE && it.toRecurring().isPendingIn(month) }.sumOf { it.amountMinor }
