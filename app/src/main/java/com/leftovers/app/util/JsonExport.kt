package com.leftovers.app.util

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Another app's JSON backup or export turned into rows for [CsvImporter.parseTable], whichever app made it.
 * The entries are the largest list of records that each have a date and an amount; ids pointing into other lists
 * (a category or account id) are swapped for that record's name, so entries keep their category and account.
 */
object JsonExport {
    private val dateKey = Regex("""^(date|datetime|date_time|transactiondate|txndate|time|timestamp|createdat|created|when|day)$""")
    private val amountKey = Regex("""^(amount|amt|value|sum|total|price|money)$""")
    private val nameKeys = listOf("name", "title", "label")

    private fun norm(key: String) = key.lowercase().replace(Regex("""[^a-z0-9]"""), "")

    /** Rows with a header first; null when no list in [json] looks like entries. */
    fun rows(json: String): List<List<String>>? {
        val root = runCatching { JSONTokener(json.trim()).nextValue() }.getOrNull() ?: return null
        val lists = mutableListOf<Pair<String, List<JSONObject>>>()
        collect(root, "", lists)
        // The list with the most dated, priced records. Counting those (not the list's length, nor requiring most of
        // it to be dated) matters for apps that keep planned payments in the same list without a date yet, like Ivy
        // Wallet: its transactions can be mostly planned ones, and they're still the entries, not its few loan records.
        val entries = lists.map { (path, items) -> Triple(path, items, datedAndPriced(items)) }
            .filter { it.third > 0 }.maxByOrNull { it.third }?.second ?: return null

        // Lookups for references: "categoryId": "abc" → that category's name, found in a list named after it.
        val named = lists.filter { (_, items) -> items.isNotEmpty() && items.all { it.has("id") } && items.any { o -> nameKeys.any(o::has) } }
        fun nameFor(refKey: String, id: String): String? {
            val base = norm(refKey).removeSuffix("ids").removeSuffix("id").removeSuffix("uuid")
            val candidates = named.sortedByDescending { (path, _) -> if (base.isNotEmpty() && norm(path).contains(base)) 1 else 0 }
            for ((path, items) in candidates) {
                if (base.isNotEmpty() && !norm(path).contains(base) && candidates.any { norm(it.first).contains(base) }) continue
                val hit = items.firstOrNull { it.opt("id")?.toString() == id } ?: continue
                return nameKeys.firstNotNullOfOrNull { k -> hit.optString(k).takeIf { it.isNotBlank() } }
            }
            return null
        }

        val keys = linkedSetOf<String>()
        entries.forEach { o -> o.keys().forEach(keys::add) }
        val columns = keys.filter { k -> entries.any { o -> o.opt(k).let { it != null && it !is JSONArray && it != JSONObject.NULL } } }
        // The header CsvImporter understands: the date and amount by their usual names, references by what they point to.
        val dateColumn = columns.firstOrNull { dateKey.matches(norm(it)) && entries.any { o -> CsvImporter.parseDate(cell(o.opt(it))) != null } }
        val header = columns.map { k ->
            val n = norm(k)
            when {
                k == dateColumn -> "date"
                amountKey.matches(n) -> "amount"
                n.endsWith("id") && n.length > 2 -> n.removeSuffix("uuid").removeSuffix("id")
                else -> k
            }
        }
        // Tags kept as a list on each entry (names, or ids pointing at a list of tags) become one "tags" column.
        val tagKeys = keys.filter { k -> Regex("""^(tags?|tagids?|labels?|labelids?|hashtags)$""").matches(norm(k)) && entries.any { it.opt(k) is JSONArray } }
        fun tagsOf(o: JSONObject): String = tagKeys.flatMap { key ->
            val arr = o.opt(key) as? JSONArray ?: return@flatMap emptyList()
            (0 until arr.length()).mapNotNull { i ->
                when (val t = arr.opt(i)) {
                    is JSONObject -> nameKeys.firstNotNullOfOrNull { nk -> t.optString(nk).takeIf { it.isNotBlank() } }
                    null, JSONObject.NULL -> null
                    else -> if (norm(key).endsWith("ids")) nameFor(key, t.toString()) else t.toString()
                }
            }
        }.joinToString(",") { it.trim().replace(Regex("""\s+"""), "-") } // one tag stays one tag, even with a space in it
        val tagged = tagKeys.isNotEmpty() && columns.none { norm(it) in setOf("tags", "tag") }
        val body = entries.map { o ->
            (if (tagged) listOf(tagsOf(o)) else emptyList()) + columns.map { k ->
                val v = o.opt(k)
                val n = norm(k)
                when {
                    v is JSONObject -> nameKeys.firstNotNullOfOrNull { nk -> v.optString(nk).takeIf { it.isNotBlank() } }.orEmpty()
                    n.endsWith("id") && n.length > 2 && v != null && v != JSONObject.NULL -> nameFor(k, v.toString()) ?: ""
                    else -> cell(v)
                }
            }
        }
        return listOf((if (tagged) listOf("tags") else emptyList()) + header) + body
    }

    private fun cell(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is Double -> java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
        else -> v.toString()
    }

    private fun collect(node: Any?, path: String, out: MutableList<Pair<String, List<JSONObject>>>) {
        when (node) {
            is JSONObject -> node.keys().forEach { k -> collect(node.opt(k), if (path.isEmpty()) k else "$path.$k", out) }
            is JSONArray -> {
                val objects = (0 until node.length()).mapNotNull { node.opt(it) as? JSONObject }
                if (objects.isNotEmpty()) out += path to objects
                objects.forEachIndexed { i, o -> if (i < 3) o.keys().forEach { k -> (o.opt(k) as? JSONArray)?.let { collect(it, "$path.$k", out) } } }
            }
        }
    }

    /** How many of [items] have both a readable date and a readable amount. */
    private fun datedAndPriced(items: List<JSONObject>): Int =
        items.count { o ->
            val keys = o.keys().asSequence().toList()
            keys.any { k -> dateKey.matches(norm(k)) && CsvImporter.parseDate(cell(o.opt(k))) != null } &&
                keys.any { k -> amountKey.matches(norm(k)) && CsvImporter.parseAmount(cell(o.opt(k))) != null }
        }
}
