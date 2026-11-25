package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingTest {
    private val fmt: (Long) -> String = { "₹" + java.math.BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }

    @Test fun typingALineFillsItIn() {
        val rice = Shopping.parse("rice 2 kg 60")!!
        assertEquals("Rice", rice.name)
        assertEquals(2.0, rice.quantity, 0.0)
        assertEquals("kg", rice.unit)
        assertEquals(6000L, rice.rateMinor)
        assertEquals(12000L, rice.totalMinor)
        val milk = Shopping.parse("Milk 30")!!
        assertEquals(1.0, milk.quantity, 0.0)
        assertEquals(3000L, milk.rateMinor)
        val plain = Shopping.parse("Bread")!!
        assertNull(plain.rateMinor)
        assertEquals("", plain.amountText)
        assertEquals("0.5 L", Shopping.parse("oil 0.5 l 180")!!.amountText)
        assertNull(Shopping.parse("   "))
    }

    @Test fun theTotalSkipsUnpriced() {
        val sum = Shopping.sum(listOf(ShoppingItem(name = "A", quantity = 2.0, rateMinor = 1050), ShoppingItem(name = "B")))
        assertEquals(2100L, sum.totalMinor)
        assertEquals(1, sum.unpriced)
    }

    @Test fun oneEntryListsEveryItem() {
        val bought = listOf(ShoppingItem(name = "Rice", quantity = 2.0, unit = "kg", rateMinor = 6000), ShoppingItem(name = "Milk", rateMinor = 3000), ShoppingItem(name = "Bread"))
        val one = Shopping.entries(bought, oneEntry = true, title = "Groceries", categoryId = 4, accountId = 1, epochDay = 100, format = fmt).single()
        assertEquals(15000L, one.amountMinor)
        assertEquals("Groceries", one.note)
        assertEquals("Rice 2 kg × ₹60/kg = ₹120\nMilk ₹30\nBread", one.details)
        assertEquals(TxType.EXPENSE, one.type)
    }

    @Test fun oneEntryEachLeavesOutUnpriced() {
        val bought = listOf(ShoppingItem(name = "Rice", quantity = 2.0, unit = "kg", rateMinor = 6000), ShoppingItem(name = "Bread"))
        val each = Shopping.entries(bought, oneEntry = false, title = "", categoryId = 4, accountId = null, epochDay = 100, format = fmt)
        assertEquals(1, each.size)
        assertEquals("Rice", each[0].note)
        assertEquals(12000L, each[0].amountMinor)
        assertEquals("2 kg × ₹60/kg = ₹120", each[0].details)
    }

    @Test fun groceriesOrFoodComesFirst() {
        val cats = listOf(
            Category(id = 1, name = "Other", emoji = "", color = 0, type = TxType.EXPENSE),
            Category(id = 2, name = "Food & Dining", emoji = "", color = 0, type = TxType.EXPENSE),
        )
        assertEquals(2L, Shopping.category(cats)!!.id)
        assertTrue(Shopping.category(cats + Category(id = 3, name = "Groceries", emoji = "", color = 0, type = TxType.EXPENSE))!!.id == 3L)
    }
}
