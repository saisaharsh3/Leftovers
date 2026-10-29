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
import com.leftovers.app.data.DeletedTransaction
import com.leftovers.app.data.photosToPath
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
 * and restores from it. Photos are included only when "Include photos" is on (Settings). With a backup password set, the file is
 * encrypted (see [BackupCrypto]).
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    val password: BackupPassword,
) {
    private val dao: BackupDao = database.backupDao()

    private companion object {
        const val MAX_PHOTO_BYTES = 40L * 1024 * 1024
    }

    /** [removed]: for Sync between devices, the entries this phone deleted or changed, so the others remove them too. */
    suspend fun export(target: Uri, removed: List<SyncRemoved.Removed> = emptyList()): Result<Int> =
        exportBytes(removed).mapCatching { (bytes, count) ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) } ?: error("Couldn't open the file")
            }
            count
        }

    /** The backup file's contents, and how many entries are in it. */
    suspend fun exportBytes(removed: List<SyncRemoved.Removed> = emptyList()): Result<Pair<ByteArray, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = dao.transactions()
            val s = settings.settings.first()
            val debtList = dao.debts()
            // With "Include photos" on, each photo goes in once, by file name; entries point to it. Past
            // MAX_PHOTO_BYTES in total the photos are left out, so a large collection can't run the phone out of memory.
            val photoFiles = (transactions.mapNotNull { it.receiptPath } + debtList.flatMap { it.photos })
                .distinct().map { java.io.File(it) }.filter { it.exists() }
            val withPhotos = s.backupPhotos && photoFiles.sumOf { it.length() } <= MAX_PHOTO_BYTES
            val photos = JSONObject()
            fun photoRef(path: String?): Any {
                if (!withPhotos || path == null) return JSONObject.NULL
                val file = java.io.File(path)
                if (!file.exists()) return JSONObject.NULL
                if (!photos.has(file.name)) photos.put(file.name, java.util.Base64.getEncoder().encodeToString(file.readBytes()))
                return file.name
            }
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
                    put("extraIncome", s.plan.extraIncome)
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
                        .put("opening", a.openingMinor).put("createdAt", a.createdAt).put("smsDigits", a.smsDigits ?: JSONObject.NULL)
                        .put("currency", a.currency ?: JSONObject.NULL).put("openingNative", a.openingNative ?: JSONObject.NULL)
                        .put("outOfBudget", a.outOfBudget).put("outOfTotal", a.outOfTotal)
                }))
                put("transactions", JSONArray(transactions.map { t ->
                    JSONObject().put("id", t.id).put("amount", t.amountMinor).put("type", t.type.name)
                        .put("categoryId", t.categoryId).put("day", t.epochDay).put("note", t.note)
                        .put("createdAt", t.createdAt).put("accountId", t.accountId ?: JSONObject.NULL)
                        .put("receipt", photoRef(t.receiptPath))
                        .put("foreignAmount", t.foreignMinor ?: JSONObject.NULL).put("foreignCurrency", t.foreignCurrency ?: JSONObject.NULL)
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
                        .put("link", g.link ?: JSONObject.NULL).put("linkPrice", g.linkPriceMinor ?: JSONObject.NULL)
                        .put("linkCheckedAt", g.linkCheckedAt ?: JSONObject.NULL)
                }))
                put("deposits", JSONArray(dao.deposits().map { d ->
                    JSONObject().put("id", d.id).put("goalId", d.goalId).put("amount", d.amountMinor)
                        .put("day", d.epochDay).put("createdAt", d.createdAt)
                }))
                put("debts", JSONArray(debtList.map { d ->
                    JSONObject().put("id", d.id).put("person", d.person).put("amount", d.amountMinor).put("note", d.note)
                        .put("day", d.epochDay).put("settled", d.settled).put("createdAt", d.createdAt)
                        .put("account", d.accountId ?: JSONObject.NULL).put("repayment", d.repayment)
                        .put("receipts", JSONArray(d.photos.map { photoRef(it) }.filter { it != JSONObject.NULL }))
                }))
                if (photos.length() > 0) put("photos", photos)
                if (removed.isNotEmpty()) put("removed", SyncRemoved.encode(removed))
            }
            val text = password.get()?.let { BackupCrypto.encrypt(json.toString(), it).toString(2) } ?: json.toString(2)
            text.toByteArray() to transactions.size
        }
    }

    /** What a restore did: entries brought in, and (keeping what's here) how many were already in the app. */
    data class Restored(val entries: Int, val alreadyHere: Int = 0, val removed: Int = 0)

    /**
     * Replaces all data with the backup's contents, or with [keep], adds the backup's data to what's already here
     * (see [BackupMerge]) and leaves the settings as they are. A protected backup is opened with [typedPassword],
     * or else the saved backup password; without the right one it fails with [BackupPasswordException].
     */
    /**
     * With [sync] (another phone's sync file, always kept), entries that phone deleted or changed are removed here
     * too (into Deleted entries, so they can be brought back), and entries deleted here, listed in [mine], aren't added back.
     */
    suspend fun import(
        source: Uri,
        typedPassword: String? = null,
        keep: Boolean = false,
        sync: Boolean = false,
        mine: List<SyncRemoved.Removed> = emptyList(),
    ): Result<Restored> = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: error("Couldn't open the file") }
            .fold({ importBytes(it, typedPassword, keep, sync, mine) }, { Result.failure(it) })
    }

    /** [import], from a backup file's contents. */
    suspend fun importBytes(
        bytes: ByteArray,
        typedPassword: String? = null,
        keep: Boolean = false,
        sync: Boolean = false,
        mine: List<SyncRemoved.Removed> = emptyList(),
    ): Result<Restored> = withContext(Dispatchers.IO) {
        runCatching {
            // A spreadsheet or another app's export picked here by mistake: say so, rather than a JSON error.
            val text = StatementFile.decodeText(bytes).trimStart()
            if (!text.startsWith("{")) throw NotABackupException()
            var json = runCatching { JSONObject(text) }.getOrElse { throw NotABackupException() }
            if (BackupCrypto.isEncrypted(json)) {
                val key = typedPassword ?: password.get() ?: throw BackupPasswordException("This backup has a password")
                json = JSONObject(BackupCrypto.decrypt(json, key))
            }
            // "cash-tracker" is the app's previous name; keep accepting those backups.
            // Another app's JSON backup: its entries can still be imported (see StatementFile).
            if (json.optString("app") !in setOf("leftovers", "cash-tracker")) throw NotABackupException()

            fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
            // Photos saved in the backup, written back to private storage on demand.
            val savedPhotos = json.optJSONObject("photos")
            val restoredPhotos = mutableMapOf<String, String>()
            fun restorePhoto(name: String): String? {
                if (name.isEmpty() || name == "null") return null
                restoredPhotos[name]?.let { return it }
                val data = savedPhotos?.optString(name)?.takeIf { it.isNotEmpty() } ?: return null
                return ReceiptStore.restore(context, java.util.Base64.getDecoder().decode(data)).also { restoredPhotos[name] = it }
            }
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
                Account(
                    it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getLong("opening"), it.getLong("createdAt"), it.stringOrNull("smsDigits"),
                    currency = it.stringOrNull("currency"), openingNative = it.longOrNull("openingNative"),
                    outOfBudget = it.optBoolean("outOfBudget"), outOfTotal = it.optBoolean("outOfTotal"),
                )
            }
            val transactions = json.getJSONArray("transactions").objects().map {
                Transaction(
                    it.getLong("id"), it.getLong("amount"), TxType.valueOf(it.getString("type")), it.getLong("categoryId"),
                    it.getLong("day"), it.getString("note"), it.getLong("createdAt"), it.longOrNull("accountId"), restorePhoto(it.optString("receipt")),
                    it.longOrNull("foreignAmount"), it.stringOrNull("foreignCurrency"),
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
                Goal(
                    it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getLong("target"), it.getString("targetMonth"), it.getLong("createdAt"),
                    link = it.stringOrNull("link"), linkPriceMinor = it.longOrNull("linkPrice"), linkCheckedAt = it.longOrNull("linkCheckedAt"),
                )
            }
            val deposits = json.optJSONArray("deposits")?.objects().orEmpty().map {
                GoalDeposit(it.getLong("id"), it.getLong("goalId"), it.getLong("amount"), it.getLong("day"), it.getLong("createdAt"))
            }
            val debts = json.optJSONArray("debts")?.objects().orEmpty().map {
                // "receipts" holds several photos; older backups had one "receipt".
                val names = it.optJSONArray("receipts")?.let { a -> (0 until a.length()).map(a::getString) } ?: listOf(it.optString("receipt"))
                Debt(
                    it.getLong("id"), it.getString("person"), it.getLong("amount"), it.optString("note"), it.getLong("day"),
                    it.optBoolean("settled"), it.getLong("createdAt"), photosToPath(names.mapNotNull(::restorePhoto)),
                    // Older backups have no account: those entries stay out of every balance, as before.
                    accountId = if (it.isNull("account")) null else it.optLong("account"),
                    repayment = it.optBoolean("repayment"),
                )
            }

            if (keep || sync) {
                // Newest removal of each entry: only entries made before it count as that entry.
                fun latest(list: List<SyncRemoved.Removed>): Map<List<Any>, Long> = list.groupBy { listOf<Any>(it.day, it.amount, it.type, SyncRemoved.key(it.note)) }
                    .mapValues { (_, same) -> same.maxOf { it.at } }
                fun Map<List<Any>, Long>.has(t: Transaction) =
                    get(listOf<Any>(t.epochDay, t.amountMinor, t.type, SyncRemoved.key(t.note)))?.let { t.createdAt <= it } == true
                val theirList = if (sync) SyncRemoved.decode(json.optJSONArray("removed")) else emptyList()
                val theirs = latest(theirList)
                val gone = latest(theirList + if (sync) mine else emptyList())
                var removedHere = 0
                val plan = database.withTransaction {
                    if (theirs.isNotEmpty()) {
                        val drop = dao.transactions().filter { theirs.has(it) }
                        val at = System.currentTimeMillis()
                        drop.forEach { dao.insertDeleted(DeletedTransaction.from(it, at)) }
                        dao.deleteTransactions(drop)
                        removedHere = drop.size
                    }
                    val existing = BackupMerge.Data(
                        dao.categories(), dao.accounts(), dao.transactions(), dao.transfers(), dao.recurring(), dao.goals(), dao.deposits(), dao.debts(),
                    )
                    val plan = BackupMerge.plan(existing, BackupMerge.Data(categories, accounts, transactions.filterNot { gone.has(it) }, transfers, recurring, goals, deposits, debts), dao.deletedIds())
                    with(plan.add) {
                        dao.insertCategories(this.categories)
                        dao.insertAccounts(this.accounts)
                        dao.insertTransactions(this.transactions)
                        dao.insertTransfers(this.transfers)
                        dao.insertRecurring(this.recurring)
                        dao.insertGoals(this.goals)
                        dao.insertDeposits(this.deposits)
                        dao.insertDebts(this.debts)
                    }
                    plan
                }
                // Photos of entries that were already here were written out for nothing.
                val used = (plan.add.transactions.mapNotNull { it.receiptPath } + plan.add.debts.flatMap { it.photos }).toSet()
                restoredPhotos.values.filter { it !in used }.forEach(ReceiptStore::delete)
                return@runCatching Restored(plan.add.transactions.size, plan.skipped, removedHere)
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
            // Photos that belonged to the replaced data are no longer used.
            ReceiptStore.deleteAllExcept(context, (transactions.mapNotNull { it.receiptPath } + debts.flatMap { it.photos }).toSet())

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
                        extraIncome = s.optBoolean("extraIncome", false),
                        carryChoices = BudgetPlan.decodeChoices(s.optString("carryChoices")),
                        carryFrom = s.optString("carryFrom").takeIf { it.isNotEmpty() }?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                    ),
                )
            }
            Restored(transactions.size)
        }
    }
}

/** The file picked to restore isn't a Leftovers backup, e.g. a bank statement or another app's export. */
class NotABackupException : Exception("This isn't a Leftovers backup")
