package com.leftovers.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AccountRepository(
    private val accountDao: AccountDao,
    private val transferDao: TransferDao,
    private val transactionDao: TransactionDao,
    private val debtDao: DebtDao,
    /** The rates set for other currencies (code to "one unit in the app's currency"), for accounts with their own. */
    rates: Flow<Map<String, String>> = kotlinx.coroutines.flow.flowOf(emptyMap()),
) {
    val accounts: Flow<List<AccountWithBalance>> =
        combine(accountDao.observeWithBalance(), accountDao.observeNativeParts(), rates) { list, parts, rateMap ->
            val byId = parts.associateBy { it.accountId }
            list.map { a ->
                val code = a.currency ?: return@map a
                a.copy(nativeMinor = nativeBalance(a.openingNative, byId[a.id], rateMap[code]))
            }
        }
    val transfers: Flow<List<Transfer>> = transferDao.observeAll()

    suspend fun saveAccount(account: Account): Long = accountDao.upsert(account)

    /** Deletes the account, or returns how many entries still use it (and deletes nothing). */
    suspend fun deleteAccount(account: Account): Int {
        val used = transactionDao.countForAccount(account.id) + accountDao.transferCount(account.id)
        if (used == 0) {
            debtDao.unlinkAccount(account.id)
            accountDao.delete(account)
        }
        return used
    }

    /** What [moveEntries] moved, so it can be put back. */
    data class Moved(val transactions: List<Long>, val debts: List<Long>) {
        val size: Int get() = transactions.size + debts.size
        fun isEmpty() = size == 0
    }

    /** Re-files every entry of one account, lent and borrowed ones too, under another; returns what moved so it can be undone. */
    suspend fun moveEntries(fromId: Long, toId: Long): Moved {
        val moved = Moved(transactionDao.idsForAccount(fromId), debtDao.idsForAccount(fromId))
        setAccount(moved, toId)
        return moved
    }

    suspend fun setAccount(moved: Moved, accountId: Long) {
        // SQLite caps the number of query parameters, so update in batches.
        moved.transactions.chunked(500).forEach { transactionDao.setAccount(it, accountId) }
        moved.debts.chunked(500).forEach { debtDao.setAccount(it, accountId) }
    }

    suspend fun saveTransfer(transfer: Transfer) = transferDao.upsert(transfer)
    suspend fun deleteTransfer(transfer: Transfer) = transferDao.delete(transfer)
}

class SmsRepository(private val dao: SmsDao, private val transactionDao: TransactionDao) {
    companion object {
        /** How long a seen message is remembered; far longer than any email check looks back. */
        const val SEEN_DAYS = 30L
    }

    val suggestions: Flow<List<SmsSuggestion>> = dao.observeAll()

    /**
     * Stores a detected payment unless it's a repeat: the same message again, or the same amount already
     * suggested or logged around the same time (see [DuplicatePayments]). Returns whether it was added.
     */
    suspend fun add(suggestion: SmsSuggestion): Boolean {
        val hash = suggestion.body
        // Seen before, even if that suggestion was since added or dismissed: email checks look back two days.
        if (hash.isNotEmpty() && (dao.countWithBody(hash) > 0 || dao.seenCount(hash) > 0)) return false
        val from = suggestion.receivedAt - DuplicatePayments.WINDOW_MS
        val to = suggestion.receivedAt + DuplicatePayments.WINDOW_MS
        val logged = if (suggestion.isIncome) transactionDao.incomeAmountsBetween(from, to) else transactionDao.expenseAmountsBetween(from, to)
        val seen = dao.amountsBetween(from, to, suggestion.isIncome) + logged
        if (hash.isNotEmpty()) dao.markSeen(SeenMessage(hash, System.currentTimeMillis()))
        if (DuplicatePayments.isDuplicate(suggestion.amountMinor, suggestion.receivedAt, seen)) return false
        dao.insert(suggestion)
        return true
    }

    /** Forgets seen messages older than any email check looks back. */
    suspend fun forgetOldSeen() = dao.forgetSeenBefore(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(SEEN_DAYS))

    suspend fun dismiss(id: Long) = dao.delete(id)

    /** The account digits a detected payment's message named, if any. */
    suspend fun digitsFor(id: Long): String? = dao.digitsFor(id)

    /** The account a detected payment matched, if any. */
    suspend fun accountIdFor(id: Long): Long? = dao.accountIdFor(id)

    /** Whether a detected payment is money coming in. */
    suspend fun isIncome(id: Long): Boolean = dao.isIncome(id) == true

    suspend fun scrubRawBodies() = dao.scrubRawBodies()
}
