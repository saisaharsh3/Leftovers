package com.leftovers.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/** See [AccountDao.observeNativeParts]. */
data class NativeParts(val accountId: Long, val inCurrency: Long, val inAppCurrency: Long)

/**
 * An account's balance in its own currency: its opening balance, what was entered in that currency, and the
 * rest turned back from the app's currency at [rate] (one unit of the account's currency in the app's, as typed:
 * "83.2"). Without a rate the rest can't be turned back, so only what's in the account's currency counts.
 */
fun nativeBalance(openingNative: Long?, parts: NativeParts?, rate: String?): Long {
    val r = rate?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val rest = parts?.inAppCurrency ?: 0L
    val converted = if (r == null || rest == 0L) 0L else BigDecimal.valueOf(rest).divide(r, 0, RoundingMode.HALF_UP).toLong()
    return (openingNative ?: 0L) + (parts?.inCurrency ?: 0L) + converted
}

/** [native] (in the account's currency) in the app's currency at [rate]; 0 without a rate. */
fun toAppCurrency(native: Long, rate: String?): Long {
    val r = rate?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return 0
    return BigDecimal.valueOf(native).multiply(r).setScale(0, RoundingMode.HALF_UP).toLong()
}
