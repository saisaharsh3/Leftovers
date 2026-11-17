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
    val today = LocalDate.now().toEpochDay()
    val payAccount = container.accounts.accounts.first().let { list -> list.matchFor(body)?.id ?: list.firstOrNull { it.id == s.defaultAccountId }?.id }
    // A subscription's payment: logged as that subscription's, not as a new entry. One waiting for its message
    // (#64) gets this month's entry; one already logged on its day moves to today, with this amount.
    val subs = container.planning.recurring.first()
    com.leftovers.app.data.SubscriptionMatch.waiting(parsed.amountMinor, parsed.isIncome, parsed.merchant, today, subs)?.let { w ->
        container.planning.payRecurring(w.subscription.toRecurring(), w.month, parsed.amountMinor, today, payAccount)
        container.sms.dismiss(suggestionId)
        return true
    }
    val entries = container.repository.getAllTransactions()
    com.leftovers.app.data.SubscriptionMatch.find(parsed.amountMinor, parsed.isIncome, parsed.merchant, today, subs, entries)?.let { m ->
        val before = m.entry.toTransaction()
        container.repository.saveTransaction(before.copy(epochDay = today, amountMinor = parsed.amountMinor, accountId = payAccount ?: before.accountId))
        container.sms.dismiss(suggestionId)
        return true
    }
    val past = entries.map { MerchantMemory.Past(it.note, it.type, it.categoryId, it.epochDay, it.createdAt) }
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
