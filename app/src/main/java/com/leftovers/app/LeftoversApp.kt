package com.leftovers.app

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.AppDatabase
import com.leftovers.app.data.SeedCategories
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.SmsRepository
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.util.BackupManager
import com.leftovers.app.util.BudgetAlertManager
import com.leftovers.app.ai.AssistantSettings
import com.leftovers.app.util.AutoBackup
import com.leftovers.app.util.BillReminders
import com.leftovers.app.util.Reminders
import com.leftovers.app.widget.MonthBudgetWidget
import com.leftovers.app.widget.SafeToSpendWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class LeftoversApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.budgetAlerts.createChannel()
        Reminders.createChannel(this)
        BillReminders.createChannel(this)
        container.start()
    }
}

/** Simple manual dependency container shared by the whole app. */
class AppContainer(private val context: Context) {
    private companion object {
        /** How long start-up housekeeping (rescheduling, clean-up) waits for the first screen. */
        const val STARTUP_QUIET_MS = 2_000L
    }

    private val database = AppDatabase.build(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val repository = TransactionRepository(database.transactionDao(), database.categoryDao(), database.recurringDao(), database.deletedDao())
    val planning = PlanningRepository(database)
    val accounts = AccountRepository(database.accountDao(), database.transferDao(), database.transactionDao())
    val sms = SmsRepository(database.smsDao(), database.transactionDao())
    val backup = BackupManager(context, database, settings, com.leftovers.app.util.BackupPassword(context))
    val budgetAlerts = BudgetAlertManager(context, repository, settings)
    val assistant = AssistantSettings(context)
    val emailAccount = com.leftovers.app.util.EmailAccount(context)

    /**
     * Wipes everything back to a fresh install: all data and photos, settings, the connected AI and email,
     * and the backup password. The app then shows the welcome screen.
     */
    suspend fun resetEverything() = withContext(Dispatchers.IO) {
        AutoBackup.schedule(context, false)
        com.leftovers.app.util.EmailSync.schedule(context, false)
        database.clearAllTables()
        SeedCategories.onCreate(database.openHelper.writableDatabase)
        java.io.File(context.filesDir, "receipts").deleteRecursively()
        emailAccount.clear()
        assistant.disconnect()
        backup.password.set(null)
        com.leftovers.app.util.DetectionLog.clear(context)
        settings.clearAll()
    }

    fun start() {
        // Housekeeping waits until the first screen is up, so it doesn't compete with drawing it.
        scope.launch {
            delay(STARTUP_QUIET_MS)
            sms.scrubRawBodies()
            sms.forgetOldSeen()
        }
        // Keep the home-screen widget in step with the data.
        scope.launch {
            combine(repository.allTransactions, settings.settings, planning.recurring) { _, _, _ -> Unit }
                // Not at launch: nothing has changed yet, and the widgets already show the last values.
                .drop(1)
                .collectLatest {
                    delay(600)
                    runCatching { SafeToSpendWidget().updateAll(context) }
                    runCatching { MonthBudgetWidget().updateAll(context) }
                }
        }
        // Clear deleted entries older than the user's chosen keep period.
        scope.launch {
            delay(STARTUP_QUIET_MS)
            settings.settings.map { it.deletedKeepDays }
                .distinctUntilChanged()
                .collect { repository.purgeDeleted(it) }
        }
        scope.launch {
            delay(STARTUP_QUIET_MS)
            emailAccount.connection.map { it != null }
                .distinctUntilChanged()
                .collect { com.leftovers.app.util.EmailSync.schedule(context, it) }
        }
        scope.launch {
            delay(STARTUP_QUIET_MS)
            settings.settings.map { (it.autoBackupDir != null) to it.autoBackupDays }
                .distinctUntilChanged()
                .collect { (on, days) -> AutoBackup.schedule(context, on, days) }
        }
        scope.launch {
            delay(STARTUP_QUIET_MS)
            settings.settings.map { it.billReminders }
                .distinctUntilChanged()
                .collect { BillReminders.schedule(context, it) }
        }
        // Reschedule the reminder whenever its settings change.
        scope.launch {
            delay(STARTUP_QUIET_MS)
            settings.settings.map { it.reminderEnabled to it.reminderMinutes }
                .distinctUntilChanged()
                .collect { (enabled, minutes) -> Reminders.schedule(context, enabled, minutes) }
        }
    }

    /** Logs subscriptions/recurring income that have come due, then checks budgets. */
    suspend fun syncRecurring() {
        settings.ensureCarryStart()
        val account = settings.settings.first().defaultAccountId
        planning.postDueRecurring(account)
            .filter { it.type == TxType.EXPENSE }
            .forEach { budgetAlerts.checkAfterExpense(LocalDate.ofEpochDay(it.epochDay), it.categoryId) }
    }
}