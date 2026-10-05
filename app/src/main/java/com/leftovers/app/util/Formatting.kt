package com.leftovers.app.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.leftovers.app.data.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Formats minor-unit amounts (amount × 100) in the user's chosen currency. */
class Money(currencyCode: String) {
    val currency: Currency =
        runCatching { Currency.getInstance(currencyCode) }.getOrElse { Currency.getInstance("USD") }

    // Indian Rupee reads best with lakh/crore grouping (1,00,000).
    private val locale: Locale =
        if (currency.currencyCode == "INR") Locale.forLanguageTag("en-IN") else Locale.getDefault()

    private val formatter = NumberFormat.getCurrencyInstance(locale).also { it.currency = currency }

    val symbol: String = currency.getSymbol(locale)

    fun format(minor: Long): String = synchronized(formatter) {
        formatter.minimumFractionDigits = if (minor % 100 == 0L) 0 else 2
        formatter.maximumFractionDigits = 2
        formatter.format(BigDecimal.valueOf(minor, 2))
    }

    /** Rounded to whole units, for headline numbers like a daily allowance. */
    fun formatWhole(minor: Long): String = format(Math.round(minor / 100.0) * 100)

    fun signed(minor: Long, type: TxType): String =
        (if (type == TxType.INCOME) "+" else "−") + format(abs(minor))

    private val grouping = NumberFormat.getIntegerInstance(locale)

    /** Adds digit grouping to raw keypad input ("12500.5" → "12,500.5") without touching the decimals. */
    fun groupInput(text: String): String {
        if (text.isEmpty()) return "0"
        val dot = text.indexOf('.')
        val whole = if (dot >= 0) text.substring(0, dot) else text
        val grouped = synchronized(grouping) { grouping.format(whole.toLongOrNull() ?: 0L) }
        return if (dot >= 0) grouped + text.substring(dot) else grouped
    }
}

val LocalMoney = staticCompositionLocalOf { Money("USD") }

object AmountInput {
    private val pattern = Regex("""\d{0,9}(\.\d{0,2})?""")

    /** Returns the cleaned text, or null if the edit should be rejected. */
    fun sanitize(raw: String): String? {
        val text = raw.replace(',', '.')
        return if (pattern.matches(text)) text else null
    }

    fun toMinor(text: String): Long? =
        text.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

    fun fromMinor(minor: Long): String =
        BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
}

private val dayThisYear = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayOtherYear = DateTimeFormatter.ofPattern("d MMM yyyy")
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val shortDate = DateTimeFormatter.ofPattern("d MMM")

fun LocalDate.friendlyLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(if (year == today.year) dayThisYear else dayOtherYear)
}

fun LocalDate.shortLabel(): String = format(shortDate)

fun YearMonth.label(): String = format(monthFormat)

data class CurrencyOption(val code: String, val name: String, val symbol: String)

private val popularCurrencyCodes = listOf(
    "INR", "USD", "EUR", "GBP", "AED", "SAR", "SGD", "AUD", "CAD", "JPY", "CNY", "CHF",
    "NZD", "HKD", "MYR", "THB", "IDR", "PHP", "VND", "KRW", "PKR", "BDT", "LKR", "NPR",
    "ZAR", "NGN", "KES", "EGP", "BRL", "MXN", "TRY", "RUB", "SEK", "NOK", "DKK", "PLN",
    "QAR", "KWD", "OMR", "BHD",
)

val currencyOptions: List<CurrencyOption> by lazy {
    popularCurrencyCodes.mapNotNull { code ->
        runCatching {
            val c = Currency.getInstance(code)
            CurrencyOption(code, c.getDisplayName(Locale.getDefault()), Money(code).symbol)
        }.getOrNull()
    }
}
