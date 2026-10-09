package com.leftovers.app.data

import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val accountDao: AccountDao,
    private val transferDao: TransferDao,
    private val transactionDao: TransactionDao,
) {
    val accounts: Flow<List<AccountWithBalance>> = accountDao.observeWithBalance()
    val transfers: Flow<List<Transfer>> = transferDao.observeAll()

    suspend fun saveAccount(account: Account): Long = accountDao.upsert(account)

    /** Deletes the account, or returns how many entries still use it (and deletes nothing). */
    suspend fun deleteAccount(account: Account): Int {
        val used = transactionDao.countForAccount(account.id) + accountDao.transferCount(account.id)
        if (used == 0) accountDao.delete(account)
        return used
    }

    /** Re-files every entry of one account under another; returns the moved ids so it can be undone. */
    suspend fun moveEntries(fromId: Long, toId: Long): List<Long> {
        val ids = transactionDao.idsForAccount(fromId)
        setAccount(ids, toId)
        return ids
    }

    suspend fun setAccount(ids: List<Long>, accountId: Long) {
        // SQLite caps the number of query parameters, so update in batches.
        ids.chunked(500).forEach { transactionDao.setAccount(it, accountId) }
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

    /** Whether a detected payment is money coming in. */
    suspend fun isIncome(id: Long): Boolean = dao.isIncome(id) == true

    suspend fun scrubRawBodies() = dao.scrubRawBodies()
}
