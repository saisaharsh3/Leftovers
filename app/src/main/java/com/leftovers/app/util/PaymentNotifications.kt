package com.leftovers.app.util

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.data.matchFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** The payment apps whose notifications are read, by package, with the name shown for them. Nothing else is looked at. */
object PaymentApps {
    val names: Map<String, String> = linkedMapOf<String, String>(
        "com.fampay.in" to "FamApp",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
    )

    /** Whether the user has given Leftovers notification access in the phone's settings. */
    fun accessGranted(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun component(context: Context) = ComponentName(context, PaymentNotificationListener::class.java)
}

/**
 * Reads a payment app's notification: "₹250 paid to Swiggy", "Paid ₹1,200 to Arjun", "Arjun sent you ₹500".
 * Stricter than bank SMS, because the same apps also send offers ("Get ₹100 cashback!") and reminders.
 */
object NotificationParser {
    /** Offers, rewards, reminders and requests: amounts in these aren't payments. */
    private val notAPayment = Regex(
        """\b(offers?|win|won|rewards?|scratch|cashback|coupons?|vouchers?|\d+\s?% off|flat|earn|chance|hurry|limited|""" +
            """recharge now|pay now|pay before|bills? (?:is )?due|due (?:on|today|tomorrow)|reminder|requested|requesting|request|collect|""" +
            """invite|refer|upto|up to|deals?|sale|claim|expir\w*|kyc|pending|failed|declined|unsuccessful|reversed|get ₹|get rs)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "Arjun sent you ₹500", "Priya paid you Rs 300": money coming in, from whoever is named first. */
    private val sentYou = Regex("""^(.{2,40}?)\s+(?:has\s+)?(?:sent|paid|transferred)\s+you\b""", RegexOption.IGNORE_CASE)

    fun parse(title: String, text: String): SmsParser.Parsed? {
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ")
        if (body.isBlank() || notAPayment.containsMatchIn(body)) return null
        val amount = MoneyText.find(body) ?: return null
        sentYou.find(title)?.let { return SmsParser.Parsed(amount, it.groupValues[1].trim(), isIncome = true) }
        sentYou.find(text)?.let { return SmsParser.Parsed(amount, it.groupValues[1].trim(), isIncome = true) }
        return SmsParser.parse(body)
    }
}

/**
 * Turns notifications from [PaymentApps] into detected payments, when the user has switched this on and given
 * notification access. Only those apps' notifications are read, and only a fingerprint of each is kept.
 */
class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Debug builds also read notifications posted from adb ("cmd notification post"), for testing.
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val app = PaymentApps.names[sbn.packageName] ?: (if (debuggable && sbn.packageName == "com.android.shell") "Test app" else return)
        val extras = sbn.notification?.extras ?: return
        // Progress and group summaries aren't payments.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val container = (applicationContext as LeftoversApp).container
        scope.launch {
            val s = container.settings.settings.first()
            if (!s.appNotifications) return@launch
            val parsed = NotificationParser.parse(title, text) ?: return@launch
            val at = sbn.postTime
            val added = container.sms.add(
                SmsSuggestion(
                    amountMinor = parsed.amountMinor,
                    merchant = parsed.merchant,
                    sender = app,
                    epochDay = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                    // Only a fingerprint, for spotting the same notification again (apps repost them on updates).
                    body = fingerprint("app:${sbn.packageName}:$title:$text"),
                    receivedAt = at,
                    source = SmsSuggestion.SOURCE_APP,
                    isIncome = parsed.isIncome,
                    accountId = container.accounts.accounts.first().matchFor("$title $text")?.id,
                ),
            )
            val amount = (if (parsed.isIncome) "income " else "") + Money(s.currencyCode).format(parsed.amountMinor)
            DetectionLog.add(applicationContext, "App", app, if (added) "Suggested $amount" else "$amount was already logged or suggested")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun fingerprint(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
