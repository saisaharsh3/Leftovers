package com.leftovers.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leftovers.app.LeftoversApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * Today's exchange rates, for accounts in their own currency and entries in another one (#30), from the free
 * and open currency-api (github.com/fawazahmed0/exchange-api; no account or key). Only when the user asks:
 * "Get today's rate" fetches once, and "Keep rates up to date" does it daily. The request names only the
 * app's currency; nothing about the user's money is sent.
 */
object ExchangeRates {
    private const val WORK_NAME = "exchange_rates"
    private val sources = listOf(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/%s.json",
        "https://latest.currency-api.pages.dev/v1/currencies/%s.json",
    )

    /** How much one unit of each of [codes] is in [base], as the rate box shows it ("95.26"). */
    suspend fun fetch(base: String, codes: Collection<String>): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
        val key = base.lowercase()
        var last: Throwable = IllegalStateException("No rates")
        for (source in sources) {
            val result = runCatching {
                client.newCall(Request.Builder().url(source.format(key)).build()).execute().use { response ->
                    if (!response.isSuccessful) error("The rates service said ${response.code}")
                    // {"date": "...", "inr": {"usd": 0.0105, ...}}: one unit of the base in each currency.
                    val table = JSONObject(response.body?.string().orEmpty()).getJSONObject(key)
                    codes.mapNotNull { code ->
                        val perBase = table.optDouble(code.lowercase(), Double.NaN)
                        if (perBase.isNaN() || perBase <= 0) null
                        else code to format(BigDecimal.ONE.divide(BigDecimal.valueOf(perBase), 6, RoundingMode.HALF_UP))
                    }.toMap()
                }
            }
            if (result.isSuccess) return@withContext result
            last = result.exceptionOrNull() ?: last
        }
        Result.failure(last)
    }

    /** A rate as typed: a few significant digits, no trailing zeros ("95.26", "0.0123"). */
    internal fun format(rate: BigDecimal): String {
        val rounded = if (rate >= BigDecimal.ONE) rate.setScale(2, RoundingMode.HALF_UP) else rate.round(java.math.MathContext(3, RoundingMode.HALF_UP))
        return rounded.stripTrailingZeros().toPlainString()
    }

    /** Fetches and saves today's rate for every currency the app uses: accounts' and those of entries made abroad. */
    suspend fun refresh(context: Context): Result<Int> {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val codes = (container.accounts.accounts.first().mapNotNull { it.currency } + s.foreignRates.keys)
            .filter { it != s.currencyCode }.toSet()
        if (codes.isEmpty()) return Result.success(0)
        return fetch(s.currencyCode, codes).map { rates ->
            rates.forEach { (code, rate) -> container.settings.setForeignRate(code, rate) }
            rates.size
        }
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ExchangeRatesWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class ExchangeRatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = (applicationContext as LeftoversApp).container.settings.settings.first()
        if (!s.autoRates) return Result.success()
        return if (ExchangeRates.refresh(applicationContext).isSuccess) Result.success() else Result.retry()
    }
}
