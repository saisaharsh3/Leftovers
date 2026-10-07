package com.leftovers.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** An entry the user deleted, kept so it can be restored. Same id as it had before. */
@Entity(tableName = "deleted_transactions")
data class DeletedTransaction(
    @PrimaryKey val id: Long,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long,
    val epochDay: Long,
    val note: String,
    val createdAt: Long,
    val accountId: Long?,
    val receiptPath: String?,
    val deletedAt: Long,
) {
    fun toTransaction() = Transaction(id, amountMinor, type, categoryId, epochDay, note, createdAt, accountId, receiptPath)

    companion object {
        fun from(t: Transaction, deletedAt: Long = System.currentTimeMillis()) =
            DeletedTransaction(t.id, t.amountMinor, t.type, t.categoryId, t.epochDay, t.note, t.createdAt, t.accountId, t.receiptPath, deletedAt)
    }
}

/** A deleted entry with its category, if that still exists. */
data class DeletedItem(
    @Embedded val entry: DeletedTransaction,
    val categoryName: String?,
    val categoryEmoji: String?,
    val categoryColor: Long?,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(entry.epochDay)
    fun deletedOn(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(entry.deletedAt).atZone(zone).toLocalDate()
}

/** Which deletions to show, by the day they were deleted. */
sealed interface DeletedFilter {
    data object All : DeletedFilter
    data object Today : DeletedFilter
    data object Week : DeletedFilter
    data object Month : DeletedFilter
    data class Range(val from: LocalDate, val to: LocalDate) : DeletedFilter

    fun matches(deletedOn: LocalDate, today: LocalDate = LocalDate.now()): Boolean = when (this) {
        All -> true
        Today -> deletedOn == today
        Week -> deletedOn > today.minusDays(7)
        Month -> deletedOn > today.minusDays(30)
        is Range -> deletedOn in from..to
    }
}
