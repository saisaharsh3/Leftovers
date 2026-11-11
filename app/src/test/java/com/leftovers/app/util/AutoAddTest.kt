package com.leftovers.app.util

import com.leftovers.app.data.Category
import com.leftovers.app.data.Debt
import com.leftovers.app.data.PersonBalance
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which detected payments are added without asking, and under which category. Made-up shops and people. */
class AutoAddTest {
    private val food = Category(id = 1, name = "Food & Dining", emoji = "", color = 0, type = TxType.EXPENSE)
    private val other = Category(id = 2, name = "Other", emoji = "", color = 0, type = TxType.EXPENSE)
    private val oldJob = Category(id = 3, name = "Office lunches", emoji = "", color = 0, type = TxType.EXPENSE, hidden = true)
    private val salary = Category(id = 4, name = "Salary", emoji = "", color = 0, type = TxType.INCOME)
    private val categories = listOf(food, other, oldJob, salary)

    @Test
    fun `small spending is added, up to and including the amount chosen`() {
        assertTrue(AutoAdd.allowed(true, 20_000, 12_000, false, "Swiggy", emptyList()))
        assertTrue(AutoAdd.allowed(true, 20_000, 20_000, false, "Swiggy", emptyList()))
        assertFalse(AutoAdd.allowed(true, 20_000, 20_001, false, "Swiggy", emptyList()))
    }

    @Test
    fun `nothing is added when it's off, for money coming in, or for nothing at all`() {
        assertFalse(AutoAdd.allowed(false, 20_000, 12_000, false, "Swiggy", emptyList()))
        assertFalse(AutoAdd.allowed(true, 20_000, 12_000, true, "Swiggy", emptyList()))
        assertFalse(AutoAdd.allowed(true, 20_000, 0, false, "Swiggy", emptyList()))
    }

    @Test
    fun `paying back someone you owe still waits, to be settled in Money owed`() {
        val owed = listOf(PersonBalance("Priya", -15_000, listOf(Debt(person = "Priya", amountMinor = -15_000))))
        assertFalse(AutoAdd.allowed(true, 20_000, 15_000, false, "priya.nair@okaxis", owed))
        // Someone else, or a shop: added as usual.
        assertTrue(AutoAdd.allowed(true, 20_000, 15_000, false, "Swiggy", owed))
    }

    @Test
    fun `the category is where that place went last time, else Other, never a hidden one`() {
        val past = listOf(
            MerchantMemory.Past("Swiggy", TxType.EXPENSE, food.id, 20_760, 1),
            MerchantMemory.Past("Canteen", TxType.EXPENSE, oldJob.id, 20_700, 1),
        )
        assertEquals(food.id, AutoAdd.category("SWIGGY", past, categories))
        assertEquals(other.id, AutoAdd.category("A new shop", past, categories))
        // Filed under a category since hidden: Other instead.
        assertEquals(other.id, AutoAdd.category("Canteen", past, categories))
        // No "Other": the first spending category.
        assertEquals(food.id, AutoAdd.category("A new shop", emptyList(), listOf(food, salary)))
        assertNull(AutoAdd.category("A new shop", emptyList(), listOf(salary)))
    }
}
