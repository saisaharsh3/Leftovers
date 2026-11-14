package com.leftovers.app.data

import kotlin.math.abs

/**
 * A subscription's real payment, told apart from its scheduled one. The app logs a subscription on its day, but
 * life isn't on schedule: the salary set for the 29th arrives on the 1st, the gym due on the 15th is paid on the 30th.
 * When the real payment then shows up (a bank text, or one added by hand), it belongs to the entry already logged:
 * that entry moves to the real day, with the real amount, instead of the payment being counted twice.
 */
object SubscriptionMatch {
    /** How far either side of its day a subscription's real payment can land and still be its. */
    const val WINDOW_DAYS = 25

    /** About the same amount: within 10% (a salary or a bill isn't always the same to the rupee), at least ₹1. */
    fun closeAmount(a: Long, b: Long): Boolean {
        if (a <= 0 || b <= 0) return false
        return abs(a - b) <= maxOf(b / 10, 100)
    }

    /** A subscription and the entry it logged, which a payment just seen is really the payment for. */
    data class Match(val subscription: RecurringItem, val entry: TransactionItem)

    /** A subscription waiting for its payment (#64), and which [month]'s charge a payment just seen pays. */
    data class Waiting(val subscription: RecurringItem, val month: java.time.YearMonth)

    /**
     * The subscription that waits for its payment message which [payee]'s payment of [amountMinor] on [epochDay]
     * is: its charge in a month not paid yet, within [WINDOW_DAYS] of that month's day (early or late), about its
     * amount, with its name in the payment or the amount to the rupee. The strongest match wins; null when none.
     */
    fun waiting(amountMinor: Long, isIncome: Boolean, payee: String, epochDay: Long, subscriptions: List<RecurringItem>): Waiting? {
        val type = if (isIncome) TxType.INCOME else TxType.EXPENSE
        val said = words(payee)
        val day = java.time.LocalDate.ofEpochDay(epochDay)
        return subscriptions.filter { r ->
            r.active && r.manual && r.type == type && closeAmount(amountMinor, r.amountMinor) &&
                (words(r.name).any { it in said } || amountMinor == r.amountMinor)
        }.mapNotNull { r ->
            val rec = r.toRecurring()
            // The month whose day is nearest to the payment and still unpaid: this one, or the one either side.
            listOf(-1L, 0L, 1L).map { java.time.YearMonth.from(day).plusMonths(it) }
                .filter { m -> rec.isPendingIn(m) && abs(rec.chargeDate(m).toEpochDay() - epochDay) <= WINDOW_DAYS }
                .minByOrNull { m -> abs(rec.chargeDate(m).toEpochDay() - epochDay) }
                ?.let { Waiting(r, it) }
        }.sortedWith(compareByDescending<Waiting> { w -> words(w.subscription.name).any { it in said } }
            .thenBy { w -> abs(w.subscription.toRecurring().chargeDate(w.month).toEpochDay() - epochDay) }).firstOrNull()
    }

    private fun words(text: String): Set<String> =
        Regex("[a-z]{3,}").findAll(text.lowercase()).map { it.value }
            .filterNot { it in noise }.toSet()

    /** Words in bank texts and payee names that say nothing about who it is. */
    private val noise = setOf(
        "upi", "pay", "payment", "paid", "bank", "ltd", "pvt", "private", "limited", "india", "the", "and", "for",
        "from", "via", "transfer", "neft", "imps", "rtgs", "online", "services", "monthly", "bill", "subscription",
    )

    /**
     * The entry [payee]'s payment of [amountMinor] on [epochDay] belongs to: one a subscription logged on its own day
     * (its name, category and usual amount), within [WINDOW_DAYS] of it, not on that same day, and with nothing else
     * paid for it in between. The name has to match (a shared word), or the amount to the rupee; null otherwise.
     */
    fun find(
        amountMinor: Long,
        isIncome: Boolean,
        payee: String,
        epochDay: Long,
        subscriptions: List<RecurringItem>,
        entries: List<TransactionItem>,
    ): Match? {
        val type = if (isIncome) TxType.INCOME else TxType.EXPENSE
        val said = words(payee)
        val matches = subscriptions.filter { r ->
            // Interest is worked out each time, and one paid by hand is never logged early.
            r.active && !r.manual && r.ratePercent == null && r.type == type && closeAmount(amountMinor, r.amountMinor) &&
                (words(r.name).any { it in said } || amountMinor == r.amountMinor)
        }.mapNotNull { r ->
            val logged = entries.filter { e ->
                e.type == r.type && e.categoryId == r.categoryId && e.amountMinor == r.amountMinor &&
                    e.note.trim().equals(r.name.trim(), ignoreCase = true) &&
                    e.epochDay != epochDay && abs(e.epochDay - epochDay) <= WINDOW_DAYS
            }.minByOrNull { abs(it.epochDay - epochDay) } ?: return@mapNotNull null
            // Paid already, some other way, closer to this day: then this payment is something else.
            val between = entries.any { e ->
                e.id != logged.id && e.type == r.type && closeAmount(e.amountMinor, r.amountMinor) &&
                    e.epochDay in minOf(logged.epochDay, epochDay)..maxOf(logged.epochDay, epochDay) &&
                    (e.categoryId == r.categoryId || words(e.note).any { it in words(r.name) })
            }
            if (between) null else Match(r, logged)
        }
        // Two could: the one with the name in the payment, then the nearest.
        return matches.sortedWith(compareByDescending<Match> { m -> words(m.subscription.name).any { it in said } }
            .thenBy { abs(it.entry.epochDay - epochDay) }).firstOrNull()
    }
}
