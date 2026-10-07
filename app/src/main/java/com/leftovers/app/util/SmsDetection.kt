package com.leftovers.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.SmsSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Recognises "money left your account" bank/UPI messages, e.g.
 * "Rs.250.00 debited from A/c XX1234 to VPA swiggy@icici on 05-10-26".
 */
object SmsParser {
    private val debitWords = Regex("""\b(debited|spent|paid|sent|withdrawn|purchase|txn of|debit)\b""", RegexOption.IGNORE_CASE)
    private val creditOnly = Regex("""\b(credited|received|refund|cashback)\b""", RegexOption.IGNORE_CASE)
    private val ignore = Regex("""\b(otp|one time password|due|will be debited|requested|declined|failed)\b""", RegexOption.IGNORE_CASE)
    private val merchant = Regex(
        """\b(?:at|to|towards|vpa|info:?)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|via|ref|upi|txn|avl|avail|from|using)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )

    data class Parsed(val amountMinor: Long, val merchant: String)

    /** What the parser made of a message, with a plain reason; used by the "Test a message" box. */
    data class Check(val parsed: Parsed?, val reason: String)

    /** [extraWords] are the user's own keywords that also mark a message as a payment. */
    fun parse(body: String, extraWords: Collection<String> = emptyList()): Parsed? = check(body, extraWords).parsed

    fun check(body: String, extraWords: Collection<String> = emptyList()): Check {
        ignore.find(body)?.let { return Check(null, "Skipped: it says \"${it.value}\", like an OTP, a reminder or a failed payment.") }
        val custom = extraWords.firstOrNull { it.isNotBlank() && body.contains(it.trim(), ignoreCase = true) }
        if (!debitWords.containsMatchIn(body) && custom == null) {
            return Check(null, "Not a payment: no word like \"debited\", \"spent\" or \"paid\". If your bank uses another word, add it as a keyword.")
        }
        // "credited" alone means money came in; ignore unless the message also says debited.
        if (creditOnly.containsMatchIn(body) && !Regex("debited", RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            return Check(null, "Skipped: it's money coming in (credited, received or refund).")
        }
        // Any currency, symbol or code, before or after the number (see MoneyText).
        val minor = MoneyText.find(body)
            ?: return Check(null, "No amount found. It needs a currency next to the number, like Rs 250, $12.50 or 45 AED.")
        val who = merchant.find(body)?.groupValues?.get(1)?.trim()
            ?.substringBefore('@')
            ?.replace(Regex("""^(vpa|upi|merchant)\s+""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.length >= 2 && !it.contains("a/c", true) && !it.matches(Regex("""[xX*\d ]+""")) }
            ?.replaceFirstChar { it.uppercase() }
            .orEmpty()
        val via = if (custom != null && !debitWords.containsMatchIn(body)) " (found by your keyword \"${custom.trim()}\")" else ""
        return Check(Parsed(minor, who), "Payment$via")
    }
}

private fun fingerprint(text: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val container = (context.applicationContext as LeftoversApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val s = container.settings.settings.first()
                // The user's own keywords count too, for banks the built-in words miss.
                val parsed = if (s.smsDetection) SmsParser.parse(body, s.detectionKeywords) else null
                if (parsed != null) {
                    container.sms.add(
                        SmsSuggestion(
                            amountMinor = parsed.amountMinor,
                            merchant = parsed.merchant,
                            sender = sender,
                            epochDay = LocalDate.now().toEpochDay(),
                            // Keep only a fingerprint for de-duplication, never the message itself
                            // (bank SMS carry account digits and balances).
                            body = fingerprint(body),
                        ),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
