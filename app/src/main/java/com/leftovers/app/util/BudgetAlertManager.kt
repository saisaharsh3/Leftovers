package com.leftovers.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.leftovers.app.MainActivity
import com.leftovers.app.R
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/** Sends a notification the first time spending crosses 80% and 100% of a budget each month. */
class BudgetAlertManager(
    context: Context,
    private val repository: TransactionRepository,
    private val settings: SettingsRepository,
) {
    private val context = context.applicationContext

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Warns you when you get close to or go over a budget"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun checkAfterExpense(date: LocalDate, categoryId: Long) {
        val s = settings.settings.first()
        if (!s.budgetAlerts) return
        val month = YearMonth.from(date)
        if (month != YearMonth.now()) return
        val money = Money(s.currencyCode)

        val monthBudget = if (s.plan.isSet) s.plan.budgetFor(month, repository.getAllTransactions()) else 0L
        if (monthBudget > 0) {
            maybeNotify(
                key = "$month-all",
                label = "monthly budget",
                spent = repository.expenseTotal(month),
                budget = monthBudget,
                money = money,
                notificationId = 1,
            )
        }

        val category = repository.getCategory(categoryId) ?: return
        val budget = category.budgetMinor ?: return
        if (budget > 0) {
            maybeNotify(
                key = "$month-cat${category.id}",
                label = "${category.emoji} ${category.name} budget",
                spent = repository.expenseTotal(month, category.id),
                budget = budget,
                money = money,
                notificationId = 1000 + category.id.toInt(),
            )
        }
    }

    private suspend fun maybeNotify(
        key: String,
        label: String,
        spent: Long,
        budget: Long,
        money: Money,
        notificationId: Int,
    ) {
        val level = when {
            spent >= budget -> 100
            spent * 100 >= budget * 80 -> 80
            else -> return
        }
        if (!settings.markAlertSent("$key-$level")) return

        val title: String
        val text: String
        if (level == 100) {
            title = "⚠️ You've gone over your $label"
            text = "Spent ${money.format(spent)} of ${money.format(budget)} — over by ${money.format(spent - budget)}."
        } else {
            title = "Heads up! 80% of your $label is used"
            text = "Spent ${money.format(spent)} of ${money.format(budget)}. ${money.format(budget - spent)} left this month."
        }
        post(notificationId, title, text)
    }

    private fun post(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            // Amounts stay hidden on the lock screen; only this neutral version shows there.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_wallet)
                    .setContentTitle("Budget update")
                    .setContentText("Unlock to see details")
                    .build(),
            )
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
