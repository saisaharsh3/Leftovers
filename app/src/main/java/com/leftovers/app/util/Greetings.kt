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
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * The main festivals where the user lives, worked out from the currency they chose (nothing is sent anywhere):
 * a greeting on Home on the day, and one notification that morning.
 */
object Festivals {
    enum class Festival(val greeting: String, val note: String) {
        NEW_YEAR("Happy New Year 🎉", "A fresh year and a fresh budget. Here's to a good one."),
        CHRISTMAS("Merry Christmas 🎄", "Enjoy the day. The gifts can be logged tomorrow."),
        DIWALI("Happy Diwali 🪔", "Wishing you light, sweets and plenty."),
        HOLI("Happy Holi 🎨", "Have a colourful day!"),
        EID_FITR("Eid Mubarak 🌙", "Wishing you and your family a joyful Eid."),
        EID_ADHA("Eid al-Adha Mubarak 🌙", "Wishing you and your family a blessed Eid."),
        LUNAR_NEW_YEAR("Happy Lunar New Year 🧧", "Wishing you luck and plenty in the year ahead."),
        THANKSGIVING("Happy Thanksgiving 🦃", "Enjoy the day with the people you're thankful for."),
        SONGKRAN("Happy Songkran 💦", "Happy Thai New Year!"),
    }

    /*
     * Festivals that follow the moon move every year. Dates as published (Drik Panchang for Diwali and Holi;
     * Eid as expected, which can move a day with the moon sighting). Years not listed get no greeting for that
     * festival until the table is extended.
     */
    private val moving: Map<Festival, Map<Int, Pair<Int, Int>>> = mapOf(
        Festival.DIWALI to mapOf(2026 to (11 to 8), 2027 to (10 to 29), 2028 to (10 to 17), 2029 to (11 to 5), 2030 to (10 to 26)),
        Festival.HOLI to mapOf(2026 to (3 to 4), 2027 to (3 to 22), 2028 to (3 to 11)),
        Festival.EID_FITR to mapOf(2026 to (3 to 20), 2027 to (3 to 10), 2028 to (2 to 27), 2029 to (2 to 15), 2030 to (2 to 5)),
        Festival.EID_ADHA to mapOf(2026 to (5 to 27), 2027 to (5 to 16), 2028 to (5 to 5), 2029 to (4 to 24), 2030 to (4 to 13)),
        Festival.LUNAR_NEW_YEAR to mapOf(2026 to (2 to 17), 2027 to (2 to 6), 2028 to (1 to 26), 2029 to (2 to 13), 2030 to (2 to 3)),
    )

    /** The day [f] falls on in [year] where [currency] is used, or null when it isn't known. */
    fun date(f: Festival, year: Int, currency: String): LocalDate? = when (f) {
        Festival.NEW_YEAR -> LocalDate.of(year, 1, 1)
        Festival.CHRISTMAS -> LocalDate.of(year, 12, 25)
        Festival.SONGKRAN -> LocalDate.of(year, 4, 13)
        // The US on the fourth Thursday of November; Canada on the second Monday of October.
        Festival.THANKSGIVING -> if (currency == "CAD") LocalDate.of(year, Month.OCTOBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY))
        else LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
        else -> moving[f]?.get(year)?.let { (m, d) -> LocalDate.of(year, m, d) }
    }

    /** The main festivals for the region a currency is used in; regional ones are left out. */
    fun forCurrency(code: String): List<Festival> = when (code.uppercase()) {
        "INR" -> listOf(Festival.NEW_YEAR, Festival.HOLI, Festival.EID_FITR, Festival.DIWALI, Festival.CHRISTMAS)
        "NPR" -> listOf(Festival.NEW_YEAR, Festival.DIWALI)
        "PKR", "BDT", "AED", "SAR", "QAR", "KWD", "BHD", "OMR", "EGP", "JOD", "MAD" -> listOf(Festival.NEW_YEAR, Festival.EID_FITR, Festival.EID_ADHA)
        "IDR" -> listOf(Festival.NEW_YEAR, Festival.EID_FITR, Festival.EID_ADHA, Festival.CHRISTMAS)
        "MYR", "SGD" -> listOf(Festival.NEW_YEAR, Festival.LUNAR_NEW_YEAR, Festival.EID_FITR, Festival.DIWALI, Festival.CHRISTMAS)
        "CNY", "RMB", "TWD", "KRW", "VND" -> listOf(Festival.NEW_YEAR, Festival.LUNAR_NEW_YEAR)
        "HKD" -> listOf(Festival.NEW_YEAR, Festival.LUNAR_NEW_YEAR, Festival.CHRISTMAS)
        "JPY", "RUB", "UAH" -> listOf(Festival.NEW_YEAR)
        "THB" -> listOf(Festival.NEW_YEAR, Festival.SONGKRAN)
        "USD", "CAD" -> listOf(Festival.NEW_YEAR, Festival.THANKSGIVING, Festival.CHRISTMAS)
        "NGN", "KES", "GHS", "TZS", "UGX" -> listOf(Festival.NEW_YEAR, Festival.EID_FITR, Festival.CHRISTMAS)
        else -> listOf(Festival.NEW_YEAR, Festival.CHRISTMAS)
    }

    /** The festival on [day] for someone using [currency], if there is one. */
    fun on(day: LocalDate, currency: String): Festival? = forCurrency(currency).firstOrNull { date(it, day.year, currency) == day }
}

/**
 * A short, light note on the week's spending for Sunday evening, when there's something worth saying: spending
 * down or up a lot on the week before, a category that changed, no-spend days. Null when there's nothing to say.
 */
object WeeklyNote {
    fun note(items: List<TransactionItem>, today: LocalDate, format: (Long) -> String): String? {
        val start = today.minusDays(6)
        val before = start.minusDays(7)
        val spends = items.filter { it.type == TxType.EXPENSE && !it.outOfBudget }
        val week = spends.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        val last = spends.filter { !it.date.isBefore(before) && it.date.isBefore(start) }
        if (week.isEmpty() && last.isEmpty()) return null
        val now = week.sumOf { it.amountMinor }
        val then = last.sumOf { it.amountMinor }
        val change = if (then > 0) Math.round((now - then) * 100.0 / then).toInt() else null
        if (change != null && change <= -15) return "You spent ${-change}% less this week than last (${format(now)} vs ${format(then)}). Nice going 🎉"
        // One category that moved a lot, either way.
        val byNow = week.groupBy { it.categoryName }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val byThen = last.groupBy { it.categoryName }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val moved = (byNow.keys + byThen.keys).mapNotNull { c ->
            val a = byNow[c] ?: 0
            val b = byThen[c] ?: 0
            // Small amounts swing a lot without meaning much: only categories of 50 or more.
            if (b <= 0 || maxOf(a, b) < 50_00) null else Triple(c, a, Math.round((a - b) * 100.0 / b).toInt())
        }.maxByOrNull { kotlin.math.abs(it.third) }
        if (moved != null && moved.third <= -30) return "${moved.first} came to ${format(moved.second)} this week, ${-moved.third}% less than last week 👏"
        val free = (0..6).count { d -> week.none { it.date == today.minusDays(d.toLong()) } }
        if (free >= 2 && week.isNotEmpty()) return "$free no-spend days this week 🙌 Your wallet says thanks."
        if (change != null && change >= 20) return "This week came to ${format(now)}, $change% more than last week. Next week's a fresh start 💪"
        if (moved != null && moved.third >= 50) return "${moved.first} was up ${moved.third}% this week (${format(moved.second)}). Worth a look? 👀"
        val top = byNow.maxByOrNull { it.value } ?: return null
        return "${top.key} was your top spend this week, at ${format(top.value)} ✨"
    }
}

/** Festival greetings in the morning and the Sunday-evening note, as notifications. */
object Greetings {
    const val CHANNEL_ID = "greetings"
    private const val FESTIVAL_WORK = "festival_greetings"
    private const val WEEKLY_WORK = "weekly_note"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Greetings and weekly notes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Festival greetings, and a short note on your week every Sunday evening"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun delayUntil(at: LocalDateTime): Long = Duration.between(LocalDateTime.now(), at).toMinutes().coerceAtLeast(1)

    fun schedule(context: Context, festivals: Boolean, weekly: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (festivals) {
            var next = LocalDate.now().atTime(LocalTime.of(9, 0))
            if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
            wm.enqueueUniquePeriodicWork(
                FESTIVAL_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<FestivalWorker>(24, TimeUnit.HOURS).setInitialDelay(delayUntil(next), TimeUnit.MINUTES).build(),
            )
        } else wm.cancelUniqueWork(FESTIVAL_WORK)
        if (weekly) {
            var next = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(19, 0))
            if (!next.isAfter(LocalDateTime.now())) next = next.plusWeeks(1)
            wm.enqueueUniquePeriodicWork(
                WEEKLY_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WeeklyNoteWorker>(7, TimeUnit.DAYS).setInitialDelay(delayUntil(next), TimeUnit.MINUTES).build(),
            )
        } else wm.cancelUniqueWork(WEEKLY_WORK)
    }

    internal fun post(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class FestivalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = (applicationContext as LeftoversApp).container.settings.settings.first()
        val today = LocalDate.now()
        val festival = Festivals.on(today, s.currencyCode) ?: return Result.success()
        // Once a festival, even if the work runs twice that day.
        val prefs = applicationContext.getSharedPreferences("greetings", Context.MODE_PRIVATE)
        val key = "${festival.name}-${today.year}"
        if (prefs.getString("last", null) == key) return Result.success()
        prefs.edit().putString("last", key).apply()
        Greetings.post(applicationContext, 71, festival.greeting, festival.note)
        return Result.success()
    }
}

class WeeklyNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val money = Money(s.currencyCode)
        val text = WeeklyNote.note(container.repository.getAllTransactions(), LocalDate.now(), money::formatWhole) ?: return Result.success()
        Greetings.post(applicationContext, 72, "Your week", text)
        return Result.success()
    }
}
