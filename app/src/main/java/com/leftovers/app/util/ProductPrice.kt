package com.leftovers.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The price on a shop's product page, for a savings goal's link. Only fetched when the user taps Check price,
 * and only over https; nothing else about the user is sent. Shops publish the price for search engines in a
 * few standard places (product data in the page, price meta tags), which is what's read here; a shop that
 * blocks apps or hides its price gets a plain message instead, and the price can be typed in.
 */
object ProductPrice {
    data class Found(val minor: Long, val currency: String?)

    class CheckException(message: String) : Exception(message)

    private const val MAX_BYTES = 3_000_000

    suspend fun check(link: String): Found = withContext(Dispatchers.IO) {
        val url = link.trim()
        if (!url.startsWith("https://")) throw CheckException("The link must start with https://")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            // Shops serve the normal page to a normal phone browser.
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw CheckException(
                    if (code == 403 || code == 429 || code == 503) "The shop didn't let the app see this page. Open it and type the price in."
                    else "The page didn't open (error $code).",
                )
            }
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                while (out.size() < MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            find(String(bytes, Charsets.UTF_8)) ?: throw CheckException("Couldn't find a price on this page. Open it and type the price in.")
        } catch (e: java.io.IOException) {
            throw CheckException("Couldn't reach the shop. Check the internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    private val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    /** The product's price in [html], from the first of the standard places that has one. */
    fun find(html: String): Found? {
        val currency = Regex(""""priceCurrency"\s*:\s*"([A-Za-z]{3})"""").find(html)?.groupValues?.get(1)?.uppercase()
            ?: meta(html, "product:price:currency", "og:price:currency")
        // 1. Product data for search engines (schema.org JSON-LD): "offers": {"price": "1299"}.
        Regex("""<script[^>]*application/ld\+json[^>]*>(.*?)</script>""", options).findAll(html).forEach { script ->
            val json = script.groupValues[1]
            if (!json.contains("offer", ignoreCase = true)) return@forEach
            Regex(""""(?:price|lowPrice)"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)""").find(json)?.groupValues?.get(1)?.let(::amount)?.let { return Found(it, currency) }
        }
        // 2. Price meta tags (Open Graph, Facebook product, schema.org itemprop).
        meta(html, "product:price:amount", "og:price:amount", "price")?.let(::amount)?.let { return Found(it, currency) }
        Regex("""itemprop=["']price["'][^>]*content=["']([^"']+)["']""", options).find(html)?.groupValues?.get(1)?.let(::amount)?.let { return Found(it, currency) }
        // 3. Amazon marks the price to pay; only that one counts, since a product that's out of stock still lists other
        //    products' prices (and the crossed-out M.R.P.) further down the page.
        val amazonCurrency = currency ?: "INR".takeIf { html.contains("amazon.in") }
        val money = """([₹$€£]?\s?[0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
        Regex("""apex-pricetopay-accessibility-label"[^>]*>\s*$money""", options).find(html)?.groupValues?.get(1)?.let(::amount)?.let { return Found(it, amazonCurrency) }
        Regex("""priceToPay[^"]*"[^>]*>\s*<span class="a-offscreen">\s*$money""", options).find(html)?.groupValues?.get(1)?.let(::amount)?.let { return Found(it, amazonCurrency) }
        Regex("""apex-pricetopay-value[^>]*>.{0,800}?a-price-whole">([0-9,]+)""", options).find(html)?.groupValues?.get(1)?.let(::amount)?.let { return Found(it, amazonCurrency) }
        // 4. Flipkart keeps the selling price in the page's data.
        Regex(""""finalPrice"\s*:\s*\{[^}]*?"value"\s*:\s*([0-9.]+)""").find(html)?.groupValues?.get(1)?.let(::amount)?.let { return Found(it, currency ?: "INR") }
        return null
    }

    private fun meta(html: String, vararg names: String): String? {
        for (name in names) {
            val n = Regex.escape(name)
            Regex("""<meta[^>]+(?:property|name|itemprop)=["']$n["'][^>]*content=["']([^"']+)["']""", options).find(html)?.let { return it.groupValues[1] }
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name|itemprop)=["']$n["']""", options).find(html)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun amount(raw: String): Long? = CsvImporter.parseAmount(raw)?.takeIf { it > 0 }
}
