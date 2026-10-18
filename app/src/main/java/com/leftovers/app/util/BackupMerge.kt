package com.leftovers.app.util

import com.leftovers.app.data.Account
import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.Transfer

/**
 * Restoring a backup while keeping what's already in the app: the backup's data is added, and anything the app
 * already has is left alone. Categories, accounts, subscriptions and goals are matched by name, so entries land in
 * the ones the app already has; an entry, transfer, deposit or lent/borrowed record that's already here (same day,
 * amount and note) isn't added twice. New rows get ids after the app's own, so nothing already saved changes.
 */
object BackupMerge {
    data class Data(
        val categories: List<Category> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val transactions: List<Transaction> = emptyList(),
        val transfers: List<Transfer> = emptyList(),
        val recurring: List<Recurring> = emptyList(),
        val goals: List<Goal> = emptyList(),
        val deposits: List<GoalDeposit> = emptyList(),
        val debts: List<Debt> = emptyList(),
    )

    /** What to add, and how many of the backup's entries were already here. */
    data class Plan(val add: Data, val skipped: Int)

    private fun key(s: String) = s.trim().lowercase()

    /** [reservedEntryIds]: ids kept by Deleted entries, so restoring one later can't overwrite an added entry. */
    fun plan(existing: Data, backup: Data, reservedEntryIds: List<Long> = emptyList()): Plan {
        var skipped = 0
        fun nextIds(ids: List<Long>) = generateSequence((ids.maxOrNull() ?: 0L) + 1) { it + 1 }.iterator()

        // Categories: the same name and kind is the same category.
        val categoryIds = nextIds(existing.categories.map { it.id })
        val newCategories = mutableListOf<Category>()
        val categoryMap = backup.categories.associate { c ->
            val match = (existing.categories + newCategories).firstOrNull { key(it.name) == key(c.name) && it.type == c.type }
            c.id to (match?.id ?: c.copy(id = categoryIds.next()).also(newCategories::add).id)
        }

        val accountIds = nextIds(existing.accounts.map { it.id })
        val newAccounts = mutableListOf<Account>()
        val accountMap = backup.accounts.associate { a ->
            val match = (existing.accounts + newAccounts).firstOrNull { key(it.name) == key(a.name) }
            a.id to (match?.id ?: a.copy(id = accountIds.next()).also(newAccounts::add).id)
        }
        fun account(id: Long?) = id?.let { accountMap[it] ?: it.takeIf { existingId -> existing.accounts.any { e -> e.id == existingId } } }

        val haveEntries = existing.transactions.map { listOf(it.epochDay, it.amountMinor, it.type, key(it.note)) }.toMutableSet()
        val transactionIds = nextIds(existing.transactions.map { it.id } + reservedEntryIds)
        val newTransactions = backup.transactions.mapNotNull { t ->
            val category = categoryMap[t.categoryId] ?: return@mapNotNull null
            if (!haveEntries.add(listOf(t.epochDay, t.amountMinor, t.type, key(t.note)))) {
                skipped++
                return@mapNotNull null
            }
            t.copy(id = transactionIds.next(), categoryId = category, accountId = account(t.accountId))
        }

        val haveTransfers = existing.transfers.map { listOf(it.epochDay, it.amountMinor, it.fromAccountId, it.toAccountId) }.toMutableSet()
        val transferIds = nextIds(existing.transfers.map { it.id })
        val newTransfers = backup.transfers.mapNotNull { t ->
            val from = account(t.fromAccountId) ?: return@mapNotNull null
            val to = account(t.toAccountId) ?: return@mapNotNull null
            if (!haveTransfers.add(listOf(t.epochDay, t.amountMinor, from, to))) return@mapNotNull null
            t.copy(id = transferIds.next(), fromAccountId = from, toAccountId = to)
        }

        // A subscription the app already has (same name and kind) isn't added again.
        val recurringIds = nextIds(existing.recurring.map { it.id })
        val newRecurring = backup.recurring.mapNotNull { r ->
            if (existing.recurring.any { key(it.name) == key(r.name) && it.type == r.type }) return@mapNotNull null
            val category = categoryMap[r.categoryId] ?: return@mapNotNull null
            r.copy(id = recurringIds.next(), categoryId = category, accountId = account(r.accountId))
        }

        // Goals: a goal with the same name gets the backup's deposits it doesn't already have.
        val goalIds = nextIds(existing.goals.map { it.id })
        val newGoals = mutableListOf<Goal>()
        val goalMap = backup.goals.associate { g ->
            val match = (existing.goals + newGoals).firstOrNull { key(it.name) == key(g.name) }
            g.id to (match?.id ?: g.copy(id = goalIds.next()).also(newGoals::add).id)
        }
        val haveDeposits = existing.deposits.map { listOf(it.goalId, it.epochDay, it.amountMinor) }.toMutableSet()
        val depositIds = nextIds(existing.deposits.map { it.id })
        val newDeposits = backup.deposits.mapNotNull { d ->
            val goal = goalMap[d.goalId] ?: return@mapNotNull null
            if (!haveDeposits.add(listOf(goal, d.epochDay, d.amountMinor))) return@mapNotNull null
            d.copy(id = depositIds.next(), goalId = goal)
        }

        val haveDebts = existing.debts.map { listOf(key(it.person), it.epochDay, it.amountMinor, key(it.note)) }.toMutableSet()
        val debtIds = nextIds(existing.debts.map { it.id })
        val newDebts = backup.debts.mapNotNull { d ->
            if (!haveDebts.add(listOf(key(d.person), d.epochDay, d.amountMinor, key(d.note)))) return@mapNotNull null
            d.copy(id = debtIds.next(), accountId = account(d.accountId))
        }

        return Plan(Data(newCategories, newAccounts, newTransactions, newTransfers, newRecurring, newGoals, newDeposits, newDebts), skipped)
    }
}
