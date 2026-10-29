package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopLinkTest {
    @Test
    fun moreShopsAreKeptWithTheirPrices() {
        val links = listOf(ShopLink("https://www.flipkart.com/p/item", 54_999_00, 1_000), ShopLink("https://www.croma.com/item"))
        assertEquals(links, ShopLink.decode(ShopLink.encode(links)))
        assertNull(ShopLink.encode(emptyList()))
        assertTrue(ShopLink.decode(null).isEmpty())
    }

    @Test
    fun aGoalListsEveryShop() {
        val g = GoalWithSaved(
            1, "Laptop", "laptop", 0, 60_000_00, "2027-01", 0, 0,
            link = "https://www.amazon.in/dp/X", linkPriceMinor = 57_000_00,
            moreLinks = ShopLink.encode(listOf(ShopLink("https://www.flipkart.com/p/item", 54_999_00))),
        )
        assertEquals(listOf("amazon.in", "flipkart.com"), g.links.map { it.shop })
    }
}
