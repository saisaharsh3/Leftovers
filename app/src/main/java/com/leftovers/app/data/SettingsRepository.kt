package com.leftovers.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System default") }

data class AppSettings(
    val onboarded: Boolean,
    val currencyCode: String,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val plan: BudgetPlan,
    val budgetAlerts: Boolean,
    val defaultAccountId: Long,
    val reminderEnabled: Boolean,
    /** Minutes after midnight for the daily reminder. */
    val reminderMinutes: Int,
    val appLock: Boolean,
    val smsDetection: Boolean,
    /** Month (yyyy-MM) whose recap the user has already opened or dismissed. */
    val recapSeen: String,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            onboarded = p[ONBOARDED] ?: false,
            currencyCode = p[CURRENCY] ?: defaultCurrencyCode(),
            themeMode = ThemeMode.entries.firstOrNull { it.name == p[THEME] } ?: ThemeMode.DARK,
            dynamicColor = p[DYNAMIC_COLOR] ?: false,
            plan = BudgetPlan(
                mode = BudgetMode.entries.firstOrNull { it.name == p[BUDGET_MODE] } ?: BudgetMode.MONTHLY,
                monthlyMinor = p[MONTHLY_BUDGET] ?: 0L,
                yearlyMinor = p[YEARLY_BUDGET] ?: 0L,
                split = YearSplit.entries.firstOrNull { it.name == p[YEAR_SPLIT] } ?: YearSplit.EVEN,
                custom = BudgetPlan.decodeCustom(p[CUSTOM_SPLIT]),
            ),
            budgetAlerts = p[BUDGET_ALERTS] ?: true,
            defaultAccountId = p[DEFAULT_ACCOUNT] ?: 1L,
            reminderEnabled = p[REMINDER] ?: false,
            reminderMinutes = p[REMINDER_TIME] ?: (21 * 60),
            appLock = p[APP_LOCK] ?: false,
            smsDetection = p[SMS] ?: false,
            recapSeen = p[RECAP_SEEN].orEmpty(),
        )
    }

    suspend fun completeOnboarding(currencyCode: String) = store.edit {
        it[CURRENCY] = currencyCode
        it[ONBOARDED] = true
    }

    suspend fun setCurrency(code: String) = store.edit { it[CURRENCY] = code }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }
    suspend fun setMonthlyBudget(minor: Long) = store.edit { it[MONTHLY_BUDGET] = minor }

    suspend fun savePlan(plan: BudgetPlan) = store.edit {
        it[BUDGET_MODE] = plan.mode.name
        it[MONTHLY_BUDGET] = plan.monthlyMinor
        it[YEARLY_BUDGET] = plan.yearlyMinor
        it[YEAR_SPLIT] = plan.split.name
        it[CUSTOM_SPLIT] = BudgetPlan.encodeCustom(plan.custom)
    }
    suspend fun setBudgetAlerts(enabled: Boolean) = store.edit { it[BUDGET_ALERTS] = enabled }
    suspend fun setDefaultAccount(id: Long) = store.edit { it[DEFAULT_ACCOUNT] = id }
    suspend fun setReminder(enabled: Boolean, minutes: Int) = store.edit {
        it[REMINDER] = enabled
        it[REMINDER_TIME] = minutes
    }
    suspend fun setAppLock(enabled: Boolean) = store.edit { it[APP_LOCK] = enabled }
    suspend fun setSmsDetection(enabled: Boolean) = store.edit { it[SMS] = enabled }
    suspend fun setRecapSeen(month: String) = store.edit { it[RECAP_SEEN] = month }

    /** Records that an alert was shown; returns false if it had already been sent. */
    suspend fun markAlertSent(key: String): Boolean {
        var added = false
        store.edit {
            val sent = it[SENT_ALERTS].orEmpty()
            if (key !in sent) {
                it[SENT_ALERTS] = sent + key
                added = true
            }
        }
        return added
    }

    suspend fun clearBudgetData() = store.edit {
        listOf(MONTHLY_BUDGET, YEARLY_BUDGET).forEach(it::remove)
        listOf(BUDGET_MODE, YEAR_SPLIT, CUSTOM_SPLIT).forEach(it::remove)
        it.remove(SENT_ALERTS)
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val CURRENCY = stringPreferencesKey("currency")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val MONTHLY_BUDGET = longPreferencesKey("monthly_budget")
        val YEARLY_BUDGET = longPreferencesKey("yearly_budget")
        val BUDGET_MODE = stringPreferencesKey("budget_mode")
        val YEAR_SPLIT = stringPreferencesKey("year_split")
        val CUSTOM_SPLIT = stringPreferencesKey("custom_split")
        val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
        val SENT_ALERTS = stringSetPreferencesKey("sent_alerts")
        val DEFAULT_ACCOUNT = longPreferencesKey("default_account")
        val REMINDER = booleanPreferencesKey("reminder")
        val REMINDER_TIME = intPreferencesKey("reminder_time")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val SMS = booleanPreferencesKey("sms_detection")
        val RECAP_SEEN = stringPreferencesKey("recap_seen")

        fun defaultCurrencyCode(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"
    }
}
