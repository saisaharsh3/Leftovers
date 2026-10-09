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
    private val debitWords = Regex(
        """\b(debited|spent|paid|sent|withdrawn|purchase|txn of|debit|thank you for using|used at|used for)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditOnly = Regex("""\b(credited|received|refund|cashback)\b""", RegexOption.IGNORE_CASE)
    /**
     * Messages that aren't a completed payment. Banks add "never share your OTP" and "if you have not requested
     * this" to real alerts, so only an actual OTP, a collect request or a reminder counts here.
     */
    private val ignore = Regex(
        """(\bis your (otp|one time password)\b|\b(otp|one time password) (is|for)\b|\bverification code\b|""" +
            """\bhas requested\b|\brequested money\b|\bcollect request\b|\bwill be debited\b|\b(payment|amount|minimum) due\b|""" +
            """\bdue (date|on)\b|\b(declined|failed|unsuccessful)\b)""",
        RegexOption.IGNORE_CASE,
    )
    /** "debited by 500.0" (SBI and others write the amount with no currency). */
    private val bareAmount = Regex(
        """\b(?:debited|spent|paid|sent|withdrawn|credited|received)\s+(?:by|with|for|of)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        RegexOption.IGNORE_CASE,
    )
    /** The account or card digits a message names, e.g. "A/C *1234", "Acct XX234", "Card ending 1234". */
    private val accountRef = Regex(
        """\b(?:a/c|ac|acct|account|card)(?:\s*(?:no\.?|number))?(?:\s+ending(?:\s+in|\s+with)?)?[\s:.-]*[xX*]*\s*(\d{3,18})\b""",
        RegexOption.IGNORE_CASE,
    )

    /** The last digits of the account or card in [body], if it names one. */
    fun accountDigits(body: String): String? = accountRef.find(body)?.groupValues?.get(1)?.takeLast(4)
    private val merchant = Regex(
        """\b(?:at|to|towards|vpa|info:?)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|via|ref\w*|upi|txn|avl|avail|from|using)\b|\s*[(\[]|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )
    /** The payee in a UPI narration: "UPI/P2A/971133155005/DHULIPALA S N V S K" or "UPI/DR/123456/SWIGGY/YESB". */
    private val upiNarration = Regex("""\bUPI/(?:[A-Z0-9]{1,6}/)*\d{6,}/([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)\s*(?=/|\n|$|\s{2})""", RegexOption.IGNORE_CASE)
    /** "to block UPI", "to report", "at your branch": footer phrases, not a payee. */
    private val notPayee = Regex(
        """^(block|report|avoid|know|view|check|call|contact|dial|sms|visit|reach|login|log|unsubscribe|update|change|help|keep|protect|ensure|receive|stop|your|you|us|our|the|this|be|any|all|bank|branch)\b|^\+?\d{6,}""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Who was paid, from bank message or email [text]: the UPI narration first, then "to X" / "at X",
     * skipping footer phrases. Empty when nobody is named.
     */
    fun payee(text: String, words: Regex = merchant): String {
        val candidates = sequenceOf(upiNarration.find(text)) + words.findAll(text)
        return candidates.filterNotNull().map { m ->
            m.groupValues[1].trim()
                .substringBefore('@')
                .replace(Regex("""^(vpa|upi|merchant)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
        }.firstOrNull {
            it.length >= 2 && !it.contains("a/c", true) && !it.matches(Regex("""[xX*\d ]+""")) && !notPayee.containsMatchIn(it)
        }?.let(::tidyName).orEmpty()
    }

    /** "ZOMATO" → "Zomato", "DHULIPALA S N V S K" → "Dhulipala S N V S K"; mixed case stays as written. */
    private fun tidyName(name: String): String {
        val shouted = name.any { it.isLetter() } && name == name.uppercase()
        val words = if (shouted) name.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } else name
        return words.replaceFirstChar { it.uppercase() }
    }

    /** [merchant] is who was paid, or for [isIncome] who the money came from. */
    data class Parsed(val amountMinor: Long, val merchant: String, val isIncome: Boolean = false)

    /** Who money came from: "received from AL RAJHI B MCB in your A/C", "credited from VPA x@y on". */
    private val creditFrom = Regex(
        """\b(?:from|sender:?)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|in|into|via|ref\w*|upi|txn|to|at|for|a/c|ac|acct)\b|\s*[(\[]|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )

    /** Who money coming in was from, in a bank message or email; empty when it doesn't say. */
    fun payer(text: String): String = payee(text, creditFrom)

    /** What the parser made of a message, with a plain reason; used by the "Test a message" box. */
    data class Check(val parsed: Parsed?, val reason: String)

    /** [extraWords] are the user's own keywords that also mark a message as a payment. */
    fun parse(body: String, extraWords: Collection<String> = emptyList()): Parsed? = check(body, extraWords).parsed

    fun check(body: String, extraWords: Collection<String> = emptyList()): Check {
        ignore.find(body)?.let { return Check(null, "Skipped: it says \"${it.value}\", like an OTP, a reminder or a failed payment.") }
        // "credited" or "received" without "debited" is money coming in, suggested as income.
        val income = creditOnly.containsMatchIn(body) && !Regex("debited", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val custom = extraWords.firstOrNull { it.isNotBlank() && body.contains(it.trim(), ignoreCase = true) }
        if (!income && !debitWords.containsMatchIn(body) && custom == null) {
            return Check(null, "Not a payment: no word like \"debited\", \"spent\", \"paid\" or \"credited\". If your bank uses another word, add it as a keyword.")
        }
        // Any currency, symbol or code, before or after the number (see MoneyText).
        val minor = MoneyText.find(body)
            ?: bareAmount.find(body)?.groupValues?.get(1)?.let(MoneyText::toMinor)
            ?: return Check(null, "No amount found. It needs a currency next to the number, like Rs 250, $12.50 or 45 AED.")
        if (income) return Check(Parsed(minor, payer(body), isIncome = true), "Money in")
        val who = payee(body)
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
                if (!s.smsDetection) return@launch
                // The user's own keywords count too, for banks the built-in words miss.
                val check = SmsParser.check(body, s.detectionKeywords)
                val parsed = check.parsed
                if (parsed != null) {
                    val added = container.sms.add(
                        SmsSuggestion(
                            amountMinor = parsed.amountMinor,
                            merchant = parsed.merchant,
                            sender = sender,
                            epochDay = LocalDate.now().toEpochDay(),
                            // Keep only a fingerprint for de-duplication, never the message itself
                            // (bank SMS carry account digits and balances).
                            body = fingerprint(body),
                            // Just the last digits, to pick the matching account.
                            accountDigits = SmsParser.accountDigits(body),
                            isIncome = parsed.isIncome,
                        ),
                    )
                    val amount = (if (parsed.isIncome) "income " else "") + Money(s.currencyCode).format(parsed.amountMinor)
                    DetectionLog.add(context, "SMS", sender, if (added) "Suggested $amount" else "$amount was already logged or suggested")
                } else if (MoneyText.find(body) != null) {
                    // Only messages with an amount are worth noting; personal texts leave no trace.
                    DetectionLog.add(context, "SMS", sender, check.reason)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
