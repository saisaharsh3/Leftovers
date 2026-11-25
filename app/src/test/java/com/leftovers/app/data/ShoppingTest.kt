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

    @Test fun itemsGoInTheirAisles() {
        assertEquals("Fruit & veg", Shopping.section("Tomatoes"))
        assertEquals("Snacks", Shopping.section("Peanuts")) // not peas
        assertEquals("Household", Shopping.section("Toilet roll")) // not bread
        assertEquals("Household", Shopping.section("Candles")) // not tins
        assertEquals("Dairy & eggs", Shopping.section("Eggs"))
        assertEquals(Shopping.OTHER_SECTION, Shopping.section("Birthday card"))
        val aisles = Shopping.bySection(listOf(ShoppingItem(name = "Soap"), ShoppingItem(name = "Milk"), ShoppingItem(name = "Apples")))
        assertEquals(listOf("Fruit & veg", "Dairy & eggs", "Household"), aisles.map { it.first })
    }

    @Test fun buyAgainMostOftenFirstAndNotWhatsOnTheList() {
        val bought = listOf(
            ShoppingItem(id = 1, name = "Milk", rateMinor = 3000, boughtAt = 10),
            ShoppingItem(id = 2, name = "milk", rateMinor = 3200, boughtAt = 20),
            ShoppingItem(id = 3, name = "Rice", quantity = 2.0, unit = "kg", rateMinor = 6000, boughtAt = 30),
            ShoppingItem(id = 4, name = "Bread", boughtAt = 5),
        )
        val again = Shopping.buyAgain(bought, onList = listOf(ShoppingItem(name = "Bread")))
        assertEquals(listOf("milk", "Rice"), again.map { it.name })
        assertEquals(3200L, again[0].rateMinor) // as last bought
        val back = Shopping.again(again[1])
        assertEquals(0L, back.id)
        assertEquals(null, back.boughtAt)
        assertEquals("kg", back.unit)
    }

    @Test fun typingANameFillsInLastTime() {
        val bought = listOf(ShoppingItem(id = 1, name = "Rice", quantity = 2.0, unit = "kg", rateMinor = 6000, boughtAt = 30))
        val rice = Shopping.parse("rice", bought)!!
        assertEquals(2.0, rice.quantity, 0.0)
        assertEquals("kg", rice.unit)
        assertEquals(6000L, rice.rateMinor)
        // A different unit doesn't take last time's rate.
        assertNull(Shopping.parse("rice 500 g", bought)!!.rateMinor)
        // What's typed wins.
        assertEquals(6500L, Shopping.parse("rice 65", bought)!!.rateMinor)
    }

    @Test fun theSharedListIsByAisle() {
        val text = Shopping.shareText(listOf(ShoppingItem(name = "Milk", quantity = 2.0, unit = "L"), ShoppingItem(name = "Apples"), ShoppingItem(name = "Soap", inCart = true)))
        assertEquals("Shopping list\n\nFruit & veg\n• Apples\n\nDairy & eggs\n• Milk (2 L)", text)
    }
}
