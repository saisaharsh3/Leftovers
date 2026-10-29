package com.leftovers.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leftovers.app.LeftoversApp
import com.leftovers.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tells the user when a newer version is out, if they turn it on (Settings → Tell me about new versions).
 * Once a week it asks GitHub for the latest release: one request with nothing about the user in it.
 * Off by default: F-Droid and Obtainium already update the app.
 */
object UpdateCheck {
    const val CHANNEL_ID = "updates"
    private const val WORK_NAME = "update_check"
    private const val LATEST = "https://api.github.com/repos/saisaharsh3/Leftovers/releases/latest"
    private const val DEV = "https://api.github.com/repos/saisaharsh3/Leftovers/releases/tags/dev-build"

    /** [publishedAt]: when it went up (epoch millis), for the Dev build, whose version doesn't change. */
    data class Release(val version: String, val url: String, val publishedAt: Long = 0)

    /** Leftovers Dev follows the Dev build, not releases. */
    fun isDevBuild(context: Context) = context.packageName.endsWith(".dev")

    /** Whether [release] is newer than what's installed. */
    fun isNewerThanInstalled(context: Context, release: Release): Boolean {
        if (!isDevBuild(context)) return isNewer(release.version, installed(context))
        // A Dev build is installed after it's published, so one published after this install is newer.
        val updated = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(Long.MAX_VALUE)
        return release.publishedAt > updated
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "New versions", NotificationManager.IMPORTANCE_LOW).apply {
            description = "When a newer version of Leftovers is out"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The latest release on GitHub. */
    suspend fun latest(dev: Boolean = false): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
            val request = Request.Builder().url(if (dev) DEV else LATEST).header("Accept", "application/vnd.github+json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub said ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                val published = runCatching { java.time.Instant.parse(json.getString("published_at")).toEpochMilli() }.getOrDefault(0L)
                // The Dev build's name says when it was built: "Dev build 29 Oct 14:35 UTC".
                val version = if (dev) json.optString("name", "Dev build") else json.getString("tag_name").removePrefix("v")
                Release(version, json.optString("html_url", "https://github.com/saisaharsh3/Leftovers/releases/latest"), published)
            }
        }
    }

    /** The version this app is, without "-dev". */
    fun installed(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty().substringBefore('-')

    /** Whether [candidate] ("1.9.0") is newer than [current] ("1.8.1"). */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun notify(context: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 21,
            Intent(Intent.ACTION_VIEW, Uri.parse(release.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(if (release.version.startsWith("Dev")) "A new Leftovers ${release.version} is out" else "Leftovers ${release.version} is out")
            .setContentText(if (release.version.startsWith("Dev")) "Tap to download it." else "Tap to see what's new. F-Droid and Obtainium will offer it too.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(61, notification)
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = (applicationContext as LeftoversApp).container.settings
        val s = settings.settings.first()
        if (!s.updateCheck) return Result.success()
        val release = UpdateCheck.latest(UpdateCheck.isDevBuild(applicationContext)).getOrElse { return Result.retry() }
        // Each version (or Dev build) is told about once.
        val key = "${release.version}@${release.publishedAt}"
        if (UpdateCheck.isNewerThanInstalled(applicationContext, release) && s.updateNotified != key) {
            UpdateCheck.notify(applicationContext, release)
            settings.setUpdateNotified(key)
        }
        return Result.success()
    }
}
