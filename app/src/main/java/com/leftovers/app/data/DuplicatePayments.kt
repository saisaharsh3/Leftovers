package com.leftovers.app.data

import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** An amount and when it was recorded (epoch millis), for duplicate checks. */
data class AmountAt(val amountMinor: Long, val at: Long)

/**
 * One payment can be reported more than once: a bank sends two alerts, or SMS and email both arrive, or the
 * user has already logged it by hand. A detected payment counts as a duplicate when the same amount was
 * already suggested or logged within [WINDOW_MS] of it. Every automatic source (SMS, and email later)
 * should check here before suggesting anything.
 */
object DuplicatePayments {
    val WINDOW_MS: Long = TimeUnit.HOURS.toMillis(2)

    fun isDuplicate(amountMinor: Long, at: Long, seen: List<AmountAt>): Boolean =
        seen.any { it.amountMinor == amountMinor && abs(it.at - at) <= WINDOW_MS }
}
