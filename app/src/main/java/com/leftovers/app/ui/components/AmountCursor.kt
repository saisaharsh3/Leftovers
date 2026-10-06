package com.leftovers.app.ui.components

import com.leftovers.app.util.AmountInput

/** The typed amount plus where the keypad writes; [cursor] is an index into [text]. */
data class AmountEdit(val text: String, val cursor: Int)

/**
 * Applies a keypad press at the cursor. Returns null when the result isn't a valid amount
 * (too many digits, a second decimal point, more than two decimals).
 */
fun applyKey(edit: AmountEdit, key: KeypadKey): AmountEdit? {
    val text = edit.text
    val pos = edit.cursor.coerceIn(0, text.length)
    val (next, cursor) = when (key) {
        is KeypadKey.Digit -> if (text == "0") key.value.toString() to 1 else insert(text, pos, key.value.toString())
        KeypadKey.DoubleZero -> if (text.isEmpty() || text == "0") return edit else insert(text, pos, "00")
        KeypadKey.Dot -> when {
            '.' in text -> return edit
            pos == 0 -> insert(text, 0, "0.")
            else -> insert(text, pos, ".")
        }
        KeypadKey.Erase -> if (pos == 0) return edit else text.removeRange(pos - 1, pos) to pos - 1
    }
    val clean = AmountInput.sanitize(next) ?: return null
    return normalize(AmountEdit(clean, cursor))
}

private fun insert(text: String, pos: Int, s: String) = text.substring(0, pos) + s + text.substring(pos) to pos + s.length

/** Drops leading zeros ("05" → "5") and keeps the cursor on the same digit. */
private fun normalize(edit: AmountEdit): AmountEdit {
    val dot = edit.text.indexOf('.')
    val whole = if (dot >= 0) edit.text.substring(0, dot) else edit.text
    if (whole.length <= 1 || !whole.startsWith('0')) return edit
    val trimmed = whole.trimStart('0').ifEmpty { "0" }
    val removed = whole.length - trimmed.length
    return AmountEdit(trimmed + edit.text.substring(whole.length), (edit.cursor - removed).coerceAtLeast(0))
}

/**
 * Converts between positions in the raw text ("12345.5") and the grouped display ("12,345.5").
 * Grouping marks only appear in the whole-number part, before the decimal point.
 */
object AmountCursor {
    /** Raw cursor for a tap on display character [index]: the cursor goes just after it. */
    fun rawAfter(display: String, raw: String, index: Int): Int {
        val wholeRaw = raw.substringBefore('.').length
        val wholeDisplay = display.length - (raw.length - wholeRaw)
        return if (index < wholeDisplay) {
            display.substring(0, index + 1).count { it.isDigit() }.coerceAtMost(wholeRaw)
        } else {
            (wholeRaw + (index - wholeDisplay + 1)).coerceAtMost(raw.length)
        }
    }

    /** Display index the caret sits after for raw [cursor]; -1 means before the first character. */
    fun displayAfter(display: String, raw: String, cursor: Int): Int {
        if (cursor <= 0) return -1
        val wholeRaw = raw.substringBefore('.').length
        val wholeDisplay = display.length - (raw.length - wholeRaw)
        if (cursor > wholeRaw) return wholeDisplay - 1 + (cursor - wholeRaw)
        var digits = 0
        display.forEachIndexed { i, ch ->
            if (i < wholeDisplay && ch.isDigit()) {
                digits++
                if (digits == cursor) return i
            }
        }
        return wholeDisplay - 1
    }
}
