package com.leftovers.app.data

import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringDao,
) {
    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val allTransactions: Flow<List<TransactionItem>> = transactionDao.observeAll()

    fun transactionsIn(month: YearMonth): Flow<List<TransactionItem>> =
        transactionDao.observeRange(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    suspend fun getAllTransactions(): List<TransactionItem> = transactionDao.getAll()
    suspend fun getTransaction(id: Long): Transaction? = transactionDao.getById(id)
    suspend fun saveTransaction(transaction: Transaction) = transactionDao.upsert(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)

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
        transactionDao.deleteAll()
        categoryDao.clearBudgets()
    }
}
