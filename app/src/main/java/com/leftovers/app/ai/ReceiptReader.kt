package com.leftovers.app.ai

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Reads one bill or receipt photo with the connected AI, to fill in the add screen: the total paid, the date,
 * a category from the user's own list, a short note and the account it was paid from. Only the photo and the
 * category and account names are sent (account names with any numbers hidden).
 */
object ReceiptReader {
    /** [paidBy] is how the bill says it was paid: cash, card, upi or bank; [account] the user's account it named. */
    data class Reading(
        val amountMinor: Long,
        val date: LocalDate?,
        val category: String?,
        val note: String?,
        val isIncome: Boolean,
        val paidBy: String? = null,
        val account: String? = null,
    )

    fun prompt(expenseCategories: List<String>, incomeCategories: List<String>, accounts: List<String> = emptyList(), today: LocalDate = LocalDate.now()): String = buildString {
        append("You read photos of bills and receipts for an expense tracker. Today is $today. ")
        append("Reply with JSON only, no other text, in this form: ")
        append("""{"amount": 0.0, "date": "YYYY-MM-DD", "category": "", "note": "", "type": "expense", "paid_by": "", "account": ""}. """)
        append("amount is the total actually paid, after tax and discounts, as a plain number. ")
        append("date is the date printed on the bill, or null if there isn't one. ")
        append("category is the best match from this list, or null: ${expenseCategories.joinToString()}. ")
        append("If it's money received (a refund or a payment to the user), set type to income and pick from: ${incomeCategories.joinToString()}. ")
        append("note is the shop or what was bought, at most four words, or null. ")
        append("paid_by is how the bill says it was paid: cash, card, upi or bank, or null if it doesn't say. ")
        if (accounts.isNotEmpty()) append("account is the best match for how it was paid from these accounts, or null: ${accounts.joinToString()}. ")
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
            paidBy = text("paid_by")?.lowercase()?.let { p ->
                when {
                    "cash" in p -> "cash"
                    "card" in p || "credit" in p || "debit" in p -> "card"
                    "upi" in p || "gpay" in p || "phonepe" in p || "paytm" in p -> "upi"
                    "bank" in p || "neft" in p || "imps" in p || "net" in p -> "bank"
                    else -> null
                }
            },
            account = text("account"),
        )
    }

    suspend fun read(config: AssistantConfig, image: ImageInput, expenseCategories: List<String>, incomeCategories: List<String>, accounts: List<String> = emptyList()): Reading? {
        val session = chatSession(config, prompt(expenseCategories, incomeCategories, accounts), emptyList())
        return parse(session.sendUser("Read this bill.", image).text)
    }

    /**
     * The account a bill was paid from: the one the AI named, else one that fits how it was paid (cash, card,
     * UPI or bank) by its icon or name, preferring [defaultId]. Null when nothing fits, so the choice stays as it is.
     */
    fun pickAccount(reading: Reading, accounts: List<Triple<Long, String, String>>, defaultId: Long?): Long? {
        reading.account?.let { named -> accounts.firstOrNull { it.second.equals(named, ignoreCase = true) }?.let { return it.first } }
        val fits: (Triple<Long, String, String>) -> Boolean = when (reading.paidBy) {
            "cash" -> { a -> a.third == "banknote" || a.second.contains("cash", ignoreCase = true) }
            "card" -> { a -> a.third == "credit-card" || a.second.contains("card", ignoreCase = true) }
            "upi", "bank" -> { a -> a.third == "landmark" || a.third == "smartphone" || a.second.contains("bank", ignoreCase = true) || a.second.contains("upi", ignoreCase = true) }
            else -> return null
        }
        val matching = accounts.filter(fits)
        return matching.firstOrNull { it.first == defaultId }?.first ?: matching.firstOrNull()?.first
    }
}
