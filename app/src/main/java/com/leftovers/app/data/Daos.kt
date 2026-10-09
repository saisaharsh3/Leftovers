package com.leftovers.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val SELECT_ITEMS = """
    SELECT t.id, t.amountMinor, t.type, t.categoryId, t.epochDay, t.note, t.createdAt,
           c.name AS categoryName, c.emoji AS categoryEmoji, c.color AS categoryColor,
           t.accountId, t.receiptPath, c.addsToBudget
    FROM transactions t INNER JOIN categories c ON c.id = t.categoryId
"""

private const val NEWEST_FIRST = " ORDER BY t.epochDay DESC, t.createdAt DESC"

@Dao
interface TransactionDao {
    @Query(SELECT_ITEMS + NEWEST_FIRST)
    fun observeAll(): Flow<List<TransactionItem>>

    @Query(SELECT_ITEMS + " WHERE t.epochDay BETWEEN :fromDay AND :toDay" + NEWEST_FIRST)
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<TransactionItem>>

    @Query(SELECT_ITEMS + NEWEST_FIRST)
    suspend fun getAll(): List<TransactionItem>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Upsert
    suspend fun upsert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE type = 'EXPENSE' AND epochDay BETWEEN :fromDay AND :toDay")
    suspend fun expenseTotal(fromDay: Long, toDay: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE type = 'EXPENSE' AND categoryId = :categoryId AND epochDay BETWEEN :fromDay AND :toDay")
    suspend fun expenseTotalForCategory(categoryId: Long, fromDay: Long, toDay: Long): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    /** Expenses logged (by creation time) between [from] and [to], for duplicate checks. */
    @Query("SELECT amountMinor, createdAt AS at FROM transactions WHERE type = 'EXPENSE' AND createdAt BETWEEN :from AND :to")
    suspend fun expenseAmountsBetween(from: Long, to: Long): List<AmountAt>

    /** Income logged (by creation time) between [from] and [to], for duplicate checks. */
    @Query("SELECT amountMinor, createdAt AS at FROM transactions WHERE type = 'INCOME' AND createdAt BETWEEN :from AND :to")
    suspend fun incomeAmountsBetween(from: Long, to: Long): List<AmountAt>

    @Query("SELECT id FROM transactions WHERE accountId = :accountId")
    suspend fun idsForAccount(accountId: Long): List<Long>

    @Query("UPDATE transactions SET accountId = :accountId WHERE id IN (:ids)")
    suspend fun setAccount(ids: List<Long>, accountId: Long)

    @Query("SELECT receiptPath FROM transactions WHERE receiptPath IS NOT NULL")
    suspend fun receiptPaths(): List<String>
}

@Dao
interface RecurringDao {
    @Query(
        """
        SELECT r.*, c.name AS categoryName, c.emoji AS categoryEmoji, c.color AS categoryColor
        FROM recurring r INNER JOIN categories c ON c.id = r.categoryId
        ORDER BY r.active DESC, r.dayOfMonth
        """,
    )
    fun observeAll(): Flow<List<RecurringItem>>

    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun getActive(): List<Recurring>

    @Upsert
    suspend fun upsert(recurring: Recurring)

    @Delete
    suspend fun delete(recurring: Recurring)

    @Query("UPDATE recurring SET lastPostedMonth = :month WHERE id = :id")
    suspend fun setLastPosted(id: Long, month: String)

    @Query("SELECT COUNT(*) FROM recurring WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int
}

@Dao
interface GoalDao {
    @Query(
        """
        SELECT g.*, COALESCE((SELECT SUM(d.amountMinor) FROM goal_deposits d WHERE d.goalId = g.id), 0) AS savedMinor
        FROM goals g ORDER BY g.createdAt
        """,
    )
    fun observeAll(): Flow<List<GoalWithSaved>>

    @Query("SELECT * FROM goal_deposits WHERE goalId = :goalId ORDER BY epochDay DESC, createdAt DESC")
    fun observeDeposits(goalId: Long): Flow<List<GoalDeposit>>

    /** Returns the new row id for an insert, or -1 for an update. */
    @Upsert
    suspend fun upsert(goal: Goal): Long

    @Delete
    suspend fun delete(goal: Goal)

    @Upsert
    suspend fun upsertDeposit(deposit: GoalDeposit)

    @Delete
    suspend fun deleteDeposit(deposit: GoalDeposit)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Upsert
    suspend fun upsert(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("UPDATE categories SET budgetMinor = :budgetMinor WHERE id = :id")
    suspend fun setBudget(id: Long, budgetMinor: Long?)

    @Query("UPDATE categories SET budgetMinor = NULL")
    suspend fun clearBudgets()
}

@Dao
interface AccountDao {
    @Query(
        """
        SELECT a.*, a.openingMinor
            + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amountMinor ELSE -t.amountMinor END)
                        FROM transactions t WHERE t.accountId = a.id), 0)
            + COALESCE((SELECT SUM(amountMinor) FROM transfers WHERE toAccountId = a.id), 0)
            - COALESCE((SELECT SUM(amountMinor) FROM transfers WHERE fromAccountId = a.id), 0) AS balanceMinor
        FROM accounts a ORDER BY a.id
        """,
    )
    fun observeWithBalance(): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<Account>

    @Upsert
    suspend fun upsert(account: Account): Long

    @Delete
    suspend fun delete(account: Account)

    @Query("SELECT COUNT(*) FROM transfers WHERE fromAccountId = :id OR toAccountId = :id")
    suspend fun transferCount(id: Long): Int
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<Transfer>>

    @Query("SELECT * FROM transfers")
    suspend fun getAll(): List<Transfer>

    @Upsert
    suspend fun upsert(transfer: Transfer)

    @Delete
    suspend fun delete(transfer: Transfer)
}

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_suggestions ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<SmsSuggestion>>

    @androidx.room.Insert
    suspend fun insert(suggestion: SmsSuggestion)

    @Query("DELETE FROM sms_suggestions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT accountDigits FROM sms_suggestions WHERE id = :id")
    suspend fun digitsFor(id: Long): String?

    @Query("SELECT isIncome FROM sms_suggestions WHERE id = :id")
    suspend fun isIncome(id: Long): Boolean?

    @Query("SELECT COUNT(*) FROM sms_suggestions WHERE body = :body")
    suspend fun countWithBody(body: String): Int

    @Query("SELECT amountMinor, receivedAt AS at FROM sms_suggestions WHERE isIncome = :income AND receivedAt BETWEEN :from AND :to")
    suspend fun amountsBetween(from: Long, to: Long, income: Boolean): List<AmountAt>

    @Query("SELECT COUNT(*) FROM seen_messages WHERE hash = :hash")
    suspend fun seenCount(hash: String): Int

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun markSeen(message: SeenMessage)

    @Query("DELETE FROM seen_messages WHERE seenAt < :before")
    suspend fun forgetSeenBefore(before: Long)

    /** Older versions stored whole messages; anything that isn't a 64-char hash gets cleared. */
    @Query("UPDATE sms_suggestions SET body = '' WHERE length(body) != 64")
    suspend fun scrubRawBodies()
}
@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Debt>>

    @Upsert
    suspend fun upsert(debt: Debt)

    @Delete
    suspend fun delete(debt: Debt)

    @Query("SELECT * FROM debts")
    suspend fun getAll(): List<Debt>

    @Query("DELETE FROM debts")
    suspend fun deleteAll()

    @Query("UPDATE debts SET settled = 1 WHERE settled = 0 AND LOWER(TRIM(person)) = LOWER(TRIM(:person))")
    suspend fun settle(person: String)
}

@Dao
interface DeletedDao {
    @Query(
        """
        SELECT d.*, c.name AS categoryName, c.emoji AS categoryEmoji, c.color AS categoryColor
        FROM deleted_transactions d LEFT JOIN categories c ON c.id = d.categoryId
        ORDER BY d.deletedAt DESC
        """,
    )
    fun observeAll(): Flow<List<DeletedItem>>

    @Query("SELECT COUNT(*) FROM deleted_transactions")
    fun count(): Flow<Int>

    @Upsert
    suspend fun upsert(entry: DeletedTransaction)

    @Query("SELECT * FROM deleted_transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DeletedTransaction>

    @Query("SELECT * FROM deleted_transactions WHERE deletedAt < :before")
    suspend fun olderThan(before: Long): List<DeletedTransaction>

    @Query("SELECT * FROM deleted_transactions")
    suspend fun getAll(): List<DeletedTransaction>

    @Query("DELETE FROM deleted_transactions WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

/** Raw table access for backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<Transaction>
    @Query("SELECT * FROM recurring") suspend fun recurring(): List<Recurring>
    @Query("SELECT * FROM goals") suspend fun goals(): List<Goal>
    @Query("SELECT * FROM goal_deposits") suspend fun deposits(): List<GoalDeposit>
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<Account>
    @Query("SELECT * FROM transfers") suspend fun transfers(): List<Transfer>
    @Query("SELECT * FROM debts") suspend fun debts(): List<Debt>

    @Query("DELETE FROM goal_deposits") suspend fun clearDeposits()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM deleted_transactions") suspend fun clearDeleted()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM transfers") suspend fun clearTransfers()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()

    @androidx.room.Insert suspend fun insertCategories(items: List<Category>)
    @androidx.room.Insert suspend fun insertTransactions(items: List<Transaction>)
    @androidx.room.Insert suspend fun insertRecurring(items: List<Recurring>)
    @androidx.room.Insert suspend fun insertGoals(items: List<Goal>)
    @androidx.room.Insert suspend fun insertDeposits(items: List<GoalDeposit>)
    @androidx.room.Insert suspend fun insertAccounts(items: List<Account>)
    @androidx.room.Insert suspend fun insertTransfers(items: List<Transfer>)
    @androidx.room.Insert suspend fun insertDebts(items: List<Debt>)
}