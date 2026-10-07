package com.leftovers.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.SmsSuggestion
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.ContentType
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import jakarta.mail.search.AndTerm
import jakarta.mail.search.BodyTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.ReceivedDateTerm
import jakarta.mail.search.SearchTerm
import jakarta.mail.search.SubjectTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Properties
import java.util.concurrent.TimeUnit

/** A payment found in an email, before it becomes a suggestion. */
data class EmailPayment(val amountMinor: Long, val merchant: String, val sender: String, val sentAt: Long, val messageKey: String)

/**
 * Reads payment emails over IMAP. Security and privacy rules:
 * - TLS only (port 993), with the server's certificate and host name checked. There is no plain-text fallback.
 * - The inbox is opened read-only and messages are read with PEEK, so nothing is changed or marked as read.
 * - Only recent messages that mention a payment are asked for, at most [MAX_MESSAGES] at a time.
 * - Emails are read in memory; only the amount, merchant, date and a hash of the message ID are kept.
 */
object EmailChecker {
    private const val MAX_MESSAGES = 50
    private val FIRST_LOOK_BACK_MS = TimeUnit.DAYS.toMillis(2)
    private val OVERLAP_MS = TimeUnit.HOURS.toMillis(6)

    private val searchWords = listOf(
        "debited", "spent", "paid", "payment", "purchase", "charged", "transaction", "order", "receipt", "invoice",
    )

    /** Connection settings: TLS with certificate and host name checks, short timeouts, read without marking. */
    private fun properties(host: String) = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", host)
        put("mail.imaps.port", "993")
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.ssl.checkserveridentity", "true")
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "20000")
        put("mail.imaps.writetimeout", "20000")
        put("mail.imaps.peek", "true")
    }

    /** Signs in once to check the details; nothing is read. */
    suspend fun testSignIn(host: String, address: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Session.getInstance(properties(host)).getStore("imaps").use { it.connect(host, address, password) }
        }.recoverCatching { throw friendlyError(it) }
    }

    /** Looks for new payment emails and adds them as suggestions. Returns how many were added. */
    suspend fun check(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        val container = (context.applicationContext as LeftoversApp).container
        val account = container.emailAccount
        val connection = account.connection.value ?: return@withContext Result.success(0)
        val password = account.password() ?: return@withContext Result.failure(IllegalStateException("Not connected"))
        val now = System.currentTimeMillis()
        val since = if (connection.lastCheckedAt == 0L) now - FIRST_LOOK_BACK_MS else connection.lastCheckedAt - OVERLAP_MS
        runCatching {
            val found = Session.getInstance(properties(connection.host)).getStore("imaps").use { store ->
                store.connect(connection.host, connection.address, password)
                findPayments(store, since)
            }
            var added = 0
            found.forEach { p ->
                val isNew = container.sms.add(
                    SmsSuggestion(
                        amountMinor = p.amountMinor,
                        merchant = p.merchant,
                        sender = p.sender,
                        epochDay = Instant.ofEpochMilli(p.sentAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                        body = hash("email:" + p.messageKey),
                        // The email's own time, so it lines up with an SMS for the same payment.
                        receivedAt = p.sentAt,
                        source = SmsSuggestion.SOURCE_EMAIL,
                    ),
                )
                if (isNew) added++
            }
            account.markChecked(now)
            added
        }.recoverCatching { throw friendlyError(it) }
    }

    /** Payment emails received since [since] in an already signed-in [store]. */
    fun findPayments(store: Store, since: Long): List<EmailPayment> {
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        try {
            val words: Array<SearchTerm> = searchWords.flatMap { listOf(SubjectTerm(it), BodyTerm(it)) }.toTypedArray()
            // IMAP dates are whole days, so the exact time is checked again below.
            val term = AndTerm(ReceivedDateTerm(ComparisonTerm.GE, Date(since)), OrTerm(words))
            val messages = inbox.search(term).filter { (it.receivedDate ?: it.sentDate)?.time?.let { t -> t >= since } ?: false }
                .sortedByDescending { (it.receivedDate ?: it.sentDate).time }
                .take(MAX_MESSAGES)
                .toTypedArray()
            inbox.fetch(messages, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE) })
            return messages.mapNotNull { m -> runCatching { paymentIn(m) }.getOrNull() }
        } finally {
            inbox.close(false)
        }
    }

    private fun paymentIn(m: Message): EmailPayment? {
        val from = m.from?.firstOrNull() as? InternetAddress
        val senderName = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address?.substringBefore('@').orEmpty()
        val subject = m.subject.orEmpty()
        val body = textOf(m, 0).take(20_000)
        val parsed = EmailParser.parse(subject, senderName, body) ?: return null
        val sentAt = (m.sentDate ?: m.receivedDate)?.time ?: return null
        val key = (m as? MimeMessage)?.messageID ?: "${from?.address}|$sentAt|$subject"
        return EmailPayment(parsed.amountMinor, parsed.merchant, senderName.take(40), sentAt, key)
    }

    /** Readable text of a message, preferring plain text over HTML. Attachments are skipped. */
    private fun textOf(part: Part, depth: Int): String {
        if (depth > 5) return ""
        if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) return ""
        return when {
            part.isMimeType("text/plain") -> read(part)
            part.isMimeType("text/html") -> EmailParser.htmlToText(read(part))
            part.isMimeType("multipart/*") -> {
                val multi = MimeMultipart(part.dataHandler.dataSource)
                val parts = (0 until multi.count).map { multi.getBodyPart(it) }
                val plain = parts.firstOrNull { it.isMimeType("text/plain") }
                if (plain != null && part.isMimeType("multipart/alternative")) read(plain)
                else parts.joinToString("\n") { textOf(it, depth + 1) }
            }
            else -> ""
        }
    }

    private fun read(part: Part): String {
        val charset = runCatching { ContentType(part.contentType).getParameter("charset") }.getOrNull()
            ?.let { MimeUtility.javaCharset(it) } ?: "UTF-8"
        // Read at most 200 KB of any one part; payment details are near the top.
        val bytes = part.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (out.size() < 200_000) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        return String(bytes, charset(charset))
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun friendlyError(e: Throwable): Throwable = when (e) {
        is AuthenticationFailedException -> IllegalStateException("Sign-in failed. Check the email address and app password.")
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            IllegalStateException("Couldn't reach the mail server. Check the connection and try again.")
        is javax.net.ssl.SSLException -> IllegalStateException("The mail server's security certificate couldn't be verified.")
        else -> IllegalStateException("Couldn't check email: ${e.message ?: e.javaClass.simpleName}")
    }
}

/** Checks the connected mailbox in the background, about every 30 minutes, only when online. */
object EmailSync {
    private const val WORK_NAME = "email_check"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<EmailCheckWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class EmailCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // A failed check (offline, password changed) just waits for the next run instead of retrying in a loop.
    override suspend fun doWork(): Result {
        EmailChecker.check(applicationContext)
        return Result.success()
    }
}
