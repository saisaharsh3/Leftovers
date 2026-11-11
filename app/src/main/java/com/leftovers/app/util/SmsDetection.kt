package com.leftovers.app.util

import com.leftovers.app.data.openBalances
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.data.matchFor
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
        // "Dr. from A/C" is Bank of Baroda's "debited".
        // "Transaction of", "payment of", "charged", "transferred" and "UPI txn" are how cards, billers and some banks say it.
        """\b(debited|spent|paid|sent|withdrawn|purchase|txn of|transaction of|payment of|debit|charged|transferred|upi txn|send money|cash ?out|thank you for using|used at|used for)\b|\bdr\.?\s+(from|to)\b""",
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
    /** Offers mention amounts too ("get Rs 100 cashback"): skipped unless the money actually moved. */
    private val promo = Regex("""(\d+\s?% off|\boffers?\b|\bsale\b|\bcoupon|\bvoucher|\bwin\b|\bup ?to\b|\bapply now\b|\bpre-?approved\b|\beligible\b)""", RegexOption.IGNORE_CASE)
    private val moved = Regex("""\b(debited|credited|spent|paid|sent|received|withdrawn|charged|transferred)\b""", RegexOption.IGNORE_CASE)
    /** "debited by 500.0" (SBI and others write the amount with no currency); Union Bank writes "Rs:200.00". */
    private val bareAmount = Regex(
        """\b(?:debited|spent|paid|sent|withdrawn|credited|received)\s+(?:by|with|for|of)?\s*(?:rs[.:]?|inr[.:]?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        RegexOption.IGNORE_CASE,
    )
    /** The account or card digits a message names, e.g. "A/C *1234", "Acct XX234", "Card ending 1234". */
    private val accountRef = Regex(
        """\b(?:a/c|ac|acct|account|card)(?:\s*(?:no\.?|number))?(?:\s+ending(?:\s+in|\s+with)?)?[\s:.-]*[xX*]*\s*(\d{3,18})\b""",
        RegexOption.IGNORE_CASE,
    )

    /** The last digits of the account or card in [body], if it names one. */
    fun accountDigits(body: String): String? = accountRef.find(body)?.groupValues?.get(1)?.takeLast(4)
    /** "to X", "at X", or the shop after the date, as ICICI cards write it: "on 07-Oct-26 on MYNTRA". */
    private val merchant = Regex(
        """(?:\b(?:at|to|towards|vpa|info:?)|(?<=\d{2}[-/ ][A-Za-z0-9]{2,3}[-/ ]\d{2,4}\s)on)\s+([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|via|ref\w*|upi|txn|avl|avail|from|using)\b|\s*[(\[]|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )
    /** The payee in a UPI narration: "UPI/P2A/971133155005/DHULIPALA S N V S K" or "UPI/DR/123456/SWIGGY/YESB". */
    private val upiNarration = Regex("""\bUPI/(?:[A-Z0-9]{1,6}/)*\d{6,}/([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)\s*(?=/|\n|$|\s{2}|\s+(?:on|via|ref\w*|avl|avail)\b|[,;]|\.\s)""", RegexOption.IGNORE_CASE)
    /** An autopay names who it's for: "auto-debited ... for NETFLIX mandate". */
    private val mandate = Regex("""\bfor\s+([A-Za-z][A-Za-z0-9 .&'-]{1,30}?)\s+(?:mandate|autopay|auto-?pay|subscription|standing instruction)\b""", RegexOption.IGNORE_CASE)
    /** "to block UPI", "to report", "at your branch": footer phrases, not a payee. */
    private val notPayee = Regex(
        """^(block|report|avoid|know|view|check|call|contact|dial|sms|visit|reach|login|log|unsubscribe|update|change|help|keep|protect|ensure|receive|stop|your|you|us|our|the|this|be|any|all|bank|branch|upi|card|wallet|credit|debit)\b|^\+?\d{6,}""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Who was paid, from bank message or email [text]: the UPI narration first, then "to X" / "at X",
     * skipping footer phrases. Empty when nobody is named.
     */
    fun payee(text: String, words: Regex = merchant): String {
        val candidates = sequenceOf(upiNarration.find(text), mandate.find(text)) + words.findAll(text)
        return candidates.filterNotNull().map { m ->
            m.groupValues[1].trim()
                .substringBefore('@')
                // bKash: "to 01XXXXXXXXX successful", "to Merchant is successful".
                .replace(Regex("""\s+(is\s+)?successful\w*$""", RegexOption.IGNORE_CASE), "")
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

    fun check(message: String, extraWords: Collection<String> = emptyList()): Check {
        // bKash writes "Tk. 100to Merchant": the amount runs into the next word.
        val body = message.replace(Regex("""(\d)(to|from)\b""", RegexOption.IGNORE_CASE), "$1 $2")
        ignore.find(body)?.let { return Check(null, "Skipped: it says \"${it.value}\", like an OTP, a reminder or a failed payment.") }
        // "credited" or "received" without "debited" is money coming in, suggested as income.
        val income = creditOnly.containsMatchIn(body) && !Regex("debited", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val custom = extraWords.firstOrNull { it.isNotBlank() && body.contains(it.trim(), ignoreCase = true) }
        if (promo.containsMatchIn(body) && !moved.containsMatchIn(body)) {
            return Check(null, "Skipped: it reads like an offer, not money that moved.")
        }
        if (!income && !debitWords.containsMatchIn(body) && custom == null) {
            return Check(null, "Not a payment: no word like \"debited\", \"spent\", \"paid\" or \"credited\". If your bank uses another word, add it as a keyword.")
        }
        // Any currency, symbol or code, before or after the number (see MoneyText).
        // Not the balance or the card's limit: "Avl Bal Rs.12,000 after debit of Rs.500" is 500.
        val minor = MoneyText.paid(body)
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

/**
 * Adds a just-detected payment as an entry when it's small enough to add without asking (see [AutoAdd]), and takes
 * its suggestion off Home. Returns whether it did.
 */
private suspend fun autoAdd(
    container: com.leftovers.app.AppContainer,
    s: com.leftovers.app.data.AppSettings,
    parsed: SmsParser.Parsed,
    body: String,
    suggestionId: Long,
): Boolean {
    val owed = container.planning.debts.first().openBalances().filter { it.netMinor != 0L }
    if (!AutoAdd.allowed(s.smsAutoAdd, s.smsAutoMaxMinor, parsed.amountMinor, parsed.isIncome, parsed.merchant, owed)) return false
    val past = container.repository.getAllTransactions().map { MerchantMemory.Past(it.note, it.type, it.categoryId, it.epochDay, it.createdAt) }
    val category = AutoAdd.category(parsed.merchant, past, container.repository.categories.first()) ?: return false
    val account = container.accounts.accounts.first().let { list -> list.matchFor(body)?.id ?: list.firstOrNull { it.id == s.defaultAccountId }?.id }
    val day = LocalDate.now()
    container.repository.saveTransaction(
        com.leftovers.app.data.Transaction(
            amountMinor = parsed.amountMinor,
            type = com.leftovers.app.data.TxType.EXPENSE,
            categoryId = category,
            epochDay = day.toEpochDay(),
            note = parsed.merchant,
            accountId = account,
        ),
    )
    container.sms.dismiss(suggestionId)
    runCatching { container.budgetAlerts.checkAfterExpense(day, category) }
    return true
}

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
                // The balance the bank reports, for the account the message is about (#59): only the number is
                // kept, and only for an account in the app's own currency, as the message's amounts are.
                BankBalance.read(body)?.let { balance ->
                    container.accounts.accounts.first().matchFor(body)?.takeIf { it.currency == null }?.let { account ->
                        container.settings.setBankBalance(account.id, balance, System.currentTimeMillis())
                    }
                }
                // The user's own keywords count too, for banks the built-in words miss.
                val check = SmsParser.check(body, s.detectionKeywords)
                // One the rules couldn't read (not an OTP or offer they skipped): the phone's own AI tries, where there is
                // one. Android lets it answer only while Leftovers is open.
                val byAi = if (check.parsed == null && !check.reason.startsWith("Skipped")) com.leftovers.app.ai.OnDevice.readPayment(body) else null
                val parsed = check.parsed ?: byAi
                if (parsed != null) {
                    val suggestionId = container.sms.addReturningId(
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
                            // By its last digits or its name ("your HBL A/C"); only the id is kept.
                            accountId = container.accounts.accounts.first().matchFor(body)?.id,
                        ),
                    )
                    val added = suggestionId != null
                    // Switched on: small spending is added now instead of waiting on Home to be checked.
                    val auto = added && autoAdd(container, s, parsed, body, suggestionId!!)
                    val amount = (if (parsed.isIncome) "income " else "") + Money(s.currencyCode).format(parsed.amountMinor)
                    val how = if (byAi != null) " (read by this phone's AI)" else ""
                    DetectionLog.add(context, "SMS", sender, if (auto) "Added $amount automatically$how" else if (added) "Suggested $amount$how" else "$amount was already logged or suggested")
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
