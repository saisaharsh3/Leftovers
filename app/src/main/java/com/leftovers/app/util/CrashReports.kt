package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Crash reports the user chooses to send, with no service and nothing sent by itself. When the app crashes,
 * a short report (app and Android version, phone model, the error) is written to the app's private files.
 * On the next launch the app asks whether to report it on GitHub (a pre-filled new issue the user reads and
 * submits), email it, or let it go. Nothing from the user's entries is in it.
 */
object CrashReports {
    private const val EMAIL = "saisaharsh3@gmail.com"
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    /** Catches crashes to keep a report, then lets Android handle them as before ("app keeps stopping"). */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText(report(app, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report from the last crash, if one is waiting. */
    fun pending(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    internal fun report(context: Context, error: Throwable): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        return buildString {
            append("Leftovers ").append(version).append('\n')
            append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")
            append(error.stackTraceToString().take(12_000))
        }
    }

    /** A new GitHub issue with the report filled in; the user reads it and decides whether to submit. */
    fun issueUrl(report: String): String {
        val error = report.lineSequence().drop(4).firstOrNull { it.isNotBlank() }.orEmpty().substringBefore(':').substringAfterLast('.')
        val title = "Crash" + if (error.isNotBlank()) ": $error" else ""
        val body = "What were you doing when it closed?\n\n\n```\n" + report.take(6_000) + "\n```"
        return "https://github.com/saisaharsh3/Leftovers/issues/new?title=" + Uri.encode(title) + "&body=" + Uri.encode(body)
    }

    fun openIssue(context: Context, report: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl(report))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** An email to the developer with the report in it, in the user's email app; they send it themselves. */
    fun email(context: Context, report: String) {
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, "Leftovers crash report")
            .putExtra(Intent.EXTRA_TEXT, "What were you doing when it closed?\n\n\n" + report)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // No email app: any app the user picks instead.
        runCatching { context.startActivity(mail) }.onFailure { share(context, report) }
    }

    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Leftovers crash report")
            .putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(send, "Share the crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
