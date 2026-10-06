package com.leftovers.app.util

import android.content.Context
import com.leftovers.app.ai.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The optional password for backups, so weekly automatic backups can be protected too. It is stored
 * encrypted with an Android Keystore key and is never written into a backup.
 */
class BackupPassword(context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val _isSet = MutableStateFlow(prefs.contains(KEY))
    val isSet: StateFlow<Boolean> = _isSet.asStateFlow()

    fun get(): String? = prefs.getString(KEY, null)?.let(KeyVault::decrypt)

    fun set(password: String?) {
        if (password.isNullOrEmpty()) prefs.edit().remove(KEY).apply() else prefs.edit().putString(KEY, KeyVault.encrypt(password)).apply()
        _isSet.value = !password.isNullOrEmpty()
    }

    private companion object {
        const val KEY = "password"
    }
}
