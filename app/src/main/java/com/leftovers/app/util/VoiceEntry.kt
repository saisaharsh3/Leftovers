package com.leftovers.app.util

/** What was understood from a spoken entry like "250 for lunch". */
data class SpokenEntry(val amountMinor: Long?, val note: String, val categoryId: Long?)

/** Turns a speech-to-text result into an amount, a note and, when it's clear, a category. */
object VoiceEntry {
    private val number = Regex("""(\d[\d,]*(?:\.\d{1,2})?)\s*(k\b|thousand\b)?""", RegexOption.IGNORE_CASE)
    private val filler = setOf(
        "add", "spent", "spend", "paid", "pay", "expense", "for", "on", "of", "a", "an", "the",
        "rupees", "rupee", "rs", "inr", "dollars", "dollar", "bucks", "euros", "euro", "pounds",
    )

    /** Everyday words that point to a default category, by its name. */
    private val hints = mapOf(
        "food" to listOf("lunch", "dinner", "breakfast", "coffee", "tea", "chai", "snack", "snacks", "food", "restaurant", "swiggy", "zomato", "pizza", "cake"),
        "transport" to listOf("uber", "ola", "auto", "bus", "metro", "taxi", "cab", "fuel", "petrol", "diesel", "parking", "train"),
        "groceries" to listOf("grocery", "groceries", "vegetables", "milk", "fruits", "supermarket", "dmart", "blinkit", "zepto"),
        "shopping" to listOf("amazon", "flipkart", "clothes", "shoes", "shirt", "myntra"),
        "bills" to listOf("electricity", "recharge", "wifi", "internet", "water", "gas", "bill"),
        "rent" to listOf("rent"),
        "health" to listOf("medicine", "medicines", "doctor", "pharmacy", "hospital", "gym"),
        "entertainment" to listOf("movie", "movies", "netflix", "spotify", "concert", "game"),
        "travel" to listOf("flight", "hotel", "trip"),
    )

    fun parse(text: String, categories: List<Pair<Long, String>>): SpokenEntry {
        val match = number.find(text)
        val amountMinor = match?.let {
            val minor = AmountInput.toMinor(it.groupValues[1].replace(",", ""))
            if (it.groupValues[2].isNotEmpty()) minor?.times(1000) else minor
        }
        val rest = (if (match != null) text.removeRange(match.range) else text)
            .replace("₹", " ").replace("$", " ")
            .split(Regex("""\s+""")).filter { it.isNotBlank() }
        val words = rest.dropWhile { it.lowercase() in filler }
        val note = words.joinToString(" ").trim().replaceFirstChar { it.uppercase() }
        return SpokenEntry(amountMinor, note, categoryFor(text.lowercase(), categories))
    }

    private fun categoryFor(text: String, categories: List<Pair<Long, String>>): Long? {
        val words = text.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()
        // A category named outright wins ("groceries", "health").
        categories.firstOrNull { (_, name) -> name.lowercase().split(Regex("""[^\p{L}]+""")).any { it.length > 2 && it in words } }
            ?.let { return it.first }
        for ((hint, keys) in hints) {
            if (keys.none { it in words }) continue
            categories.firstOrNull { (_, name) -> name.lowercase().contains(hint) }?.let { return it.first }
        }
        return null
    }
}
