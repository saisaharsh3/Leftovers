package com.leftovers.desktop.data

import com.leftovers.app.data.Account
import com.leftovers.app.data.AccountWithBalance
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.DeletedItem
import com.leftovers.app.data.DeletedTransaction
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.NativeParts
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.SubscriptionMatch
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import com.leftovers.app.data.nativeBalance
import com.leftovers.app.util.BackupMerge
import com.leftovers.app.util.SyncRemoved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * What the screens see: the book, plus every record joined the way the phone's database queries join them, so the
 * shared maths (budgets, forecasts, trends) gets the same shapes it gets on the phone.
 */
class View(val book: Book) {
    private val categoryById = book.categories.associateBy { it.id }
    private val accountById = book.accounts.associateBy { it.id }

    /** Newest first, like the phone's Activity. An envelope budget marks entries it doesn't cover as out of it. */
    val entries: List<TransactionItem> = book.transactions
        .mapNotNull { t ->
            val c = categoryById[t.categoryId] ?: return@mapNotNull null
            TransactionItem(
                t.id, t.amountMinor, t.type, t.categoryId, t.epochDay, t.note, t.createdAt, c.name, c.emoji, c.color,
                t.accountId, t.receiptPath, c.addsToBudget, t.foreignMinor, t.foreignCurrency,
                outOfBudget = accountById[t.accountId]?.outOfBudget == true, splitOf = t.splitOf, details = t.details,
            )
        }
        .map { if (book.plan.envelope && it.type == TxType.EXPENSE && !it.outOfBudget && !book.plan.counts(it)) it.copy(outOfBudget = true) else it }
        .sortedWith(compareByDescending<TransactionItem> { it.epochDay }.thenByDescending { it.createdAt })

    val accounts: List<AccountWithBalance> = book.accounts.sortedWith(compareBy<Account> { it.position }.thenBy { it.id }).map { a ->
        val own = book.transactions.filter { it.accountId == a.id }
        val transfersIn = book.transfers.filter { it.toAccountId == a.id }.sumOf { it.amountMinor }
        val transfersOut = book.transfers.filter { it.fromAccountId == a.id }.sumOf { it.amountMinor }
        val owed = book.debts.filter { it.accountId == a.id }.sumOf { it.amountMinor }
        val balance = a.openingMinor + own.sumOf { it.signed() } + transfersIn - transfersOut - owed
        val native = a.currency?.let { cur ->
            val parts = NativeParts(
                a.id,
                inCurrency = own.filter { it.foreignCurrency == cur }.sumOf { (it.foreignMinor ?: 0L).let { m -> if (it.type == TxType.INCOME) m else -m } },
                inAppCurrency = own.filter { it.foreignCurrency != cur }.sumOf { it.signed() } + transfersIn - transfersOut - owed,
            )
            nativeBalance(a.openingNative, parts, book.prefs.rates[cur])
        }
        AccountWithBalance(a.id, a.name, a.icon, a.color, a.openingMinor, a.createdAt, a.smsDigits, balance, a.currency, a.openingNative, a.outOfBudget, a.outOfTotal, native, a.position)
    }

    val recurring: List<RecurringItem> = book.recurring
        .mapNotNull { r ->
            val c = categoryById[r.categoryId] ?: return@mapNotNull null
            RecurringItem(r.id, r.name, r.amountMinor, r.type, r.categoryId, r.dayOfMonth, r.startMonth, r.lastPostedMonth, r.active, r.everyMonths, r.accountId, c.name, c.emoji, c.color, r.ratePercent, r.manual)
        }
        .sortedWith(compareByDescending<RecurringItem> { it.active }.thenBy { it.dayOfMonth })

    val goals: List<GoalWithSaved> = book.goals.sortedBy { it.createdAt }.map { g ->
        GoalWithSaved(g.id, g.name, g.emoji, g.color, g.targetMinor, g.targetMonth, g.createdAt, book.deposits.filter { it.goalId == g.id }.sumOf { it.amountMinor }, g.link, g.linkPriceMinor, g.linkCheckedAt, g.moreLinks)
    }

    val deleted: List<DeletedItem> = book.deleted.sortedByDescending { it.deletedAt }.map { d ->
        val c = categoryById[d.categoryId]
        DeletedItem(d, c?.name, c?.emoji, c?.color)
    }

    fun category(id: Long): Category? = categoryById[id]
    fun account(id: Long?): AccountWithBalance? = accounts.firstOrNull { it.id == id }

    private fun Transaction.signed() = if (type == TxType.INCOME) amountMinor else -amountMinor
}

/**
 * The app's data on this computer, saved as one file in the user's app-data folder (a backup the phone can open as
 * it is). Every change goes through here, is saved at once, and shows on every screen.
 */
class Store(val dir: File = defaultDir()) {
    val file = File(dir, "leftovers.json")
    val photosDir = File(dir, "photos")

    private val _view = MutableStateFlow(View(load()))
    val view: StateFlow<View> = _view
    val book: Book get() = _view.value.book

    private fun load(): Book {
        if (!file.exists()) return Book(prefs = Prefs())
        return runCatching { Codec.decode(Codec.open(file.readText(), null), photosDir) }.getOrElse {
            // Keep a broken file aside rather than start over on top of it.
            runCatching { file.copyTo(File(dir, "leftovers-unreadable-${System.currentTimeMillis()}.json")) }
            Book(prefs = Prefs())
        }
    }

    @Synchronized
    fun save(book: Book) {
        _view.value = View(book)
        dir.mkdirs()
        val tmp = File(dir, "leftovers.json.tmp")
        tmp.writeText(Codec.encode(book, withPhotos = false, forThisComputer = true).toString())
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** One change to the book, saved. */
    fun edit(change: Book.() -> Book) = save(book.change())

    // ---- Entries ----

    private fun nextId(ids: List<Long>) = (ids.maxOrNull() ?: 0L) + 1

    private fun Transaction.sameEntry(o: Transaction) =
        epochDay == o.epochDay && amountMinor == o.amountMinor && type == o.type && SyncRemoved.key(note) == SyncRemoved.key(o.note)

    private fun List<SyncRemoved.Removed>.plus(t: Transaction, now: Long) =
        filterNot { it.sameEntry(t) } + SyncRemoved.Removed(t.epochDay, t.amountMinor, t.type, t.note, now)

    private fun List<SyncRemoved.Removed>.forget(t: Transaction) = filterNot { it.sameEntry(t) }

    /** Saves an entry, new or changed; returns its id. */
    fun saveTransaction(t: Transaction, now: Long = System.currentTimeMillis()): Long {
        var id = t.id
        edit {
            val old = transactions.firstOrNull { it.id == t.id }
            var removed = this.removed
            // An edit that changes the day, amount, kind or note replaces the old entry on synced devices.
            if (old != null && !old.sameEntry(t)) removed = removed.plus(old, now)
            removed = removed.forget(t)
            if (id == 0L) id = nextId(transactions.map { it.id } + deleted.map { it.id })
            val saved = t.copy(id = id)
            copy(
                transactions = transactions.filterNot { it.id == id } + saved,
                deleted = deleted.filterNot { it.id == id },
                removed = removed,
            )
        }
        return id
    }

    fun addTransactions(list: List<Transaction>) = edit {
        var id = nextId(transactions.map { it.id } + deleted.map { it.id })
        copy(transactions = transactions + list.map { it.copy(id = if (it.id == 0L) id++ else it.id) })
    }

    /** Deletes an entry, keeping it under Deleted entries so it can be restored. */
    fun deleteTransaction(t: Transaction, now: Long = System.currentTimeMillis()) = edit {
        copy(
            transactions = transactions.filterNot { it.id == t.id },
            deleted = deleted.filterNot { it.id == t.id } + DeletedTransaction.from(t, now),
            removed = removed.plus(t, now),
        )
    }

    /** Takes an entry back at once, with no copy in Deleted entries (undoing a slip). */
    fun removeTransaction(t: Transaction, now: Long = System.currentTimeMillis()) = edit {
        copy(transactions = transactions.filterNot { it.id == t.id }, removed = removed.plus(t, now))
    }

    fun restoreDeleted(ids: List<Long>) = edit {
        val back = deleted.filter { it.id in ids }.map { e ->
            val categoryId = if (categories.any { it.id == e.categoryId }) e.categoryId else {
                val sameType = categories.filter { it.type == e.type }
                (sameType.firstOrNull { it.name.equals("Other", ignoreCase = true) } ?: sameType.firstOrNull())?.id ?: return@map null
            }
            e.toTransaction().copy(categoryId = categoryId)
        }.filterNotNull()
        var removed = this.removed
        back.forEach { removed = removed.forget(it) }
        copy(transactions = transactions + back, deleted = deleted.filterNot { it.id in ids }, removed = removed)
    }

    fun deleteForever(ids: List<Long>) = edit {
        deleted.filter { it.id in ids }.forEach { it.receiptPath?.let { p -> File(p).delete() } }
        copy(deleted = deleted.filterNot { it.id in ids })
    }

    fun purgeDeleted(now: Long = System.currentTimeMillis()) {
        val keep = book.prefs.deletedKeepDays
        if (keep <= 0) return
        val cutoff = now - TimeUnit.DAYS.toMillis(keep.toLong())
        deleteForever(book.deleted.filter { it.deletedAt < cutoff }.map { it.id })
    }

    // ---- Categories and accounts ----

    fun saveCategory(c: Category): Long {
        var id = c.id
        edit {
            if (id == 0L) id = nextId(categories.map { it.id })
            copy(categories = categories.filterNot { it.id == id } + c.copy(id = id))
        }
        return id
    }

    /** Deletes the category, or returns how many entries and subscriptions still use it (and deletes nothing). */
    fun deleteCategory(c: Category): Int {
        val used = book.transactions.count { it.categoryId == c.id } + book.recurring.count { it.categoryId == c.id }
        if (used == 0) edit { copy(categories = categories.filterNot { it.id == c.id }) }
        return used
    }

    fun saveAccount(a: Account): Long {
        var id = a.id
        edit {
            if (id == 0L) id = nextId(accounts.map { it.id })
            val saved = if (a.id == 0L) a.copy(id = id, position = (accounts.maxOfOrNull { it.position } ?: -1) + 1) else a
            copy(accounts = accounts.filterNot { it.id == id } + saved)
        }
        return id
    }

    fun deleteAccount(a: Account) = edit {
        copy(
            accounts = accounts.filterNot { it.id == a.id },
            transactions = transactions.map { if (it.accountId == a.id) it.copy(accountId = null) else it },
            transfers = transfers.filterNot { it.fromAccountId == a.id || it.toAccountId == a.id },
            recurring = recurring.map { if (it.accountId == a.id) it.copy(accountId = null) else it },
            debts = debts.map { if (it.accountId == a.id) it.copy(accountId = null) else it },
            prefs = prefs.copy(
                defaultAccountId = if (prefs.defaultAccountId == a.id) accounts.firstOrNull { it.id != a.id }?.id ?: 0 else prefs.defaultAccountId,
                homeAccountId = prefs.homeAccountId.takeIf { it != a.id },
            ),
        )
    }

    fun reorderAccounts(ids: List<Long>) = edit {
        copy(accounts = accounts.map { a -> a.copy(position = ids.indexOf(a.id).takeIf { it >= 0 } ?: a.position) })
    }

    fun saveTransfer(t: Transfer): Long {
        var id = t.id
        edit {
            if (id == 0L) id = nextId(transfers.map { it.id })
            copy(transfers = transfers.filterNot { it.id == id } + t.copy(id = id))
        }
        return id
    }

    fun deleteTransfer(t: Transfer) = edit { copy(transfers = transfers.filterNot { it.id == t.id }) }

    // ---- Subscriptions ----

    fun saveRecurring(r: Recurring): Long {
        var id = r.id
        edit {
            if (id == 0L) id = nextId(recurring.map { it.id })
            copy(recurring = recurring.filterNot { it.id == id } + r.copy(id = id))
        }
        return id
    }

    fun deleteRecurring(r: Recurring) = edit { copy(recurring = recurring.filterNot { it.id == r.id }) }

    private fun Book.withLastPosted(id: Long, month: String?) = copy(recurring = recurring.map { if (it.id == id) it.copy(lastPostedMonth = month) else it })

    /** Marks [r]'s [month] paid with an entry for what was really paid, and when (#64). Returns the entry's id. */
    fun payRecurring(r: Recurring, month: YearMonth, amountMinor: Long, epochDay: Long, accountId: Long?): Long {
        val id = saveTransaction(Transaction(amountMinor = amountMinor, type = r.type, categoryId = r.categoryId, epochDay = epochDay, note = r.name, accountId = accountId ?: r.accountId))
        edit { withLastPosted(r.id, month.toString()) }
        return id
    }

    fun skipRecurring(r: Recurring, month: YearMonth) = edit { withLastPosted(r.id, month.toString()) }

    fun unpayRecurring(r: Recurring, entryId: Long?) {
        entryId?.let { id -> book.transactions.firstOrNull { it.id == id }?.let(::removeTransaction) }
        edit { withLastPosted(r.id, r.lastPostedMonth) }
    }

    /** Logs every subscription charge whose day has come but isn't logged yet, catching up on days the app wasn't open. */
    fun postDueRecurring(today: LocalDate = LocalDate.now()): List<Transaction> {
        val posted = mutableListOf<Transaction>()
        val v = view.value
        val balances = v.accounts.associate { it.id to it.balanceMinor }.toMutableMap()
        val existing = v.entries.toMutableList()
        val currentMonth = YearMonth.from(today)
        var b = book
        b.recurring.filter { it.active && !it.manual }.forEach { r ->
            var month = r.lastPostedMonth?.let { YearMonth.parse(it).plusMonths(1) } ?: YearMonth.parse(r.startMonth)
            var lastPosted: YearMonth? = null
            while (month <= currentMonth) {
                if (!r.isDueIn(month)) { month = month.plusMonths(1); continue }
                val date = r.chargeDate(month)
                if (date > today) break
                val into = r.accountId ?: b.prefs.defaultAccountId
                val amount = r.interestOn(balances[into] ?: 0L) ?: r.amountMinor
                if (amount <= 0 || existing.any { alreadyLogged(it, r, amount, date) }) {
                    lastPosted = month; month = month.plusMonths(1); continue
                }
                balances[into] = (balances[into] ?: 0L) + amount
                val tx = Transaction(
                    id = nextId(b.transactions.map { it.id } + b.deleted.map { it.id }),
                    amountMinor = amount, type = r.type, categoryId = r.categoryId, epochDay = date.toEpochDay(), note = r.name,
                    accountId = r.accountId ?: b.prefs.defaultAccountId,
                )
                b = b.copy(transactions = b.transactions + tx)
                posted += tx
                lastPosted = month
                month = month.plusMonths(1)
            }
            lastPosted?.let { b = b.withLastPosted(r.id, it.toString()) }
        }
        if (posted.isNotEmpty() || b != book) save(b)
        return posted
    }

    private fun alreadyLogged(item: TransactionItem, r: Recurring, amount: Long, date: LocalDate): Boolean =
        item.type == r.type && SubscriptionMatch.closeAmount(item.amountMinor, amount) && kotlin.math.abs(item.epochDay - date.toEpochDay()) <= 7 &&
            (item.categoryId == r.categoryId || (r.name.isNotBlank() && item.note.contains(r.name.trim(), ignoreCase = true)))

    // ---- Goals ----

    fun saveGoal(g: Goal): Long {
        var id = g.id
        edit {
            if (id == 0L) id = nextId(goals.map { it.id })
            copy(goals = goals.filterNot { it.id == id } + g.copy(id = id))
        }
        return id
    }

    fun deleteGoal(g: Goal) = edit { copy(goals = goals.filterNot { it.id == g.id }, deposits = deposits.filterNot { it.goalId == g.id }) }

    fun saveDeposit(d: GoalDeposit): Long {
        var id = d.id
        edit {
            if (id == 0L) id = nextId(deposits.map { it.id })
            copy(deposits = deposits.filterNot { it.id == id } + d.copy(id = id))
        }
        return id
    }

    fun deleteDeposit(d: GoalDeposit) = edit { copy(deposits = deposits.filterNot { it.id == d.id }) }

    // ---- Money owed ----

    fun saveDebt(d: Debt): Long {
        var id = d.id
        edit {
            if (id == 0L) id = nextId(debts.map { it.id })
            copy(debts = debts.filterNot { it.id == id } + d.copy(id = id))
        }
        return id
    }

    fun deleteDebt(d: Debt) = edit {
        d.photos.forEach { File(it).delete() }
        copy(debts = debts.filterNot { it.id == d.id })
    }

    private fun samePerson(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /**
     * Records [amountMinor] paid back by or to [person] (all of it when null), into or out of [accountId] (unless
     * [chosen], the account of their latest entry). Paying all of it settles them.
     */
    fun settleUp(person: String, amountMinor: Long? = null, accountId: Long? = null, chosen: Boolean = false, epochDay: Long = LocalDate.now().toEpochDay()) = edit {
        val open = debts.filter { samePerson(it.person, person) && !it.settled }.sortedByDescending { it.createdAt }
        val net = open.sumOf { it.amountMinor }
        fun Book.settleAll() = copy(debts = debts.map { if (samePerson(it.person, person) && !it.settled) it.copy(settled = true, settledOn = epochDay) else it })
        if (net == 0L) return@edit settleAll()
        val paid = (amountMinor ?: kotlin.math.abs(net)).coerceIn(1, kotlin.math.abs(net))
        val payback = Debt(
            id = nextId(debts.map { it.id }),
            person = open.first().person.trim(),
            amountMinor = if (net > 0) -paid else paid,
            note = "Paid back",
            epochDay = epochDay,
            accountId = if (chosen) accountId else open.firstNotNullOfOrNull { it.accountId },
            repayment = true,
        )
        val with = copy(debts = debts + payback)
        if (paid == kotlin.math.abs(net)) with.settleAll() else with
    }

    /** One entry paid back in full, apart from the rest with that person. */
    fun settleEntry(d: Debt, epochDay: Long = LocalDate.now().toEpochDay()) = edit {
        val payback = Debt(
            id = nextId(debts.map { it.id }), person = d.person.trim(), amountMinor = -d.amountMinor, note = "Paid back",
            epochDay = epochDay, accountId = d.accountId, repayment = true, settled = true, settledOn = epochDay,
        )
        copy(debts = debts.map { if (it.id == d.id) it.copy(settled = true, settledOn = epochDay) else it } + payback)
    }

    // ---- Settings ----

    /** Saves the budget plan; carrying over counts from the month a budget was first set, as on the phone. */
    fun setPlan(plan: BudgetPlan) = edit {
        val from = plan.carryFrom ?: this.plan.carryFrom
        copy(plan = plan.copy(carryFrom = if (plan.isSet) from ?: YearMonth.now() else null, carryChoices = plan.carryChoices.ifEmpty { this.plan.carryChoices }))
    }
    fun setCurrency(code: String) = edit { copy(currency = code) }
    fun setPrefs(change: Prefs.() -> Prefs) = edit { copy(prefs = prefs.change()) }
    fun setCarryChoice(month: YearMonth, carry: Boolean) = edit { copy(plan = plan.copy(carryChoices = plan.carryChoices + (month to carry))) }

    // ---- Backups ----

    /** Replaces everything with a backup's data; this computer's own preferences stay. */
    fun replaceWith(json: org.json.JSONObject) {
        val fresh = Codec.decode(json, photosDir, keepPrefs = book.prefs)
        val defaultOk = fresh.accounts.any { it.id == fresh.prefs.defaultAccountId }
        save(fresh.copy(prefs = fresh.prefs.copy(onboarded = true, defaultAccountId = if (defaultOk) fresh.prefs.defaultAccountId else fresh.accounts.firstOrNull()?.id ?: 0)))
    }

    /** Adds what a backup has that this computer hasn't, like the phone's Keep mine and add theirs. */
    fun mergeWith(json: org.json.JSONObject, sync: Boolean = false, now: Long = System.currentTimeMillis()): BackupMerge.Plan {
        val theirs = Codec.decode(json, photosDir, keepPrefs = book.prefs)
        var b = book
        // Entries the other device removed go here too, if they were made before that.
        if (sync && theirs.removed.isNotEmpty()) {
            val drop = b.transactions.filter { t -> theirs.removed.any { it.matches(t) } }
            b = b.copy(transactions = b.transactions - drop.toSet(), deleted = b.deleted + drop.map { DeletedTransaction.from(it, now) })
        }
        val gone = (theirs.removed + if (sync) b.removed else emptyList())
        val existing = BackupMerge.Data(b.categories, b.accounts, b.transactions, b.transfers, b.recurring, b.goals, b.deposits, b.debts)
        val incoming = BackupMerge.Data(
            theirs.categories, theirs.accounts, theirs.transactions.filterNot { t -> gone.any { it.matches(t) } },
            theirs.transfers, theirs.recurring, theirs.goals, theirs.deposits, theirs.debts,
        )
        val plan = BackupMerge.plan(existing, incoming, b.deleted.map { it.id })
        with(plan.add) {
            b = b.copy(
                categories = b.categories + categories, accounts = b.accounts + accounts, transactions = b.transactions + transactions,
                transfers = b.transfers + transfers, recurring = b.recurring + recurring, goals = b.goals + goals,
                deposits = b.deposits + deposits, debts = b.debts + debts, icons = b.icons + theirs.icons,
            )
        }
        save(b.copy(prefs = b.prefs.copy(onboarded = true)))
        return plan
    }

    fun exportJson(withPhotos: Boolean): org.json.JSONObject = Codec.encode(book, withPhotos, forThisComputer = false)

    companion object {
        fun defaultDir(): File {
            val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            return if (appData != null) File(appData, "Leftovers") else File(System.getProperty("user.home"), ".leftovers")
        }
    }
}
