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
    private val amount = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val merchant = Regex(
        """\b(?:at|to|towards|vpa|info:?)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|via|ref|upi|txn|avl|avail|from|using)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )

    data class Parsed(val amountMinor: Long, val merchant: String)

    fun parse(body: String): Parsed? {
        if (ignore.containsMatchIn(body)) return null
        if (!debitWords.containsMatchIn(body)) return null
        // "credited" alone means money came in; ignore unless the message also says debited.
        if (creditOnly.containsMatchIn(body) && !Regex("debited", RegexOption.IGNORE_CASE).containsMatchIn(body)) return null
        val value = amount.find(body)?.groupValues?.get(1)?.replace(",", "") ?: return null
        val minor = runCatching { BigDecimal(value).movePointRight(2).toLong() }.getOrNull() ?: return null
        if (minor <= 0) return null
        val who = merchant.find(body)?.groupValues?.get(1)?.trim()
            ?.substringBefore('@')
            ?.replace(Regex("""^(vpa|upi|merchant)\s+""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.length >= 2 && !it.contains("a/c", true) && !it.matches(Regex("""[xX*\d ]+""")) }
            ?.replaceFirstChar { it.uppercase() }
            .orEmpty()
        return Parsed(minor, who)
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
        val parsed = SmsParser.parse(body) ?: return
        val container = (context.applicationContext as LeftoversApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (container.settings.settings.first().smsDetection) {
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
