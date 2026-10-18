package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where shops put a product's price, for a goal's link. */
class ProductPriceTest {
    @Test fun schemaOrgProductData() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Headphones",
             "offers":{"@type":"Offer","priceCurrency":"INR","price":"12999.00","availability":"InStock"}}
            </script></head></html>
        """.trimIndent()
        assertEquals(ProductPrice.Found(1_299_900, "INR"), ProductPrice.find(html))
    }

    @Test fun lowPriceOfAnOfferRange() {
        val html = """<script type="application/ld+json">{"@type":"Product","offers":{"@type":"AggregateOffer","lowPrice":499,"highPrice":899,"priceCurrency":"USD"}}</script>"""
        assertEquals(ProductPrice.Found(49_900, "USD"), ProductPrice.find(html))
    }

    @Test fun priceMetaTagsEitherAttributeOrder() {
        assertEquals(1_49_900L, ProductPrice.find("""<meta property="product:price:amount" content="1499"><meta property="product:price:currency" content="INR">""")?.minor)
        assertEquals(2_000L, ProductPrice.find("""<meta content="20.00" property="og:price:amount" />""")?.minor)
        assertEquals(75_000L, ProductPrice.find("""<span itemprop="price" content="750">₹750</span>""")?.minor)
    }

    // From a real amazon.in phone page: the price to pay, then the crossed-out M.R.P.
    private val amazonPage = """
        <div id="apex_mobile_feature_div"><span id="apex-pricetopay-accessibility-label" data-pricetopay-label="{priceToPay}" class="aok-offscreen"> ₹129.00 with 78 percent savings </span>
        <span class="a-price aok-align-center reinventPricePriceToPayMargin priceToPay apex-pricetopay-value" data-a-size="xxl"><span class="a-offscreen"> </span>
        <span aria-hidden="true"><span class="a-price-symbol">₹</span><span class="a-price-whole">129</span></span></span>
        <span class="apex-basisprice-label">M.R.P.:</span> <span class="a-price a-text-price apex-basisprice-value" data-a-strike="true"><span class="a-offscreen">₹599</span></span></div>
        https://www.amazon.in/
    """.trimIndent()

    @Test fun amazonPriceToPayNotTheMrp() = assertEquals(ProductPrice.Found(12_900, "INR"), ProductPrice.find(amazonPage))

    @Test fun amazonWithoutTheAccessibilityLabel() {
        val page = amazonPage.replace(Regex("""<span id="apex-pricetopay-accessibility-label".*?</span>"""), "")
        assertEquals(12_900L, ProductPrice.find(page)?.minor)
    }

    @Test fun amazonOutOfStockShowsOtherProductsPrices() {
        // Only related products' prices, with no price-to-pay block: no price rather than the wrong one.
        assertNull(ProductPrice.find("""<div id="corePrice_feature_div"></div> Currently unavailable. <span class="a-price"><span class="a-offscreen">₹76,999.00</span><span class="a-price-whole">76,999</span></span>"""))
    }

    @Test fun flipkartFinalPrice() {
        val html = """<script>window.__INITIAL_STATE__={"pricing":{"finalPrice":{"decimalValue":null,"value":54999,"currency":"INR"}}}</script>"""
        assertEquals(5_499_900L, ProductPrice.find(html)?.minor)
    }

    @Test fun noPriceOnThePage() = assertNull(ProductPrice.find("<html><body>Sign in to continue</body></html>"))
}
