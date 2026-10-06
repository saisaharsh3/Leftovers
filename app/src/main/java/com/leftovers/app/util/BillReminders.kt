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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leftovers.app.LeftoversApp
import com.leftovers.app.MainActivity
import com.leftovers.app.R
import com.leftovers.app.data.TxType
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Morning heads-up for subscriptions that are charged tomorrow. */
object BillReminders {
    const val CHANNEL_ID = "bill_reminders"
    private const val WORK_NAME = "bill_reminders"
    private val CHECK_AT: LocalTime = LocalTime.of(9, 0)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Upcoming bills", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder the day before a subscription is charged"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(CHECK_AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        if (!s.billReminders) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val tomorrow = LocalDate.now().plusDays(1)
        val due = container.planning.recurring.first()
            .filter { it.active && it.type == TxType.EXPENSE && it.toRecurring().nextChargeDate() == tomorrow }
        if (due.isEmpty()) return Result.success()

        val money = Money(s.currencyCode)
        val title = if (due.size == 1) "${due[0].name} tomorrow" else "${due.size} bills tomorrow"
        val text = due.joinToString(" · ") { "${it.name} ${money.format(it.amountMinor)}" }
        val open = PendingIntent.getActivity(
            applicationContext, 8,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, BillReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(43, notification)
        return Result.success()
    }
}
