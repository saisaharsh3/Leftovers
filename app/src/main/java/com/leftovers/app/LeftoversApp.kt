package com.leftovers.app

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.AppDatabase
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.SmsRepository
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.util.BackupManager
import com.leftovers.app.util.BudgetAlertManager
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val database = AppDatabase.build(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val repository = TransactionRepository(database.transactionDao(), database.categoryDao(), database.recurringDao())
    val planning = PlanningRepository(database)
    val accounts = AccountRepository(database.accountDao(), database.transferDao(), database.transactionDao())
    val sms = SmsRepository(database.smsDao())
    val backup = BackupManager(context, database, settings)
    val budgetAlerts = BudgetAlertManager(context, repository, settings)

    fun start() {
        scope.launch { sms.scrubRawBodies() }
        // Keep the home-screen widget in step with the data.
        scope.launch {
            combine(repository.allTransactions, settings.settings, planning.recurring) { _, _, _ -> Unit }
                .collectLatest {
                    delay(600)
                    runCatching { SafeToSpendWidget().updateAll(context) }
                    runCatching { MonthBudgetWidget().updateAll(context) }
                }
        }
        scope.launch {
            settings.settings.map { it.autoBackupDir != null }
                .distinctUntilChanged()
                .collect { AutoBackup.schedule(context, it) }
        }
        scope.launch {
            settings.settings.map { it.billReminders }
                .distinctUntilChanged()
                .collect { BillReminders.schedule(context, it) }
        }
        // Reschedule the reminder whenever its settings change.
        scope.launch {
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