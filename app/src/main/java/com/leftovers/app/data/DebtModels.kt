package com.leftovers.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Money lent to or borrowed from someone. Kept apart from spending, so it never touches the budget. */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    /** Positive when they owe you, negative when you owe them. */
    val amountMinor: Long,
    val note: String = "",
    val epochDay: Long = LocalDate.now().toEpochDay(),
    val settled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Photos of bills, chats or receipts in the app's private files, one path per line. */
    val receiptPath: String? = null,
) {
    val photos: List<String> get() = receiptPath?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
}

/** Joins photo paths for [Debt.receiptPath]; null when there are none. */
fun photosToPath(photos: List<String>): String? = photos.filter { it.isNotBlank() }.joinToString("\n").ifEmpty { null }

/** At most this many photos on one entry. */
const val MAX_DEBT_PHOTOS = 6

/** Everything still open with one person; [netMinor] is positive when they owe you. */
data class PersonBalance(val person: String, val netMinor: Long, val entries: List<Debt>)

/** Open entries grouped by person (names match ignoring case), largest balance first. */
fun List<Debt>.openBalances(): List<PersonBalance> =
    filter { !it.settled }
        .groupBy { it.person.trim().lowercase() }
        .map { (_, list) ->
            val newest = list.sortedByDescending { it.createdAt }
            PersonBalance(newest.first().person.trim(), list.sumOf { it.amountMinor }, newest)
        }
        .sortedByDescending { kotlin.math.abs(it.netMinor) }
