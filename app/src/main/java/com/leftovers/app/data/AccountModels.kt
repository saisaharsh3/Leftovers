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
)

data class AccountWithBalance(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val openingMinor: Long,
    val createdAt: Long,
    val balanceMinor: Long,
) {
    fun toAccount() = Account(id, name, icon, color, openingMinor, createdAt)
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
) {
    companion object {
        const val SOURCE_SMS = "sms"
        const val SOURCE_EMAIL = "email"
    }
}
