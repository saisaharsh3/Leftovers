package com.leftovers.app.util

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Payment apps read by default, by package, with the name shown for them. The user can add or remove apps. */
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
 * Which apps' notifications are read, and which account each one's payments go to. Apps that sent a notification
 * recently are remembered by package and name only (nothing from the notification), so a bank's own app can be
 * picked without the "see all apps" permission. Kept on the phone, never in backups.
 */
class PaymentAppPrefs(context: Context) {
    data class App(val pkg: String, val name: String, val read: Boolean, val accountId: Long?)

    private val prefs = context.getSharedPreferences("payment_apps", Context.MODE_PRIVATE)
    private val _apps = MutableStateFlow(load())
    /** Apps switched on first, then the rest: built-in ones, then others that notified recently. Hidden apps are left out. */
    val apps: StateFlow<List<App>> = _apps.asStateFlow()
    private val _hidden = MutableStateFlow(hidden().size)
    /** How many apps the user hid from the list. */
    val hiddenCount: StateFlow<Int> = _hidden.asStateFlow()

    private fun hidden(): Set<String> = prefs.getStringSet(HIDDEN, emptySet()).orEmpty()

    private fun selected(): Set<String> = prefs.getStringSet(SELECTED, null) ?: PaymentApps.names.keys

    private fun seen(): Map<String, String> = runCatching {
        val o = org.json.JSONObject(prefs.getString(SEEN, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getJSONObject(it).getString("name") }
    }.getOrDefault(emptyMap())

    private fun accounts(): Map<String, Long> = runCatching {
        val o = org.json.JSONObject(prefs.getString(ACCOUNTS, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    private fun load(): List<App> {
        val read = selected()
        val seen = seen()
        val accounts = accounts()
        val names = PaymentApps.names + seen.filterKeys { it !in PaymentApps.names }
        val hidden = hidden()
        val order = PaymentApps.names.keys.toList() + read.filter { it !in PaymentApps.names } + seen.keys.filter { it !in PaymentApps.names && it !in read }
        // The ones being read on top (#17).
        return order.distinct().filter { it !in hidden }
            .map { pkg -> App(pkg, names[pkg] ?: pkg.substringAfterLast('.'), pkg in read, accounts[pkg]) }
            .sortedByDescending { it.read }
    }

    fun isRead(pkg: String): Boolean = pkg in selected()

    fun accountFor(pkg: String): Long? = accounts()[pkg]

    fun setRead(pkg: String, read: Boolean) {
        val now = selected().toMutableSet().apply { if (read) add(pkg) else remove(pkg) }
        prefs.edit().putStringSet(SELECTED, now).apply()
        _apps.value = load()
    }

    /** Payments from [pkg] go to [accountId]; null finds the account from the message, as for SMS. */
    fun setAccount(pkg: String, accountId: Long?) {
        val o = org.json.JSONObject(prefs.getString(ACCOUNTS, "{}") ?: "{}")
        if (accountId == null) o.remove(pkg) else o.put(pkg, accountId)
        prefs.edit().putString(ACCOUNTS, o.toString()).apply()
        _apps.value = load()
    }

    /** Takes [pkg] off the list for good: it isn't read, and it doesn't come back when it notifies again (#17). */
    fun hide(pkg: String) {
        prefs.edit()
            .putStringSet(HIDDEN, hidden() + pkg)
            .putStringSet(SELECTED, selected() - pkg)
            .apply()
        _apps.value = load()
        _hidden.value = hidden().size
    }

    /** Puts every hidden app back on the list (switched off). */
    fun showHidden() {
        prefs.edit().remove(HIDDEN).apply()
        _apps.value = load()
        _hidden.value = 0
    }

    /** Remembers that [pkg] sent a notification, keeping the 30 most recent apps. */
    fun noteSeen(pkg: String, name: String) {
        if (pkg in hidden()) return
        val o = runCatching { org.json.JSONObject(prefs.getString(SEEN, "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        val already = o.optJSONObject(pkg)
        // Only write when something changed, at most once an hour per app: notifications can arrive often.
        if (already != null && already.optString("name") == name && System.currentTimeMillis() - already.optLong("at") < 3_600_000) return
        o.put(pkg, org.json.JSONObject().put("name", name).put("at", System.currentTimeMillis()))
        val keep = o.keys().asSequence().toList().sortedByDescending { o.getJSONObject(it).optLong("at") }.take(30).toSet()
        o.keys().asSequence().toList().filter { it !in keep }.forEach { o.remove(it) }
        prefs.edit().putString(SEEN, o.toString()).apply()
        _apps.value = load()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _apps.value = load()
    }

    private companion object {
        const val SELECTED = "selected"
        const val SEEN = "seen"
        const val ACCOUNTS = "accounts"
        const val HIDDEN = "hidden"
    }
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
        val pkg = sbn.packageName
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
        val extras = sbn.notification?.extras ?: return
        // Progress and group summaries aren't payments.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val container = (applicationContext as LeftoversApp).container
        val apps = container.paymentApps
        // The app's name for the picker, from the notification itself; the package name if it isn't there.
        val name = PaymentApps.names[pkg] ?: appName(sbn) ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        scope.launch {
            val s = container.settings.settings.first()
            if (!s.appNotifications) return@launch
            apps.noteSeen(pkg, name)
            if (!apps.isRead(pkg)) return@launch
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            val app = name
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
                    // The account picked for this app, else the one the message names (last digits or name).
                    accountId = apps.accountFor(pkg)?.takeIf { id -> container.accounts.accounts.first().any { it.id == id } }
                        ?: container.accounts.accounts.first().matchFor("$title $text")?.id,
                ),
            )
            val amount = (if (parsed.isIncome) "income " else "") + Money(s.currencyCode).format(parsed.amountMinor)
            DetectionLog.add(applicationContext, "App", app, if (added) "Suggested $amount" else "$amount was already logged or suggested")
        }
    }

    private fun appName(sbn: StatusBarNotification): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = sbn.notification.extras.getParcelable<android.content.pm.ApplicationInfo>("android.appInfo")
            ?: packageManager.getApplicationInfo(sbn.packageName, 0)
        packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() && it != sbn.packageName }
    }.getOrNull()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun fingerprint(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
