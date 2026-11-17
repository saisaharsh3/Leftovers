package com.leftovers.desktop.data

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import com.leftovers.app.data.hashtags
import com.leftovers.app.data.totalOf
import com.leftovers.app.util.AmountInput
import java.time.LocalDate
import java.time.YearMonth

enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Spent"), INCOME("Income") }
enum class ActivitySort(val label: String) { NEWEST("Newest"), HIGHEST("Highest"), LOWEST("Lowest") }

/** What Activity is showing: a month or all time, a search, and filters. */
data class ActivityQuery(
    val month: YearMonth = YearMonth.now(),
    val allTime: Boolean = false,
    val type: TypeFilter = TypeFilter.ALL,
    val search: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val tag: String? = null,
    val sort: ActivitySort = ActivitySort.NEWEST,
) {
    val narrows get() = accountId != null || categoryId != null || tag != null
}

/** Money moved between two of your accounts that day; [sign] is −1 or +1 for the account being shown, else 0. */
data class TransferLine(val transfer: Transfer, val from: String, val to: String, val sign: Int)

/** A day's entries; with [label], one list in another order (by amount) instead of a day. */
data class DayGroup(val date: LocalDate, val items: List<TransactionItem>, val label: String? = null, val transfers: List<TransferLine> = emptyList()) {
    val spent get() = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val earned get() = items.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
}

data class ActivityModel(val groups: List<DayGroup>, val income: Long, val expense: Long, val count: Int, val monthItems: List<TransactionItem>) {
    companion object {
        /** Every word in the search is in the name, details, category or account, or is the amount itself. */
        fun matches(item: TransactionItem, query: String, accountName: String?): Boolean =
            query.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.all { word ->
                val amount = AmountInput.toMinor(word.replace(",", ""))
                item.note.contains(word, ignoreCase = true) ||
                    item.details.contains(word, ignoreCase = true) ||
                    item.categoryName.contains(word, ignoreCase = true) ||
                    accountName?.contains(word, ignoreCase = true) == true ||
                    (amount != null && item.amountMinor == amount)
            }

        fun of(view: View, q: ActivityQuery): ActivityModel {
            val all = view.entries
            val oneBill = view.book.prefs.splitsAsOneBill
            val names = view.accounts.associate { it.id to it.name }
            val monthItems = all.filter { YearMonth.from(it.date) == q.month }
            val scoped = when {
                q.search.isNotBlank() -> all.filter { matches(it, q.search, names[it.accountId]) && (q.tag == null || q.tag in hashtags(it.note)) }
                q.tag != null -> all.filter { q.tag in hashtags(it.note) }
                q.allTime -> all
                else -> monthItems
            }
            val narrowed = scoped.filter { (q.accountId == null || it.accountId == q.accountId) && (q.categoryId == null || it.categoryId == q.categoryId) }
            val filtered = when (q.type) {
                TypeFilter.ALL -> narrowed
                TypeFilter.EXPENSE -> narrowed.filter { it.type == TxType.EXPENSE }
                TypeFilter.INCOME -> narrowed.filter { it.type == TxType.INCOME }
            }
            // Transfers between your own accounts, on their day: not spending or income, so never in the totals.
            val words = q.search.lowercase().split(' ').filter { it.isNotBlank() }
            val moved = if (q.sort != ActivitySort.NEWEST || q.type != TypeFilter.ALL || q.categoryId != null || q.tag != null) emptyList()
            else view.book.transfers.asSequence()
                .filter { q.search.isNotBlank() || q.allTime || YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == q.month }
                .filter { q.accountId == null || it.fromAccountId == q.accountId || it.toAccountId == q.accountId }
                .map { t -> TransferLine(t, names[t.fromAccountId] ?: "?", names[t.toAccountId] ?: "?", when (q.accountId) { null -> 0; t.fromAccountId -> -1; else -> 1 }) }
                .filter { line -> words.all { w -> "transfer ${line.from} ${line.to} ${line.transfer.note}".lowercase().contains(w) } }
                .sortedByDescending { it.transfer.createdAt }
                .toList()
            val groups = if (q.sort == ActivitySort.NEWEST) {
                val byDay = filtered.groupBy { it.epochDay }
                val movedByDay = moved.groupBy { it.transfer.epochDay }
                (byDay.keys + movedByDay.keys).distinct().sortedDescending().map { day ->
                    DayGroup(LocalDate.ofEpochDay(day), byDay[day].orEmpty().let { if (oneBill) splitOrder(it) else it }, transfers = movedByDay[day].orEmpty())
                }
            } else {
                // By amount: one list. A split bill counts as its whole amount and keeps its parts under it.
                val ids = filtered.map { it.id }.toSet()
                val owedFor = view.book.debts.filter { it.splitOf != null && !it.repayment }.groupBy { it.splitOf!! }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
                val bills = if (!oneBill) filtered.map { listOf(it) }
                else filtered.filter { it.splitOf == null || it.splitOf !in ids }.map { main -> listOf(main) + filtered.filter { it.splitOf == main.id } }
                fun whole(b: List<TransactionItem>) = b.sumOf { it.amountMinor } + if (oneBill) owedFor[b.first().id] ?: 0L else 0L
                val sorted = if (q.sort == ActivitySort.HIGHEST) bills.sortedByDescending(::whole) else bills.sortedBy(::whole)
                if (sorted.isEmpty()) emptyList()
                else listOf(DayGroup(sorted.first().first().date, sorted.flatten(), if (q.sort == ActivitySort.HIGHEST) "Highest first" else "Lowest first"))
            }
            return ActivityModel(groups, filtered.totalOf(TxType.INCOME), filtered.totalOf(TxType.EXPENSE), filtered.size, monthItems)
        }
    }
}
