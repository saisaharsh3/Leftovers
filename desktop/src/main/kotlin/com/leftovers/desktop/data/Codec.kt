package com.leftovers.desktop.data

import com.leftovers.app.data.Account
import com.leftovers.app.data.BudgetMode
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.CarryMode
import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.DeletedTransaction
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import com.leftovers.app.data.YearSplit
import com.leftovers.app.data.photosToPath
import com.leftovers.app.util.BackupCrypto
import com.leftovers.app.util.SyncRemoved
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Base64

/** A backup file needs its password, or the one given was wrong. */
class PasswordNeeded(val wrong: Boolean) : Exception()
class NotABackup : Exception()

/**
 * The phone's backup format, field for field (see the phone's BackupManager): a backup made on either opens on
 * the other. The desktop's own data file is this format too, with two more keys the phone ignores: "prefs" and
 * "deleted".
 */
object Codec {
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    fun encode(book: Book, withPhotos: Boolean, forThisComputer: Boolean, now: Long = System.currentTimeMillis()): JSONObject {
        val photos = JSONObject()
        fun photoRef(path: String?): Any {
            if (!withPhotos || path == null) return JSONObject.NULL
            val file = File(path)
            if (!file.exists()) return JSONObject.NULL
            if (!photos.has(file.name)) photos.put(file.name, Base64.getEncoder().encodeToString(file.readBytes()))
            return file.name
        }
        val p = book.plan
        return JSONObject().apply {
            put("app", "leftovers")
            put("version", 1)
            put("exportedAt", now)
            put("settings", JSONObject().apply {
                put("currency", book.currency)
                put("budgetMode", p.mode.name)
                put("monthly", p.monthlyMinor)
                put("yearly", p.yearlyMinor)
                put("split", p.split.name)
                put("custom", BudgetPlan.encodeCustom(p.custom))
                put("carryMode", p.carry.name)
                put("extraIncome", p.extraIncome)
                put("startDay", p.startDay)
                put("rangeBudget", p.rangeMinor)
                put("buckets", p.buckets)
                put("bucketIncome", p.bucketIncome)
                put("bucketPicker", p.bucketPicker)
                put("periodBudget", p.periodMinor)
                put("periodDays", p.periodDays)
                p.periodFrom?.let { put("periodFrom", it.toString()) }
                p.rangeTag?.let { put("rangeTag", it) }
                p.rangeAccount?.let { put("rangeAccount", it) }
                p.rangeFrom?.let { put("rangeFrom", it.toString()) }
                p.rangeTo?.let { put("rangeTo", it.toString()) }
                put("carryChoices", BudgetPlan.encodeChoices(p.carryChoices))
                p.carryFrom?.let { put("carryFrom", it.toString()) }
            })
            put("categories", JSONArray(book.categories.map { c ->
                JSONObject().put("id", c.id).put("name", c.name).put("icon", c.emoji).put("color", c.color)
                    .put("type", c.type.name).put("budget", c.budgetMinor.orNull())
                    .put("addsToBudget", c.addsToBudget).put("bucket", c.bucket.orNull()).put("hidden", c.hidden)
            }))
            val usedIcons = (book.categories.map { it.emoji } + book.accounts.map { it.icon } + book.goals.map { it.emoji })
                .filter { it.startsWith("img:") }.map { it.removePrefix("img:").substringAfterLast('/') }.toSet()
            book.icons.filterKeys { it in usedIcons }.takeIf { it.isNotEmpty() }?.let { put("icons", JSONObject(it)) }
            put("accounts", JSONArray(book.accounts.map { a ->
                JSONObject().put("id", a.id).put("name", a.name).put("icon", a.icon).put("color", a.color)
                    .put("opening", a.openingMinor).put("createdAt", a.createdAt).put("smsDigits", a.smsDigits.orNull())
                    .put("currency", a.currency.orNull()).put("openingNative", a.openingNative.orNull())
                    .put("outOfBudget", a.outOfBudget).put("outOfTotal", a.outOfTotal).put("position", a.position)
            }))
            put("transactions", JSONArray(book.transactions.map { t ->
                JSONObject().put("id", t.id).put("amount", t.amountMinor).put("type", t.type.name)
                    .put("categoryId", t.categoryId).put("day", t.epochDay).put("note", t.note)
                    .put("createdAt", t.createdAt).put("accountId", t.accountId.orNull())
                    .put("receipt", photoRef(t.receiptPath))
                    .put("foreignAmount", t.foreignMinor.orNull()).put("foreignCurrency", t.foreignCurrency.orNull())
                    .put("splitOf", t.splitOf.orNull())
                    .apply { if (t.details.isNotEmpty()) put("details", t.details) }
            }))
            put("transfers", JSONArray(book.transfers.map { t ->
                JSONObject().put("id", t.id).put("from", t.fromAccountId).put("to", t.toAccountId).put("amount", t.amountMinor)
                    .put("day", t.epochDay).put("note", t.note).put("createdAt", t.createdAt)
            }))
            put("recurring", JSONArray(book.recurring.map { r ->
                JSONObject().put("id", r.id).put("name", r.name).put("amount", r.amountMinor).put("type", r.type.name)
                    .put("categoryId", r.categoryId).put("day", r.dayOfMonth).put("start", r.startMonth)
                    .put("lastPosted", r.lastPostedMonth.orNull()).put("active", r.active)
                    .put("every", r.everyMonths).put("account", r.accountId.orNull())
                    .put("rate", r.ratePercent.orNull())
                    .apply { if (r.manual) put("manual", true) }
            }))
            put("goals", JSONArray(book.goals.map { g ->
                JSONObject().put("id", g.id).put("name", g.name).put("icon", g.emoji).put("color", g.color)
                    .put("target", g.targetMinor).put("targetMonth", g.targetMonth).put("createdAt", g.createdAt)
                    .put("link", g.link.orNull()).put("linkPrice", g.linkPriceMinor.orNull())
                    .put("linkCheckedAt", g.linkCheckedAt.orNull()).put("moreLinks", g.moreLinks.orNull())
            }))
            put("deposits", JSONArray(book.deposits.map { d ->
                JSONObject().put("id", d.id).put("goalId", d.goalId).put("amount", d.amountMinor)
                    .put("day", d.epochDay).put("createdAt", d.createdAt)
            }))
            put("debts", JSONArray(book.debts.map { d ->
                JSONObject().put("id", d.id).put("person", d.person).put("amount", d.amountMinor).put("note", d.note)
                    .put("day", d.epochDay).put("settled", d.settled).put("createdAt", d.createdAt)
                    .put("account", d.accountId.orNull()).put("repayment", d.repayment)
                    .put("splitOf", d.splitOf.orNull()).put("pending", d.pending)
                    .put("settledOn", d.settledOn.orNull()).put("sharedId", d.sharedId.orNull())
                    .put("receipts", JSONArray(d.photos.map { photoRef(it) }.filter { it != JSONObject.NULL }))
            }))
            if (photos.length() > 0) put("photos", photos)
            if (book.removed.isNotEmpty()) put("removed", SyncRemoved.encode(book.removed))
            if (forThisComputer) {
                put("deleted", JSONArray(book.deleted.map { d ->
                    JSONObject().put("id", d.id).put("amount", d.amountMinor).put("type", d.type.name).put("categoryId", d.categoryId)
                        .put("day", d.epochDay).put("note", d.note).put("createdAt", d.createdAt).put("accountId", d.accountId.orNull())
                        .put("receipt", d.receiptPath.orNull()).put("deletedAt", d.deletedAt)
                        .put("foreignAmount", d.foreignMinor.orNull()).put("foreignCurrency", d.foreignCurrency.orNull())
                        .put("splitOf", d.splitOf.orNull()).put("details", d.details)
                }))
                put("prefs", encodePrefs(book.prefs))
            }
        }
    }

    /** The JSON in [text], decrypted with [password] when it's an encrypted backup. */
    fun open(text: String, password: String?): JSONObject {
        val json = runCatching { JSONObject(text) }.getOrElse { throw NotABackup() }
        if (BackupCrypto.isEncrypted(json)) {
            if (password.isNullOrEmpty()) throw PasswordNeeded(wrong = false)
            val plain = runCatching { BackupCrypto.decrypt(json, password) }.getOrElse { throw PasswordNeeded(wrong = true) }
            return JSONObject(plain)
        }
        return json
    }

    /** Reads a backup into a book. Photos in it are written to [photosDir]; the entries point at them there. */
    fun decode(json: JSONObject, photosDir: File, keepPrefs: Prefs? = null): Book {
        val app = json.optString("app")
        if (app != "leftovers" && app != "cash-tracker") throw NotABackup()
        fun JSONObject.longOrNull(key: String) = if (!has(key) || isNull(key)) null else getLong(key)
        fun JSONObject.stringOrNull(key: String) = if (!has(key) || isNull(key)) null else getString(key)
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

        val savedPhotos = json.optJSONObject("photos")
        fun restorePhoto(name: String?): String? {
            if (name.isNullOrBlank() || savedPhotos == null || !savedPhotos.has(name)) return null
            // Only a plain file name, never a path out of the photos folder.
            val safe = name.substringAfterLast('/').substringAfterLast('\\')
            if (safe.isBlank() || safe.startsWith(".")) return null
            return runCatching {
                photosDir.mkdirs()
                val f = File(photosDir, safe)
                f.writeBytes(Base64.getDecoder().decode(savedPhotos.getString(name)))
                f.absolutePath
            }.getOrNull()
        }

        val categories = json.getJSONArray("categories").objects().map {
            val type = TxType.valueOf(it.getString("type"))
            val name = it.getString("name")
            val adds = it.optBoolean("addsToBudget", type == TxType.INCOME && name != "Salary")
            Category(it.getLong("id"), name, it.getString("icon"), it.getLong("color"), type, it.longOrNull("budget"), adds, it.stringOrNull("bucket"), it.optBoolean("hidden", false))
        }
        val accounts = json.optJSONArray("accounts")?.objects().orEmpty().map {
            Account(
                it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getLong("opening"), it.getLong("createdAt"), it.stringOrNull("smsDigits"),
                currency = it.stringOrNull("currency"), openingNative = it.longOrNull("openingNative"),
                outOfBudget = it.optBoolean("outOfBudget"), outOfTotal = it.optBoolean("outOfTotal"), position = it.optInt("position"),
            )
        }
        val transactions = json.getJSONArray("transactions").objects().map {
            Transaction(
                it.getLong("id"), it.getLong("amount"), TxType.valueOf(it.getString("type")), it.getLong("categoryId"),
                it.getLong("day"), it.getString("note"), it.getLong("createdAt"), it.longOrNull("accountId"), restorePhoto(it.stringOrNull("receipt")),
                it.longOrNull("foreignAmount"), it.stringOrNull("foreignCurrency"), it.longOrNull("splitOf"),
                it.optString("details"),
            )
        }
        val transfers = json.optJSONArray("transfers")?.objects().orEmpty().map {
            Transfer(it.getLong("id"), it.getLong("from"), it.getLong("to"), it.getLong("amount"), it.getLong("day"), it.getString("note"), it.getLong("createdAt"))
        }
        val recurring = json.optJSONArray("recurring")?.objects().orEmpty().map {
            Recurring(
                it.getLong("id"), it.getString("name"), it.getLong("amount"), TxType.valueOf(it.getString("type")),
                it.getLong("categoryId"), it.getInt("day"), it.getString("start"), it.stringOrNull("lastPosted"), it.getBoolean("active"),
                it.optInt("every", 1), it.longOrNull("account"),
                ratePercent = it.stringOrNull("rate"), manual = it.optBoolean("manual"),
            )
        }
        val goals = json.optJSONArray("goals")?.objects().orEmpty().map {
            Goal(
                it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getLong("target"), it.getString("targetMonth"), it.getLong("createdAt"),
                link = it.stringOrNull("link"), linkPriceMinor = it.longOrNull("linkPrice"), linkCheckedAt = it.longOrNull("linkCheckedAt"),
                moreLinks = it.stringOrNull("moreLinks"),
            )
        }
        val deposits = json.optJSONArray("deposits")?.objects().orEmpty().map {
            GoalDeposit(it.getLong("id"), it.getLong("goalId"), it.getLong("amount"), it.getLong("day"), it.getLong("createdAt"))
        }
        val debts = json.optJSONArray("debts")?.objects().orEmpty().map {
            val names = it.optJSONArray("receipts")?.let { a -> (0 until a.length()).map(a::getString) } ?: listOf(it.optString("receipt"))
            Debt(
                it.getLong("id"), it.getString("person"), it.getLong("amount"), it.optString("note"), it.getLong("day"),
                it.optBoolean("settled"), it.getLong("createdAt"), photosToPath(names.mapNotNull(::restorePhoto)),
                accountId = if (it.isNull("account")) null else it.optLong("account"),
                repayment = it.optBoolean("repayment"),
                splitOf = it.longOrNull("splitOf"),
                pending = it.optInt("pending", 0),
                settledOn = it.longOrNull("settledOn"),
                sharedId = it.stringOrNull("sharedId"),
            )
        }
        val deleted = json.optJSONArray("deleted")?.objects().orEmpty().map {
            DeletedTransaction(
                it.getLong("id"), it.getLong("amount"), TxType.valueOf(it.getString("type")), it.getLong("categoryId"), it.getLong("day"),
                it.getString("note"), it.getLong("createdAt"), it.longOrNull("accountId"), it.stringOrNull("receipt"), it.getLong("deletedAt"),
                it.longOrNull("foreignAmount"), it.stringOrNull("foreignCurrency"), it.longOrNull("splitOf"), it.optString("details"),
            )
        }
        val icons = json.optJSONObject("icons")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
        val s = json.optJSONObject("settings")
        return Book(
            currency = s?.optString("currency")?.takeIf { it.isNotEmpty() } ?: "USD",
            plan = s?.let(::decodePlan) ?: BudgetPlan(),
            categories = categories,
            accounts = accounts,
            transactions = transactions,
            transfers = transfers,
            recurring = recurring,
            goals = goals,
            deposits = deposits,
            debts = debts,
            deleted = deleted,
            removed = SyncRemoved.decode(json.optJSONArray("removed")),
            icons = icons,
            prefs = keepPrefs ?: json.optJSONObject("prefs")?.let(::decodePrefs) ?: Prefs(defaultAccountId = accounts.firstOrNull()?.id ?: 0),
        )
    }

    private fun date(s: JSONObject, key: String) = s.optString(key).takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun decodePlan(s: JSONObject) = BudgetPlan(
        mode = BudgetMode.entries.firstOrNull { it.name == s.optString("budgetMode") } ?: BudgetMode.MONTHLY,
        monthlyMinor = s.optLong("monthly"),
        yearlyMinor = s.optLong("yearly"),
        split = YearSplit.entries.firstOrNull { it.name == s.optString("split") } ?: BudgetPlan().split,
        custom = BudgetPlan.decodeCustom(s.optString("custom")),
        carry = CarryMode.entries.firstOrNull { it.name == s.optString("carryMode") } ?: CarryMode.ASK,
        extraIncome = s.optBoolean("extraIncome", false),
        startDay = s.optInt("startDay", 1),
        rangeMinor = s.optLong("rangeBudget", 0),
        buckets = s.optBoolean("buckets", false),
        bucketIncome = s.optLong("bucketIncome", 0),
        bucketPicker = s.optBoolean("bucketPicker", false),
        periodMinor = s.optLong("periodBudget", 0),
        periodDays = s.optInt("periodDays", 14),
        periodFrom = date(s, "periodFrom"),
        rangeTag = s.optString("rangeTag").takeIf { it.isNotEmpty() },
        rangeAccount = if (s.has("rangeAccount")) s.optLong("rangeAccount") else null,
        rangeFrom = date(s, "rangeFrom"),
        rangeTo = date(s, "rangeTo"),
        carryChoices = BudgetPlan.decodeChoices(s.optString("carryChoices")),
        carryFrom = s.optString("carryFrom").takeIf { it.isNotEmpty() }?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
    )

    private fun encodePrefs(p: Prefs) = JSONObject().apply {
        put("theme", p.theme.name)
        put("glass", p.glass)
        put("splitsAsOneBill", p.splitsAsOneBill)
        put("showNewDailyLimit", p.showNewDailyLimit)
        put("defaultAccountId", p.defaultAccountId)
        p.homeAccountId?.let { put("homeAccountId", it) }
        put("deletedKeepDays", p.deletedKeepDays)
        put("onboarded", p.onboarded)
        put("recapSeen", p.recapSeen)
        put("whatsNewSeen", p.whatsNewSeen)
        p.syncDir?.let { put("syncDir", it) }
        p.syncServer?.let { put("syncServer", it) }
        p.syncUser?.let { put("syncUser", it) }
        p.syncDevice?.let { put("syncDevice", it) }
        put("syncLast", p.syncLast)
        put("rates", JSONObject(p.rates))
        put("dismissed", JSONArray(p.dismissed.toList()))
        put("tray", p.tray)
        put("billReminders", p.billReminders)
    }

    private fun decodePrefs(o: JSONObject): Prefs {
        fun str(key: String) = o.optString(key).takeIf { o.has(key) && it.isNotEmpty() }
        return Prefs(
            theme = Theme.entries.firstOrNull { it.name == o.optString("theme") } ?: Theme.SYSTEM,
            glass = o.optBoolean("glass"),
            splitsAsOneBill = o.optBoolean("splitsAsOneBill", true),
            showNewDailyLimit = o.optBoolean("showNewDailyLimit"),
            defaultAccountId = o.optLong("defaultAccountId"),
            homeAccountId = if (o.has("homeAccountId")) o.optLong("homeAccountId") else null,
            deletedKeepDays = o.optInt("deletedKeepDays", 90),
            onboarded = o.optBoolean("onboarded"),
            recapSeen = o.optString("recapSeen"),
            whatsNewSeen = o.optString("whatsNewSeen"),
            syncDir = str("syncDir"),
            syncServer = str("syncServer"),
            syncUser = str("syncUser"),
            syncDevice = str("syncDevice"),
            syncLast = o.optLong("syncLast"),
            rates = o.optJSONObject("rates")?.let { r -> r.keys().asSequence().associateWith { r.getString(it) } }.orEmpty(),
            dismissed = o.optJSONArray("dismissed")?.let { a -> (0 until a.length()).map(a::getString).toSet() }.orEmpty(),
            tray = o.optBoolean("tray", true),
            billReminders = o.optBoolean("billReminders", true),
        )
    }
}
