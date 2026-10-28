package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTagsTest {
    @Test
    fun wordsAndTagsComeApart() {
        assertEquals("Dinner with friends", noteText("Dinner #goa with friends #trip"))
        assertEquals(listOf("goa", "trip"), hashtags("Dinner #goa with friends #trip"))
        // Not tags: an order number, or a # inside a word.
        assertEquals("Order #4521 abc#def", noteText("Order #4521 abc#def"))
    }

    @Test
    fun andGoBackTogether() {
        assertEquals("Dinner #goa #trip", withTags("Dinner", listOf("goa", "trip", "goa")))
        assertEquals("#work", withTags("  ", listOf("work")))
        assertEquals("Lunch", withTags("Lunch", emptyList()))
    }
}
