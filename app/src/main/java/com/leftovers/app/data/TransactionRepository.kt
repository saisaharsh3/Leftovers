package com.leftovers.app.data

import com.leftovers.app.util.ReceiptStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import java.time.YearMonth

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringDao,
    private val deletedDao: DeletedDao,
) {
    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val allTransactions: Flow<List<TransactionItem>> = transactionDao.observeAll()

    fun transactionsIn(month: YearMonth): Flow<List<TransactionItem>> =
        transactionDao.observeRange(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    suspend fun getAllTransactions(): List<TransactionItem> = transactionDao.getAll()
    suspend fun getTransaction(id: Long): Transaction? = transactionDao.getById(id)
    /** Saves an entry. One coming back (Undo) is taken out of Deleted entries. */
    suspend fun saveTransaction(transaction: Transaction) {
        transactionDao.upsert(transaction)
        if (transaction.id != 0L) deletedDao.delete(listOf(transaction.id))
    }

    /** Deletes an entry, keeping a copy in Deleted entries (with its receipt photo) so it can be restored. */
    suspend fun deleteTransaction(transaction: Transaction) {
        deletedDao.upsert(DeletedTransaction.from(transaction))
        transactionDao.delete(transaction)
    }

    val deleted: Flow<List<DeletedItem>> = deletedDao.observeAll()
    val deletedCount: Flow<Int> = deletedDao.count()

    /** Puts deleted entries back. If an entry's category was removed since, it goes to "Other". */
    suspend fun restoreDeleted(ids: List<Long>) {
        val entries = deletedDao.getByIds(ids)
        if (entries.isEmpty()) return
        val categories = categoryDao.observeAll().first()
        entries.forEach { e ->
            val categoryId = if (categories.any { it.id == e.categoryId }) e.categoryId else {
                val sameType = categories.filter { it.type == e.type }
                (sameType.firstOrNull { it.name.equals("Other", ignoreCase = true) } ?: sameType.firstOrNull() ?: return@forEach).id
            }
            transactionDao.upsert(e.toTransaction().copy(categoryId = categoryId))
        }
        deletedDao.delete(entries.map { it.id })
    }

    /** Removes deleted entries for good, with their receipt photos. */
    suspend fun deleteForever(ids: List<Long>) {
        val entries = deletedDao.getByIds(ids)
        deletedDao.delete(entries.map { it.id })
        entries.forEach { ReceiptStore.delete(it.receiptPath) }
    }

    /** Clears entries deleted more than [keepDays] days ago; 0 keeps them forever. */
    suspend fun purgeDeleted(keepDays: Int, now: Long = System.currentTimeMillis()) {
        if (keepDays <= 0) return
        deleteForever(deletedDao.olderThan(now - TimeUnit.DAYS.toMillis(keepDays.toLong())).map { it.id })
    }

    suspend fun expenseTotal(month: YearMonth): Long =
        transactionDao.expenseTotal(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    suspend fun expenseTotal(month: YearMonth, categoryId: Long): Long =
        transactionDao.expenseTotalForCategory(categoryId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    suspend fun getCategory(id: Long): Category? = categoryDao.getById(id)
    suspend fun saveCategory(category: Category) = categoryDao.upsert(category)
    suspend fun setCategoryBudget(id: Long, budgetMinor: Long?) = categoryDao.setBudget(id, budgetMinor)

    /** Deletes the category, or returns how many entries still use it (and deletes nothing). */
    suspend fun deleteCategory(category: Category): Int {
        val used = transactionDao.countForCategory(category.id) + recurringDao.countForCategory(category.id)
        if (used == 0) categoryDao.delete(category)
        return used
    }

    suspend fun deleteAllData() {
        deleteForever(deletedDao.getAll().map { it.id })
        transactionDao.deleteAll()
        categoryDao.clearBudgets()
    }
}
