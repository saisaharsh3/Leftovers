package com.leftovers.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Chats with the assistant, kept on this phone to come back to: in one file in the app's private storage, never in
 * backups or sent anywhere. Only the words are kept (a photo is noted, not saved), and the newest [MAX] chats.
 * Turning it off deletes them all.
 */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chats.json")
    private val prefs = context.getSharedPreferences("assistant_chats", Context.MODE_PRIVATE)

    /** One saved message, as it showed in the chat: [type] u (you), b (the AI), c (a change), s (what it looked at), n (a note). */
    data class Line(val type: Char, val text: String, val kind: String = "", val state: String = "", val error: Boolean = false)

    data class Saved(val id: Long, val title: String, val updated: Long, val lines: List<Line>)

    var enabled: Boolean
        get() = prefs.getBoolean(KEEP, true)
        set(value) {
            prefs.edit().putBoolean(KEEP, value).apply()
            if (!value) deleteAll()
        }

    @Synchronized
    fun list(): List<Saved> = runCatching {
        if (!file.exists()) return emptyList()
        val all = JSONArray(file.readText())
        (0 until all.length()).map { i ->
            val o = all.getJSONObject(i)
            val ls = o.getJSONArray("l")
            Saved(o.getLong("id"), o.optString("t"), o.optLong("at"), (0 until ls.length()).map { k ->
                val l = ls.getJSONObject(k)
                Line(l.optString("y", "n").firstOrNull() ?: 'n', l.optString("x"), l.optString("k"), l.optString("s"), l.optBoolean("e"))
            })
        }.sortedByDescending { it.updated }
    }.getOrDefault(emptyList())

    /** Adds [chat], or replaces the one with its id, keeping the newest [MAX]. */
    @Synchronized
    fun save(chat: Saved) {
        if (!enabled || chat.lines.none { it.type == 'u' }) return
        write((listOf(chat) + list().filter { it.id != chat.id }).sortedByDescending { it.updated }.take(MAX))
    }

    @Synchronized
    fun delete(id: Long) = write(list().filter { it.id != id })

    @Synchronized
    fun deleteAll() { file.delete() }

    private fun write(chats: List<Saved>) {
        val json = JSONArray(chats.map { c ->
            JSONObject().put("id", c.id).put("t", c.title).put("at", c.updated).put("l", JSONArray(c.lines.map { l ->
                JSONObject().put("y", l.type.toString()).put("x", l.text).apply {
                    if (l.kind.isNotEmpty()) put("k", l.kind)
                    if (l.state.isNotEmpty()) put("s", l.state)
                    if (l.error) put("e", true)
                }
            }))
        })
        // Written whole to a new file first, so a phone switched off mid-write never leaves half a file.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private companion object {
        const val KEEP = "keep"
        const val MAX = 40
    }
}
