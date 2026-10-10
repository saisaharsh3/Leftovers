package com.leftovers.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where money lives: cash, a bank account, a UPI wallet, a card. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Icon key from CategoryIcons. */
    val icon: String,
    val color: Long,
    /** Balance before any entries were logged in the app. */
    val openingMinor: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Last digits of the bank account or card, so payments spotted in SMS land in this account. */
    val smsDigits: String? = null,
)

data class AccountWithBalance(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val openingMinor: Long,
    val createdAt: Long,
    val smsDigits: String?,
    val balanceMinor: Long,
) {
    fun toAccount() = Account(id, name, icon, color, openingMinor, createdAt, smsDigits)

    /** True when a message naming account or card digits [digits] belongs to this account. */
    fun matchesDigits(digits: String?): Boolean {
        val mine = smsDigits?.filter(Char::isDigit)?.takeLast(4)
        if (digits.isNullOrEmpty() || mine.isNullOrEmpty()) return false
        // Banks show 3 or 4 digits ("XX234", "*1234"); match on what both have.
        val n = minOf(digits.length, mine.length)
        return n >= 3 && digits.takeLast(n) == mine.takeLast(n)
    }

    /**
     * True when a bank message uses this account's name as an account: "in your HBL A/C", "HDFC Bank card",
     * "credited to your Revolut account". A name on its own isn't enough ("Cash withdrawal" isn't the Cash account).
     */
    fun namedIn(text: String): Boolean = nameForms().any { form ->
        val n = Regex.escape(form)
        Regex(
            """\b$n(?:\s+bank)?\s*(?:a/c|ac|acct|account|card|credit card|debit card|wallet)\b|""" +
                """\b(?:to|in|into|from|on)\s+your\s+$n\b""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(text)
    }

    /** The account's name, and the same without words like "Bank" or "Savings" ("HBL Bank" also answers to "HBL"). */
    private fun nameForms(): List<String> {
        val full = name.trim()
        val core = full.replace(ACCOUNT_WORDS, "").trim()
        return listOf(full, core).distinct().filter { it.length >= 2 && it.any(Char::isLetter) }
    }

    private companion object {
        val ACCOUNT_WORDS = Regex("""(?:\s+(?:bank|account|a/c|savings|saving|current|salary|card|wallet))+$""", RegexOption.IGNORE_CASE)
    }
}

/**
 * The account a bank message names by its own words, as in "in your HBL A/C" → "HBL", for a hint when none of
 * the user's accounts matches. Null when it names none.
 */
fun namedAccountIn(text: String): String? = Regex(
    """\byour\s+([A-Za-z][A-Za-z&.\- ]{0,24}?)\s+(?:bank\s+)?(?:a/c|ac|acct|account|card|credit card|debit card)\b""",
    RegexOption.IGNORE_CASE,
).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.lowercase() !in setOf("bank", "savings", "current", "the") }

/**
 * The account a bank message belongs to: the one whose last digits it names, otherwise the only one it names
 * as an account by name. Null when it's unclear, so the usual default is used.
 */
fun List<AccountWithBalance>.matchFor(text: String): AccountWithBalance? {
    val digits = com.leftovers.app.util.SmsParser.accountDigits(text)
    firstOrNull { it.matchesDigits(digits) }?.let { return it }
    return filter { it.namedIn(text) }.singleOrNull()
}

/** Money moved between two of the user's own accounts; not income or spending. */
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId")],
)
data class Transfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountMinor: Long,
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** A payment spotted in a bank SMS or an email, waiting for the user to confirm or dismiss it. */
@Entity(tableName = "sms_suggestions")
data class SmsSuggestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val merchant: String,
    val sender: String,
    val epochDay: Long,
    /** A hash for spotting repeats, never the message itself. */
    val body: String,
    val receivedAt: Long = System.currentTimeMillis(),
    /** Where it was spotted: [SOURCE_SMS] or [SOURCE_EMAIL]. */
    @ColumnInfo(defaultValue = SOURCE_SMS) val source: String = SOURCE_SMS,
    /** Last digits of the account or card the message named, if any. */
    val accountDigits: String? = null,
    /** Money coming in ("credited", "received"), suggested as income rather than an expense. */
    @ColumnInfo(defaultValue = "0") val isIncome: Boolean = false,
    /** The account the message matched, by its last digits or its name (see [matchFor]). */
    val accountId: Long? = null,
) {
    companion object {
        const val SOURCE_SMS = "sms"
        const val SOURCE_EMAIL = "email"
    }
}

/**
 * A message that has already become a suggestion, kept after the suggestion is added or dismissed, so a later
 * email check never brings it back. Only the message's hash is kept, and only for [SmsRepository.SEEN_DAYS] days.
 */
@Entity(tableName = "seen_messages")
data class SeenMessage(
    @PrimaryKey val hash: String,
    val seenAt: Long,
)
