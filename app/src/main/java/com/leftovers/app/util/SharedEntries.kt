package com.leftovers.app.util

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Entries shared with someone: a readable summary for any chat, and a link that, opened on a phone with Leftovers,
 * offers to add them there. It's the same page and encoding as Money owed links ([SharedOwed]), marked as entries
 * ("t": "x"). Only what the entries say travels: amount, kind, day, category name and note; no account, no ids.
 */
object SharedEntries {
    /** Most entries one link carries; the text lists them all the same. */
    const val MAX = 40

    /** One shared entry: [income] or spending, on [epochDay], under the sender's [category] name. */
    data class Line(val amountMinor: Long, val income: Boolean, val epochDay: Long, val category: String, val note: String) {
        val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    }

    data class Shared(val currency: String, val lines: List<Line>)

    /** The link for [items] (the first [MAX] of them), or null when there are none. */
    fun link(currency: String, items: List<TransactionItem>): String? {
        val lines = items.filter { it.amountMinor > 0 }.take(MAX)
        if (lines.isEmpty()) return null
        val json = JSONObject().put("v", 1).put("t", "x").put("c", currency).put("x", JSONArray(lines.map { t ->
            JSONArray().put(t.amountMinor).put(if (t.type == TxType.INCOME) 1 else 0).put(t.epochDay).put(t.categoryName.trim().take(40)).put(t.note.trim().take(80))
        }))
        return SharedOwed.page + "#" + SharedOwed.encode(json.toString())
    }

    /**
     * What's sent with the link: one entry as "Dinner · ₹850 · 22 Nov", several as a line each and their totals.
     * Readable without Leftovers; the link is for adding them.
     */
    fun text(items: List<TransactionItem>, money: Money, link: String?): String = buildString {
        val f = DateTimeFormatter.ofPattern("d MMM")
        fun line(t: TransactionItem) = listOf(
            t.note.trim().ifBlank { t.categoryName },
            (if (t.type == TxType.INCOME) "+" else "") + money.format(t.amountMinor),
            t.date.format(f),
        ).joinToString(" · ") + if (t.note.isNotBlank() && t.note.trim() != t.categoryName) " (${t.categoryName})" else ""
        if (items.size == 1) {
            append(line(items[0]))
        } else {
            append("${items.size} entries from Leftovers:\n")
            items.forEach { append("• ").append(line(it)).append('\n') }
            val spent = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
            val earned = items.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
            append(listOfNotNull(
                if (spent > 0) "Spent ${money.format(spent)}" else null,
                if (earned > 0) "earned ${money.format(earned)}" else null,
            ).joinToString(", ").replaceFirstChar { it.uppercase() })
        }
        if (link != null) {
            append("\n\n")
            append(if (items.size > MAX) "Add the first $MAX to your Leftovers: " else "Add ${if (items.size == 1) "it" else "them"} to your Leftovers: ")
            append(link)
        }
    }

    /** Reads an entries link (the page's, or the app's own leftovers://owed?d=…); null when it isn't one. */
    fun parse(text: String): Shared? = runCatching {
        val code = text.trim().let { t ->
            when {
                "d=" in t.substringAfter('?', "") -> t.substringAfter("d=").substringBefore('&')
                '#' in t -> t.substringAfter('#')
                else -> t
            }
        }
        val json = JSONObject(SharedOwed.decode(code))
        if (json.optInt("v") != 1 || json.optString("t") != "x") return null
        val x = json.getJSONArray("x")
        val clean = Regex("[\\p{Cntrl}]")
        val lines = (0 until minOf(x.length(), MAX)).mapNotNull { i ->
            val e = x.optJSONArray(i) ?: return@mapNotNull null
            val amount = e.optLong(0)
            if (amount <= 0 || amount > 1_000_000_000_00L) return@mapNotNull null
            Line(amount, e.optInt(1) == 1, e.optLong(2), e.optString(3).replace(clean, "").trim().take(40), e.optString(4).replace(clean, " ").trim().take(80))
        }
        if (lines.isEmpty()) null else Shared(json.optString("c").take(3).uppercase(), lines)
    }.getOrNull()
}

/** Opens the share sheet for entries: the readable text, with the link to add them in Leftovers. */
object ShareEntries {
    fun open(context: android.content.Context, items: List<TransactionItem>, money: Money) {
        if (items.isEmpty()) return
        val sorted = items.sortedWith(compareByDescending<TransactionItem> { it.epochDay }.thenByDescending { it.createdAt })
        val link = SharedEntries.link(money.currency.currencyCode, sorted)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, SharedEntries.text(sorted, money, link))
            putExtra(android.content.Intent.EXTRA_SUBJECT, if (items.size == 1) "An entry from Leftovers" else "${items.size} entries from Leftovers")
        }
        runCatching { context.startActivity(android.content.Intent.createChooser(send, if (items.size == 1) "Share entry" else "Share ${items.size} entries")) }
    }
}
