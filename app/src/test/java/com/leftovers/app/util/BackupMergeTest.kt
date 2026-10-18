package com.leftovers.app.util

import com.leftovers.app.data.Account
import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Restoring a backup while keeping what's already in the app. */
class BackupMergeTest {
    private val food = Category(1, "Food", "utensils", 0, TxType.EXPENSE)
    private val cash = Account(1, "Cash", "banknote", 0)

    private val here = BackupMerge.Data(
        categories = listOf(food),
        accounts = listOf(cash),
        transactions = listOf(Transaction(1, 25_000, TxType.EXPENSE, 1, 20_000, "Lunch", accountId = 1)),
        goals = listOf(Goal(1, "Goa trip", "plane", 0, 3_000_000, "2027-01")),
        deposits = listOf(GoalDeposit(1, 1, 500_000, 20_000)),
    )

    // The same things under the backup's own ids, plus new ones.
    private val backup = BackupMerge.Data(
        categories = listOf(Category(7, "food", "utensils", 0, TxType.EXPENSE), Category(8, "Gifts", "gift", 0, TxType.INCOME)),
        accounts = listOf(Account(5, "Cash", "banknote", 0), Account(6, "HDFC Bank", "landmark", 0)),
        transactions = listOf(
            Transaction(1, 25_000, TxType.EXPENSE, 7, 20_000, "lunch ", accountId = 5), // already here
            Transaction(2, 9_900, TxType.EXPENSE, 7, 20_001, "Coffee", accountId = 6),
            Transaction(3, 100_000, TxType.INCOME, 8, 20_002, "Birthday", accountId = 6),
        ),
        recurring = listOf(Recurring(4, "Netflix", 64_900, TxType.EXPENSE, 7, 12, "2026-01")),
        goals = listOf(Goal(9, "Goa Trip", "plane", 0, 3_000_000, "2027-01"), Goal(10, "New phone", "smartphone", 0, 6_000_000, "2027-06")),
        deposits = listOf(GoalDeposit(3, 9, 500_000, 20_000), GoalDeposit(4, 9, 200_000, 20_010), GoalDeposit(5, 10, 100_000, 20_011)),
        debts = listOf(Debt(2, "Nick", 30_000, "Cab", 20_003, accountId = 6)),
    )

    private val plan = BackupMerge.plan(here, backup, reservedEntryIds = listOf(40))

    @Test fun entriesAlreadyHereArentAddedTwice() {
        assertEquals(1, plan.skipped)
        assertEquals(listOf("Coffee", "Birthday"), plan.add.transactions.map { it.note })
    }

    @Test fun matchingCategoriesAndAccountsAreReused() {
        assertEquals(listOf("Gifts"), plan.add.categories.map { it.name })
        assertEquals(listOf("HDFC Bank"), plan.add.accounts.map { it.name })
        val coffee = plan.add.transactions.first()
        assertEquals(1L, coffee.categoryId) // the app's own Food
        assertEquals(plan.add.accounts.single().id, coffee.accountId)
    }

    @Test fun newRowsGetIdsAfterTheAppsOwnAndDeletedEntries() {
        assertTrue(plan.add.categories.all { it.id > 1 })
        // Deleted entries keep id 40, so added entries start after it.
        assertEquals(listOf(41L, 42L), plan.add.transactions.map { it.id })
    }

    @Test fun goalsMergeByNameWithoutDuplicateDeposits() {
        assertEquals(listOf("New phone"), plan.add.goals.map { it.name })
        // Goa trip's 5,000 deposit was already here; its 2,000 one is new, and goes to the app's Goa trip.
        assertEquals(listOf(1L to 200_000L, plan.add.goals.single().id to 100_000L), plan.add.deposits.map { it.goalId to it.amountMinor })
    }

    @Test fun subscriptionsAndMoneyOwedComeAcross() {
        assertEquals(listOf("Netflix"), plan.add.recurring.map { it.name })
        assertEquals(1L, plan.add.recurring.single().categoryId)
        assertEquals(plan.add.accounts.single().id, plan.add.debts.single().accountId)
    }

    @Test fun restoringTheSameBackupTwiceAddsNothing() {
        val once = BackupMerge.plan(here, backup).add
        val after = BackupMerge.Data(
            here.categories + once.categories, here.accounts + once.accounts, here.transactions + once.transactions, once.transfers,
            once.recurring, here.goals + once.goals, here.deposits + once.deposits, once.debts,
        )
        val twice = BackupMerge.plan(after, backup)
        assertEquals(0, twice.add.transactions.size + twice.add.categories.size + twice.add.goals.size + twice.add.deposits.size + twice.add.debts.size + twice.add.recurring.size)
    }
}
