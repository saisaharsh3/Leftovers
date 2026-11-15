package com.leftovers.desktop.data

import com.leftovers.app.data.Debt
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import com.leftovers.app.util.friendlyLabel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Sums typed into the amount: "12+4.50", "3*250", "100/4", "1,200 - 75". Null when it isn't a sum. */
object Sum {
    fun minor(text: String): Long? = value(text)?.let { v ->
        v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { it >= 0 }
    }

    fun value(text: String): BigDecimal? {
        val s = text.replace(",", "").replace(" ", "").replace('×', '*').replace('x', '*').replace('÷', '/').replace('−', '-')
        if (s.isEmpty()) return null
        return runCatching {
            val p = Parser(s)
            val v = p.expr()
            if (p.i != s.length) null else v
        }.getOrNull()
    }

    private class Parser(val s: String) {
        var i = 0
        fun expr(): BigDecimal {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun term(): BigDecimal {
            var v = number()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = number()
                v = if (op == '*') v * r else v.divide(r, 6, RoundingMode.HALF_UP)
            }
            return v
        }
        fun number(): BigDecimal {
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                require(i < s.length && s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return BigDecimal(s.substring(start, i))
        }
    }
}

/** What the add panel holds before it's saved. */
data class EntryDraft(
    val id: Long = 0,
    val type: TxType = TxType.EXPENSE,
    val amountMinor: Long = 0,
    val categoryId: Long? = null,
    val note: String = "",
    val details: String = "",
    val day: LocalDate = LocalDate.now(),
    val accountId: Long? = null,
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** People the bill is split evenly with; each owes their share, and only yours is spending. */
    val people: List<String> = emptyList(),
    val repeatMonthly: Boolean = false,
    val foreignMinor: Long? = null,
    val foreignCurrency: String? = null,
) {
    val isEditing get() = id != 0L
    /** Each person's share, splitting the bill evenly with you; the remainder stays yours. */
    val share: Long get() = if (people.isEmpty() || type != TxType.EXPENSE) 0 else amountMinor / (people.size + 1)
    val yours: Long get() = amountMinor - share * people.size
}

/** Saves an entry as the phone's add screen does; returns the line to show, or null when it isn't complete. */
fun Store.saveEntry(d: EntryDraft, money: (Long) -> String): String? {
    if (d.amountMinor <= 0) return null
    val category = d.categoryId ?: return null
    val people = if (d.repeatMonthly || d.type != TxType.EXPENSE) emptyList() else d.people.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    val draft = d.copy(people = people)
    val savedShares = if (d.isEditing) book.debts.filter { it.splitOf == d.id && !it.repayment } else emptyList()
    val mainId = saveTransaction(
        Transaction(
            id = d.id, amountMinor = draft.yours, type = d.type, categoryId = category, epochDay = d.day.toEpochDay(),
            note = d.note.trim(), details = d.details.trim(), createdAt = d.createdAt, accountId = d.accountId,
            receiptPath = d.receiptPath, foreignMinor = d.foreignMinor.takeIf { people.isEmpty() && d.foreignCurrency != null },
            foreignCurrency = d.foreignCurrency.takeIf { people.isEmpty() },
        ),
    )
    val categoryName = book.categories.firstOrNull { it.id == category }?.name.orEmpty()
    val owedNote = d.note.trim().ifBlank { categoryName }
    people.forEach { person ->
        val was = savedShares.firstOrNull { it.person.equals(person, ignoreCase = true) }
        saveDebt(
            was?.copy(amountMinor = draft.share, note = owedNote, epochDay = d.day.toEpochDay(), accountId = d.accountId)
                ?: Debt(person = person, amountMinor = draft.share, note = owedNote, epochDay = d.day.toEpochDay(), accountId = d.accountId, splitOf = mainId),
        )
    }
    savedShares.filter { s -> people.none { it.equals(s.person, ignoreCase = true) } }.forEach { deleteDebt(it) }
    val repeats = d.repeatMonthly && !d.isEditing
    if (repeats) {
        val month = YearMonth.from(d.day).toString()
        saveRecurring(
            Recurring(
                name = owedNote, amountMinor = draft.yours, type = d.type, categoryId = category, dayOfMonth = d.day.dayOfMonth,
                startMonth = month, lastPostedMonth = month, accountId = d.accountId,
            ),
        )
    }
    val whenText = if (d.day == LocalDate.now()) "" else " · " + d.day.friendlyLabel()
    return when {
        d.isEditing && people.isEmpty() -> "Changes saved"
        people.isNotEmpty() -> "${money(d.amountMinor)} added · ${people.joinToString(" and ")} ${if (people.size == 1) "owes" else "owe"} you ${money(draft.share)}${if (people.size > 1) " each" else ""}"
        repeats -> "${money(draft.yours)} added · repeats every ${ordinal(d.day.dayOfMonth)}"
        else -> "${money(draft.yours)} added to $categoryName$whenText"
    }
}

/** A draft of an entry already saved, with the people it was split with. */
fun Store.draftOf(id: Long): EntryDraft? {
    val t = book.transactions.firstOrNull { it.id == id } ?: return null
    val shares = book.debts.filter { it.splitOf == id && !it.repayment }
    return EntryDraft(
        id = t.id, type = t.type, amountMinor = t.amountMinor + shares.sumOf { it.amountMinor }, categoryId = t.categoryId,
        note = t.note, details = t.details, day = LocalDate.ofEpochDay(t.epochDay), accountId = t.accountId, receiptPath = t.receiptPath,
        createdAt = t.createdAt, people = shares.map { it.person }, foreignMinor = t.foreignMinor, foreignCurrency = t.foreignCurrency,
    )
}

/** Deletes an entry and what came with it (its split parts, what people owe for it); returns how to undo it. */
fun Store.deleteEntry(id: Long): () -> Unit {
    val before = book
    val t = book.transactions.firstOrNull { it.id == id } ?: return {}
    book.transactions.filter { it.splitOf == id }.forEach { deleteTransaction(it) }
    book.debts.filter { it.splitOf == id && !it.repayment }.forEach { d -> edit { copy(debts = debts.filterNot { it.id == d.id }) } }
    deleteTransaction(t)
    return { save(before.copy(prefs = book.prefs)) }
}

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}
