package com.leftovers.app.util

import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Entries this phone deleted or changed, so Sync between devices can take them off the other phones too.
 * An entry is known by its day, amount, kind and note (as when a backup is merged), and [Removed.at] says when
 * it went: only entries made before then are removed elsewhere, so a new entry that happens to look the same
 * isn't. Kept for 90 days, in the app's private files; nothing here leaves the phone unless sync is on.
 */
class SyncRemoved(private val file: File) {
    data class Removed(val day: Long, val amount: Long, val type: TxType, val note: String, val at: Long) {
        fun matches(t: Transaction) = sameEntry(t) && t.createdAt <= at
        fun sameEntry(t: Transaction) = t.epochDay == day && t.amountMinor == amount && t.type == type && key(t.note) == key(note)
    }

    @Synchronized
    fun all(now: Long = System.currentTimeMillis()): List<Removed> {
        val list = runCatching { if (file.exists()) decode(JSONArray(file.readText())) else emptyList() }.getOrDefault(emptyList())
        return list.filter { now - it.at < KEEP_MS }
    }

    /** Notes that [t] is gone from this phone (deleted, or changed into something else). */
    @Synchronized
    fun add(t: Transaction, now: Long = System.currentTimeMillis()) =
        write(all(now).filterNot { it.sameEntry(t) } + Removed(t.epochDay, t.amountMinor, t.type, t.note, now))

    /** [t] is here again (Undo, restored, or saved back as it was), so it mustn't be removed elsewhere. */
    @Synchronized
    fun forget(t: Transaction, now: Long = System.currentTimeMillis()) {
        val list = all(now)
        if (list.any { it.sameEntry(t) }) write(list.filterNot { it.sameEntry(t) })
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }

    private fun write(list: List<Removed>) {
        runCatching { file.writeText(encode(list.takeLast(MAX)).toString()) }
    }

    companion object {
        private val KEEP_MS = TimeUnit.DAYS.toMillis(90)
        private const val MAX = 2_000

        fun key(note: String) = note.trim().lowercase()

        fun encode(list: List<Removed>) = JSONArray(list.map {
            JSONObject().put("day", it.day).put("amount", it.amount).put("type", it.type.name).put("note", it.note).put("at", it.at)
        })

        fun decode(array: JSONArray?): List<Removed> = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val o = array!!.optJSONObject(i) ?: return@mapNotNull null
            runCatching { Removed(o.getLong("day"), o.getLong("amount"), TxType.valueOf(o.getString("type")), o.optString("note"), o.getLong("at")) }.getOrNull()
        }
    }
}
