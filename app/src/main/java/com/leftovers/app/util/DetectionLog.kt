package com.leftovers.app.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last few things SMS and email detection did, so the user can see why something was or wasn't
 * picked up. Only the sender, a short result and the time are kept, never the message itself, and only
 * for messages that had an amount in them.
 */
object DetectionLog {
    private const val PREFS = "detection_log"
    private const val KEY = "entries"
    private const val KEEP = 15

    data class Entry(val at: Long, val source: String, val from: String, val result: String)

    @Synchronized
    fun add(context: Context, source: String, from: String, result: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = JSONArray(prefs.getString(KEY, "[]"))
        val updated = JSONArray().put(JSONObject().put("at", System.currentTimeMillis()).put("source", source).put("from", from.take(40)).put("result", result.take(120)))
        for (i in 0 until minOf(list.length(), KEEP - 1)) updated.put(list.getJSONObject(i))
        prefs.edit().putString(KEY, updated.toString()).apply()
    }

    fun read(context: Context): List<Entry> {
        val list = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        return (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            Entry(o.getLong("at"), o.getString("source"), o.getString("from"), o.getString("result"))
        }
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
