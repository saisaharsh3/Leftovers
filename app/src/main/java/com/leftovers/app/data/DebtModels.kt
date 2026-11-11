package com.leftovers.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Money lent to or borrowed from someone, or paid back. Kept apart from spending, so it never touches the
 * budget, but it moves [accountId]'s balance: lending takes the money out, borrowing puts it in, and a
 * [repayment] moves it the other way. Settling records what's left as a repayment, so it all evens out.
 */
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
    /** The account the money left or came into; null for entries made before accounts were tracked here. */
    val accountId: Long? = null,
    /** Money paid back, all or part of what was owed; its sign is the opposite of what it pays off. */
    val repayment: Boolean = false,
    /** A share of a bill split with people: the expense it was split from, which is your share (#36). */
    val splitOf: Long? = null,
    /**
     * An amount not known yet, kept as a reminder (#36): 1 when they'll owe you, -1 when you'll owe them, with
     * [amountMinor] 0 until it's filled in; 0 for everything else.
     */
    val pending: Int = 0,
    /** The day it was paid back, for entries settled from v27 on; null before then, or while it's open. */
    val settledOn: Long? = null,
    /** Came from, or was sent to, a friend's Leftovers: the id they both know it by, so it's never added twice. */
    val sharedId: String? = null,
) {
    val photos: List<String> get() = receiptPath?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    /** Its amount isn't known yet: it counts nowhere until it's filled in. */
    val isPending: Boolean get() = pending != 0 && amountMinor == 0L

    /** Money lent (they owe you), rather than borrowed; a pending entry says which way it will go. */
    val theyOwe: Boolean get() = if (isPending) pending > 0 else amountMinor > 0
}

/** Joins photo paths for [Debt.receiptPath]; null when there are none. */
fun photosToPath(photos: List<String>): String? = photos.filter { it.isNotBlank() }.joinToString("\n").ifEmpty { null }

/** At most this many photos on one entry. */
const val MAX_DEBT_PHOTOS = 6

/** Everything still open with one person; [netMinor] is positive when they owe you. */
data class PersonBalance(val person: String, val netMinor: Long, val entries: List<Debt>) {
    /** Nothing owed yet, only entries whose amount isn't known (#36). */
    val pendingOnly: Boolean get() = netMinor == 0L && entries.any { it.isPending }

    /** Worth showing: something is owed, or an amount is still to come. */
    val open: Boolean get() = netMinor != 0L || entries.any { it.isPending }
}

/** Open entries grouped by person (names match ignoring case), largest balance first. */
fun List<Debt>.openBalances(): List<PersonBalance> =
    filter { !it.settled }
        .groupBy { it.person.trim().lowercase() }
        .map { (_, list) ->
            val newest = list.sortedByDescending { it.createdAt }
            PersonBalance(newest.first().person.trim(), list.sumOf { it.amountMinor }, newest)
        }
        .sortedByDescending { kotlin.math.abs(it.netMinor) }
