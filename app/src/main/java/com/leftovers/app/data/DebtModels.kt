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
    /** A photo of a bill or note, kept in the app's private files. */
    val receiptPath: String? = null,
)

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
