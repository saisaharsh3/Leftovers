package com.leftovers.app.util

import com.leftovers.app.data.Debt
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Money owed, sent to a friend who also uses Leftovers: a link in the message you send them carries the open entries
 * between you, and their app adds them the other way round (what you lent, they borrowed). Nothing goes through a
 * server: the entries are in the link itself, after the "#", which a browser never sends anywhere.
 */
object SharedOwed {
    /** The page the link opens, which hands the entries to the app (or says where to get it). */
    const val PAGE = "https://saisaharsh3.github.io/Leftovers/add"
    const val SCHEME = "leftovers"
    const val HOST = "owed"

    /** One entry as the sender has it: [amountMinor] above zero when the receiver owes the sender. */
    data class Line(val id: String, val amountMinor: Long, val epochDay: Long, val note: String, val repayment: Boolean)

    data class Shared(val currency: String, val lines: List<Line>) {
        /** What the receiver owes in all (above zero) or is owed (below). */
        val netMinor: Long get() = lines.sumOf { it.amountMinor }
    }

    /**
     * The link for [debts], the open entries with one person. Each entry keeps one id however often it's sent (and
     * the id it came with, when it was received from them), so nobody gets the same entry twice.
     */
    fun link(device: String, currency: String, debts: List<Debt>): String? {
        val lines = debts.filter { it.amountMinor != 0L }.take(40)
        if (lines.isEmpty()) return null
        val json = JSONObject().put("v", 1).put("c", currency).put("e", JSONArray(lines.map { d ->
            JSONArray().put(idOf(device, d)).put(d.amountMinor).put(d.epochDay).put(d.note.trim().take(60)).put(if (d.repayment) 1 else 0)
        }))
        return "$PAGE#" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    fun idOf(device: String, debt: Debt): String =
        debt.sharedId ?: (device.takeLast(6) + "." + debt.createdAt.toString(36))

    /** Reads a link, the app's own (leftovers://owed?d=…) or the page's (…/add#…); null when it isn't one. */
    fun parse(text: String): Shared? = runCatching {
        val code = text.trim().let { t ->
            when {
                "d=" in t.substringAfter('?', "") -> t.substringAfter("d=").substringBefore('&')
                '#' in t -> t.substringAfter('#')
                else -> t
            }
        }
        val json = JSONObject(String(Base64.getUrlDecoder().decode(code)))
        if (json.optInt("v") != 1) return null
        val entries = json.getJSONArray("e")
        val lines = (0 until minOf(entries.length(), 40)).map { i ->
            val e = entries.getJSONArray(i)
            Line(e.getString(0).take(40), e.getLong(1), e.getLong(2), e.optString(3).take(60), e.optInt(4) == 1)
        }.filter { it.id.isNotBlank() && it.amountMinor != 0L }
        if (lines.isEmpty()) null else Shared(json.optString("c").take(3).uppercase(), lines)
    }.getOrNull()

    /** [line] as the receiver's own entry with [person], who sent it: the other way round, and in no account. */
    fun mirror(line: Line, person: String): Debt =
        Debt(person = person.trim(), amountMinor = -line.amountMinor, note = line.note, epochDay = line.epochDay, repayment = line.repayment, sharedId = line.id)
}
