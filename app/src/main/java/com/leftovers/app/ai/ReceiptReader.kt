package com.leftovers.app.ai

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Reads one bill or receipt photo with the connected AI, to fill in the add screen: the total paid, the date,
 * a category from the user's own list and a short note. Only the photo and the category names are sent.
 */
object ReceiptReader {
    data class Reading(val amountMinor: Long, val date: LocalDate?, val category: String?, val note: String?, val isIncome: Boolean)

    fun prompt(expenseCategories: List<String>, incomeCategories: List<String>, today: LocalDate = LocalDate.now()): String = buildString {
        append("You read photos of bills and receipts for an expense tracker. Today is $today. ")
        append("Reply with JSON only, no other text, in this form: ")
        append("""{"amount": 0.0, "date": "YYYY-MM-DD", "category": "", "note": "", "type": "expense"}. """)
        append("amount is the total actually paid, after tax and discounts, as a plain number. ")
        append("date is the date printed on the bill, or null if there isn't one. ")
        append("category is the best match from this list, or null: ${expenseCategories.joinToString()}. ")
        append("If it's money received (a refund or a payment to the user), set type to income and pick from: ${incomeCategories.joinToString()}. ")
        append("note is the shop or what was bought, at most four words, or null. ")
        append("""If the photo isn't a bill or receipt, or there's no total, reply {"amount": null}.""")
    }

    /** What the AI's reply says, or null when there's no usable amount. Tolerates ```json fences and extra text. */
    fun parse(reply: String, today: LocalDate = LocalDate.now()): Reading? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(reply.substring(start, end + 1)) }.getOrNull() ?: return null
        if (o.isNull("amount")) return null
        val amount = runCatching { BigDecimal(o.get("amount").toString().replace(",", "")) }.getOrNull() ?: return null
        val minor = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        if (minor <= 0 || minor > 1_000_000_000L) return null
        // A date the bill can't have (tomorrow, or years ago) is dropped rather than trusted.
        val date = o.optString("date").takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?.takeIf { !it.isAfter(today) && it.isAfter(today.minusYears(2)) }
        fun text(key: String) = o.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
        return Reading(
            amountMinor = minor,
            date = date,
            category = text("category"),
            note = text("note")?.take(40),
            isIncome = o.optString("type").equals("income", ignoreCase = true),
        )
    }

    suspend fun read(config: AssistantConfig, image: ImageInput, expenseCategories: List<String>, incomeCategories: List<String>): Reading? {
        val session = chatSession(config, prompt(expenseCategories, incomeCategories), emptyList())
        return parse(session.sendUser("Read this bill.", image).text)
    }
}
