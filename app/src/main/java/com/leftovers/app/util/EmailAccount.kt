package com.leftovers.app.util

import android.content.Context
import com.leftovers.app.ai.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mail services that still allow IMAP sign-in with an app password. */
enum class MailProvider(val label: String, val host: String, val helpUrl: String, val hint: String) {
    GMAIL("Gmail", "imap.gmail.com", "https://myaccount.google.com/apppasswords", "Needs 2-Step Verification. Make one at Google Account → Security → App passwords."),
    YAHOO("Yahoo", "imap.mail.yahoo.com", "https://login.yahoo.com/account/security", "Yahoo Account → Security → Generate app password."),
    ICLOUD("iCloud", "imap.mail.me.com", "https://account.apple.com", "Apple Account → Sign-In and Security → App-Specific Passwords."),
    ZOHO("Zoho", "imap.zoho.com", "https://accounts.zoho.com/home#security/app_password", "Zoho Accounts → Security → App Passwords."),
    ZOHO_IN("Zoho (India)", "imap.zoho.in", "https://accounts.zoho.in/home#security/app_password", "Zoho Accounts → Security → App Passwords."),
    OTHER("Other", "", "", "Your mail service's IMAP server. It must support SSL on port 993."),
}

data class EmailConnection(val host: String, val address: String, val lastCheckedAt: Long)

/**
 * The connected mailbox. The address and app password are encrypted with an Android Keystore key, stay on
 * the phone, and are never part of a backup (the app has Android backup turned off). Disconnecting wipes them.
 */
class EmailAccount(context: Context) {
    private val prefs = context.getSharedPreferences("email", Context.MODE_PRIVATE)
    private val _connection = MutableStateFlow(load())
    val connection: StateFlow<EmailConnection?> = _connection.asStateFlow()

    /** The app password, only read when signing in. */
    fun password(): String? = prefs.getString(PASSWORD, null)?.let(KeyVault::decrypt)

    fun save(host: String, address: String, password: String) {
        prefs.edit()
            .putString(HOST, host)
            .putString(ADDRESS, KeyVault.encrypt(address))
            .putString(PASSWORD, KeyVault.encrypt(password))
            .putLong(LAST_CHECKED, 0L)
            .apply()
        _connection.value = load()
    }

    fun markChecked(at: Long) {
        prefs.edit().putLong(LAST_CHECKED, at).apply()
        _connection.value = load()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _connection.value = null
    }

    private fun load(): EmailConnection? {
        val host = prefs.getString(HOST, null) ?: return null
        val address = prefs.getString(ADDRESS, null)?.let(KeyVault::decrypt) ?: return null
        return EmailConnection(host, address, prefs.getLong(LAST_CHECKED, 0L))
    }

    private companion object {
        const val HOST = "host"
        const val ADDRESS = "address"
        const val PASSWORD = "password"
        const val LAST_CHECKED = "last_checked"
    }
}
