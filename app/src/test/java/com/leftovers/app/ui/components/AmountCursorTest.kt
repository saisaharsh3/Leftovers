package com.leftovers.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountCursorTest {
    private fun key(text: String, cursor: Int, k: KeypadKey) = applyKey(AmountEdit(text, cursor), k)

    @Test fun typesAtTheEndByDefault() {
        assertEquals(AmountEdit("125", 3), key("12", 2, KeypadKey.Digit('5')))
    }

    @Test fun insertsAndErasesInTheMiddle() {
        assertEquals(AmountEdit("1925", 2), key("125", 1, KeypadKey.Digit('9')))
        assertEquals(AmountEdit("15", 1), key("125", 2, KeypadKey.Erase))
    }

    @Test fun eraseAtStartDoesNothing() {
        assertEquals(AmountEdit("125", 0), key("125", 0, KeypadKey.Erase))
    }

    @Test fun leadingZerosAreDropped() {
        assertEquals(AmountEdit("5", 1), key("0", 1, KeypadKey.Digit('5')))
        // Erasing the 1 of "105" leaves "05" → "5", cursor stays in front of the 5.
        assertEquals(AmountEdit("5", 0), key("105", 1, KeypadKey.Erase))
    }

    @Test fun dotRules() {
        assertEquals(AmountEdit("0.5", 2), key("5", 0, KeypadKey.Dot))
        assertEquals(AmountEdit("12.5", 3), key("125", 2, KeypadKey.Dot))
        assertEquals(AmountEdit("1.5", 1), key("1.5", 1, KeypadKey.Dot))
        assertNull(key("1.25", 4, KeypadKey.Digit('9')))
    }

    @Test fun mapsBetweenDisplayAndRaw() {
        val raw = "123456.5"
        val display = "1,23,456.5"
        // Tap the "3" (display index 3) → cursor after the 3rd digit.
        assertEquals(3, AmountCursor.rawAfter(display, raw, 3))
        // Tap the comma after it → still after the 3rd digit.
        assertEquals(3, AmountCursor.rawAfter(display, raw, 4))
        assertEquals(8, AmountCursor.rawAfter(display, raw, 9))
        assertEquals(3, AmountCursor.displayAfter(display, raw, 3))
        assertEquals(-1, AmountCursor.displayAfter(display, raw, 0))
        assertEquals(8, AmountCursor.displayAfter(display, raw, 7)) // after the dot
        assertEquals(9, AmountCursor.displayAfter(display, raw, 8))
    }
}
