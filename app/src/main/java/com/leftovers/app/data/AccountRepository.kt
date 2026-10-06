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

class SmsRepository(private val dao: SmsDao) {
    val suggestions: Flow<List<SmsSuggestion>> = dao.observeAll()

    /** Stores a detected payment unless the same message was already seen. */
    suspend fun add(suggestion: SmsSuggestion) {
        if (dao.countWithBody(suggestion.body) == 0) dao.insert(suggestion)
    }

    suspend fun dismiss(id: Long) = dao.delete(id)

    suspend fun scrubRawBodies() = dao.scrubRawBodies()
}
