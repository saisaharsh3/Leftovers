package com.leftovers.app.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.leftovers.app.data.Account
import com.leftovers.app.data.AppDatabase
import com.leftovers.app.data.BackupDao
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves everything to a single JSON file the user picks (local storage, Google Drive, …)
 * and restores from it. Receipt photos are not included. With a backup password set, the file is
 * encrypted (see [BackupCrypto]).
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    val password: BackupPassword,
) {
    private val dao: BackupDao = database.backupDao()

    suspend fun export(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = dao.transactions()
            val s = settings.settings.first()
            val json = JSONObject().apply {
                put("app", "leftovers")
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("settings", JSONObject().apply {
                    put("currency", s.currencyCode)
                    put("budgetMode", s.plan.mode.name)
                    put("monthly", s.plan.monthlyMinor)
                    put("yearly", s.plan.yearlyMinor)
                    put("split", s.plan.split.name)
                    put("custom", BudgetPlan.encodeCustom(s.plan.custom))
                    put("carryMode", s.plan.carry.name)
                    put("carryChoices", BudgetPlan.encodeChoices(s.plan.carryChoices))
                    s.plan.carryFrom?.let { put("carryFrom", it.toString()) }
                })
                put("categories", JSONArray(dao.categories().map { c ->
                    JSONObject().put("id", c.id).put("name", c.name).put("icon", c.emoji).put("color", c.color)
                        .put("type", c.type.name).put("budget", c.budgetMinor ?: JSONObject.NULL)
                        .put("addsToBudget", c.addsToBudget)
                }))
                put("accounts", JSONArray(dao.accounts().map { a ->
                    JSONObject().put("id", a.id).put("name", a.name).put("icon", a.icon).put("color", a.color)
                        .put("opening", a.openingMinor).put("createdAt", a.createdAt)
                }))
                put("transactions", JSONArray(transactions.map { t ->
                    JSONObject().put("id", t.id).put("amount", t.amountMinor).put("type", t.type.name)
                        .put("categoryId", t.categoryId).put("day", t.epochDay).put("note", t.note)
                        .put("createdAt", t.createdAt).put("accountId", t.accountId ?: JSONObject.NULL)
                }))
                put("transfers", JSONArray(dao.transfers().map { t ->
                    JSONObject().put("id", t.id).put("from", t.fromAccountId).put("to", t.toAccountId).put("amount", t.amountMinor)
                        .put("day", t.epochDay).put("note", t.note).put("createdAt", t.createdAt)
                }))
                put("recurring", JSONArray(dao.recurring().map { r ->
                    JSONObject().put("id", r.id).put("name", r.name).put("amount", r.amountMinor).put("type", r.type.name)
                        .put("categoryId", r.categoryId).put("day", r.dayOfMonth).put("start", r.startMonth)
                        .put("lastPosted", r.lastPostedMonth ?: JSONObject.NULL).put("active", r.active)
                        .put("every", r.everyMonths).put("account", r.accountId ?: JSONObject.NULL)
                }))
                put("goals", JSONArray(dao.goals().map { g ->
                    JSONObject().put("id", g.id).put("name", g.name).put("icon", g.emoji).put("color", g.color)
                        .put("target", g.targetMinor).put("targetMonth", g.targetMonth).put("createdAt", g.createdAt)
                }))
                put("deposits", JSONArray(dao.deposits().map { d ->
                    JSONObject().put("id", d.id).put("goalId", d.goalId).put("amount", d.amountMinor)
                        .put("day", d.epochDay).put("createdAt", d.createdAt)
                }))
                put("debts", JSONArray(dao.debts().map { d ->
                    JSONObject().put("id", d.id).put("person", d.person).put("amount", d.amountMinor).put("note", d.note)
                        .put("day", d.epochDay).put("settled", d.settled).put("createdAt", d.createdAt)
                }))
            }
            val text = password.get()?.let { BackupCrypto.encrypt(json.toString(), it).toString(2) } ?: json.toString(2)
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open the file")
            transactions.size
        }
    }

    /**
     * Replaces all data with the backup's contents. A protected backup is opened with [typedPassword],
     * or else the saved backup password; without the right one it fails with [BackupPasswordException].
     */
    suspend fun import(source: Uri, typedPassword: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open the file")
            var json = JSONObject(text)
            if (BackupCrypto.isEncrypted(json)) {
                val key = typedPassword ?: password.get() ?: throw BackupPasswordException("This backup has a password")
                json = JSONObject(BackupCrypto.decrypt(json, key))
            }
            // "cash-tracker" is the app's previous name; keep accepting those backups.
            require(json.optString("app") in setOf("leftovers", "cash-tracker")) { "This isn't a Leftovers backup" }

            fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
            fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
            fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

            val categories = json.getJSONArray("categories").objects().map {
                val type = TxType.valueOf(it.getString("type"))
                val name = it.getString("name")
                // Older backups predate the flag: use the same default as the database migration.
                val adds = it.optBoolean("addsToBudget", type == TxType.INCOME && name != "Salary")
                Category(it.getLong("id"), name, it.getString("icon"), it.getLong("color"), type, it.longOrNull("budget"), adds)
            }
            val accounts = json.optJSONArray("accounts")?.objects().orEmpty().map {
                Account(it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getLong("opening"), it.getLong("createdAt"))
            }
            val transactions = json.getJSONArray("transactions").objects().map {
                Transaction(
                    it.getLong("id"), it.getLong("amount"), TxType.valueOf(it.getString("type")), it.getLong("categoryId"),
                    it.getLong("day"), it.getString("note"), it.getLong("createdAt"), it.longOrNull("accountId"), null,
                )
            }
            val transfers = json.optJSONArray("transfers")?.objects().orEmpty().map {
                Transfer(it.getLong("id"), it.getLong("from"), it.getLong("to"), it.getLong("amount"), it.getLong("day"), it.getString("note"), it.getLong("createdAt"))
            }
            val recurring = json.optJSONArray("recurring")?.objects().orEmpty().map {
                Recurring(
                    it.getLong("id"), it.getString("name"), it.getLong("amount"), TxType.valueOf(it.getString("type")),
                    it.getLong("categoryId"), it.getInt("day"), it.getString("start"), it.stringOrNull("lastPosted"), it.getBoolean("active"),
                    it.optInt("every", 1),
                    it.longOrNull("account"),
                )
            }
            val goals = json.optJSONArray("goals")?.objects().orEmpty().map {
                Goal(it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getLong("target"), it.getString("targetMonth"), it.getLong("createdAt"))
            }
            val deposits = json.optJSONArray("deposits")?.objects().orEmpty().map {
                GoalDeposit(it.getLong("id"), it.getLong("goalId"), it.getLong("amount"), it.getLong("day"), it.getLong("createdAt"))
            }
            val debts = json.optJSONArray("debts")?.objects().orEmpty().map {
                Debt(it.getLong("id"), it.getString("person"), it.getLong("amount"), it.optString("note"), it.getLong("day"), it.optBoolean("settled"), it.getLong("createdAt"))
            }

            database.withTransaction {
                dao.clearDebts()
                // Deleted entries belong to the data being replaced.
                dao.clearDeleted()
                dao.clearDeposits()
                dao.clearGoals()
                dao.clearTransfers()
                dao.clearTransactions()
                dao.clearRecurring()
                dao.clearAccounts()
                dao.clearCategories()
                dao.insertCategories(categories)
                dao.insertAccounts(accounts)
                dao.insertTransactions(transactions)
                dao.insertTransfers(transfers)
                dao.insertRecurring(recurring)
                dao.insertGoals(goals)
                dao.insertDeposits(deposits)
                dao.insertDebts(debts)
            }

            json.optJSONObject("settings")?.let { s ->
                settings.setCurrency(s.getString("currency"))
                settings.savePlan(
                    BudgetPlan(
                        mode = com.leftovers.app.data.BudgetMode.valueOf(s.getString("budgetMode")),
                        monthlyMinor = s.getLong("monthly"),
                        yearlyMinor = s.getLong("yearly"),
                        split = com.leftovers.app.data.YearSplit.valueOf(s.getString("split")),
                        custom = BudgetPlan.decodeCustom(s.optString("custom")),
                        carry = com.leftovers.app.data.CarryMode.entries.firstOrNull { it.name == s.optString("carryMode") } ?: com.leftovers.app.data.CarryMode.ASK,
                        carryChoices = BudgetPlan.decodeChoices(s.optString("carryChoices")),
                        carryFrom = s.optString("carryFrom").takeIf { it.isNotEmpty() }?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                    ),
                )
            }
            transactions.size
        }
    }
}
