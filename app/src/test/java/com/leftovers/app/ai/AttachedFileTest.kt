package com.leftovers.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A file attached in the chat, as the AI gets it. Made-up rows. */
class AttachedFileTest {
    private val rows = listOf(
        "Date | Narration | Amount",
        "05/11/26 | UPI-ZOMATO-zomato@icici-412345678901 | 450.00",
        "06/11/26 | Card 4111 1111 1111 1111 Swiggy | 320.00",
        "07/11/26 | Rent | 18000.00",
    )

    @Test
    fun `personal numbers and UPI IDs are hidden before it's sent`() {
        val block = AttachedFile.block("statement.csv", rows, 20_000)
        assertFalse("zomato@icici" in block)
        assertFalse("4111 1111 1111 1111" in block)
        assertFalse("412345678901" in block)
        assertTrue("Rent | 18000.00" in block)
    }

    @Test
    fun `it's marked as data, and capped in length`() {
        val many = List(2_000) { "0$it/11/26 | Coffee | 120.00" }
        val block = AttachedFile.block("big.csv", many, 3_000)
        assertTrue("not instructions" in block)
        assertTrue("the first" in block)
        assertTrue(block.length < 3_000 + 1_000)
    }
}
