package com.leftovers.desktop.data

import com.leftovers.app.data.Account
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.DeletedTransaction
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import com.leftovers.app.util.SyncRemoved

/** How the app looks and behaves on this computer: kept with the data, but never sent to the phone. */
data class Prefs(
    val theme: Theme = Theme.SYSTEM,
    /** Frosted glass surfaces, like the phone's; off is the calm, flat look. */
    val glass: Boolean = false,
    val splitsAsOneBill: Boolean = true,
    val showNewDailyLimit: Boolean = false,
    /** The account new entries go to. */
    val defaultAccountId: Long = 0,
    /** The account Home shows, or null for all of them. */
    val homeAccountId: Long? = null,
    val deletedKeepDays: Int = 90,
    /** Set up: the currency was chosen, or a backup was opened. */
    val onboarded: Boolean = false,
    val recapSeen: String = "",
    val whatsNewSeen: String = "",
    /** Sync: a shared folder, or a WebDAV server, and the name this computer goes by there. */
    val syncDir: String? = null,
    val syncServer: String? = null,
    val syncUser: String? = null,
    val syncDevice: String? = null,
    val syncLast: Long = 0,
    /** Exchange rates the user set for accounts in other currencies: "AED" to how much one is in the app's. */
    val rates: Map<String, String> = emptyMap(),
)

enum class Theme { SYSTEM, LIGHT, DARK }

/** Everything Leftovers keeps: the same records as the phone's database, in the same shapes. */
data class Book(
    val currency: String = "USD",
    val plan: BudgetPlan = BudgetPlan(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val deposits: List<GoalDeposit> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val deleted: List<DeletedTransaction> = emptyList(),
    /** Entries deleted or changed here, so Sync takes them off the other devices too. */
    val removed: List<SyncRemoved.Removed> = emptyList(),
    /** Category pictures the user made on the phone, by file name, as base64 PNG. */
    val icons: Map<String, String> = emptyMap(),
    val prefs: Prefs = Prefs(),
) {
    companion object {
        /** A fresh start: the phone's starting categories and accounts. */
        fun starter(currency: String = "USD"): Book {
            var id = 0L
            return Book(
                currency = currency,
                categories = DefaultCategories.map { it.copy(id = ++id) },
                accounts = listOf(
                    Account(id = 1, name = "Cash", icon = "banknote", color = Palette.MINT, position = 0),
                    Account(id = 2, name = "Bank", icon = "landmark", color = Palette.SKY, position = 1),
                ),
                prefs = Prefs(defaultAccountId = 1),
            )
        }
    }
}

/** The phone's colours for categories, accounts and goals. */
object Palette {
    const val PEACH = 0xFFF4A68AL
    const val AMBER = 0xFFF2C46DL
    const val LIME = 0xFFC8E07AL
    const val MINT = 0xFF7FD9A8L
    const val TEAL = 0xFF6FD0D0L
    const val SKY = 0xFF7DB7F5L
    const val IRIS = 0xFF9C9CF8L
    const val LILAC = 0xFFC59AF2L
    const val ROSE = 0xFFF291B4L
    const val CORAL = 0xFFF27E7EL
    const val SAND = 0xFFD9B99BL
    const val SLATE = 0xFFA3AEBDL

    val all = listOf(PEACH, AMBER, LIME, MINT, TEAL, SKY, IRIS, LILAC, ROSE, CORAL, SAND, SLATE)
}

val DefaultCategories = listOf(
    Category(name = "Food & Dining", emoji = "utensils", color = Palette.PEACH, type = TxType.EXPENSE),
    Category(name = "Groceries", emoji = "shopping-cart", color = Palette.MINT, type = TxType.EXPENSE),
    Category(name = "Transport", emoji = "taxi", color = Palette.SKY, type = TxType.EXPENSE),
    Category(name = "Shopping", emoji = "shopping-bag", color = Palette.ROSE, type = TxType.EXPENSE),
    Category(name = "Bills & Utilities", emoji = "receipt", color = Palette.AMBER, type = TxType.EXPENSE),
    Category(name = "Rent", emoji = "house", color = Palette.SAND, type = TxType.EXPENSE),
    Category(name = "Entertainment", emoji = "clapperboard", color = Palette.LILAC, type = TxType.EXPENSE),
    Category(name = "Health", emoji = "heart-pulse", color = Palette.CORAL, type = TxType.EXPENSE),
    Category(name = "Education", emoji = "graduation-cap", color = Palette.IRIS, type = TxType.EXPENSE),
    Category(name = "Travel", emoji = "plane", color = Palette.TEAL, type = TxType.EXPENSE),
    Category(name = "Gifts", emoji = "gift", color = Palette.LIME, type = TxType.EXPENSE),
    Category(name = "Other", emoji = "package", color = Palette.SLATE, type = TxType.EXPENSE),
    Category(name = "Salary", emoji = "briefcase", color = Palette.MINT, type = TxType.INCOME),
    Category(name = "Freelance", emoji = "laptop", color = Palette.SKY, type = TxType.INCOME, addsToBudget = true),
    Category(name = "Investments", emoji = "trending-up", color = Palette.LIME, type = TxType.INCOME, addsToBudget = true),
    Category(name = "Gifts", emoji = "party", color = Palette.AMBER, type = TxType.INCOME, addsToBudget = true),
    Category(name = "Other", emoji = "coins", color = Palette.LILAC, type = TxType.INCOME, addsToBudget = true),
)
