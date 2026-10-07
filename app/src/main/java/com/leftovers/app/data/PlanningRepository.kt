package com.leftovers.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class PlanningRepository(private val database: AppDatabase) {
    private val recurringDao = database.recurringDao()
    private val goalDao = database.goalDao()
    private val transactionDao = database.transactionDao()
    private val debtDao = database.debtDao()

    val recurring: Flow<List<RecurringItem>> = recurringDao.observeAll()
    val goals: Flow<List<GoalWithSaved>> = goalDao.observeAll()

    val debts: Flow<List<Debt>> = debtDao.observeAll()

    fun deposits(goalId: Long): Flow<List<GoalDeposit>> = goalDao.observeDeposits(goalId)

    suspend fun saveRecurring(recurring: Recurring) = recurringDao.upsert(recurring)
    suspend fun deleteRecurring(recurring: Recurring) = recurringDao.delete(recurring)

    suspend fun saveGoal(goal: Goal) = goalDao.upsert(goal)
    suspend fun deleteGoal(goal: Goal) = goalDao.delete(goal)
    suspend fun saveDeposit(deposit: GoalDeposit) = goalDao.upsertDeposit(deposit)
    suspend fun deleteDeposit(deposit: GoalDeposit) = goalDao.deleteDeposit(deposit)

    suspend fun saveDebt(debt: Debt) = debtDao.upsert(debt)
    suspend fun deleteDebt(debt: Debt) {
        debtDao.delete(debt)
        debt.photos.forEach { com.leftovers.app.util.ReceiptStore.delete(it) }
    }
    suspend fun settle(person: String) = debtDao.settle(person)

    /**
     * Logs every recurring charge whose date has arrived but hasn't been recorded yet,
     * catching up on months the app wasn't opened. Returns the transactions it created.
     */
    suspend fun postDueRecurring(accountId: Long?, today: LocalDate = LocalDate.now()): List<Transaction> {
        val posted = mutableListOf<Transaction>()
        database.withTransaction {
            val currentMonth = YearMonth.from(today)
            recurringDao.getActive().forEach { r ->
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
                    val tx = Transaction(
                        amountMinor = r.amountMinor,
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
fun List<RecurringItem>.pendingExpenses(month: YearMonth): Long =
    filter { it.type == TxType.EXPENSE && it.toRecurring().isPendingIn(month) }.sumOf { it.amountMinor }
