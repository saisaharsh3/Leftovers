package com.leftovers.app.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Sums typed on the add keypad: "120+45×2" is ₹210, worked out the way a calculator does (× and ÷ before + and −),
 * to the paisa. A sum that ends in an operator ("120+") uses what's there; one that can't be worked out (÷ 0) or
 * comes to less than nothing has no amount.
 */
object AmountSum {
    const val OPERATORS = "+−×÷"

    /** The total of [expression] in minor units, or null when there isn't one. */
    fun evaluate(expression: String): Long? {
        val e = expression.trimEnd { it in OPERATORS }
        if (e.isEmpty()) return null
        val numbers = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        val current = StringBuilder()
        for (ch in e) {
            if (ch in OPERATORS) {
                numbers += current.toString().toBigDecimalOrNull() ?: return null
                ops += ch
                current.clear()
            } else {
                current.append(ch)
            }
        }
        numbers += current.toString().toBigDecimalOrNull() ?: return null
        // × and ÷ first, then + and − from left to right.
        val terms = arrayListOf(numbers[0])
        val signs = ArrayList<Char>()
        ops.forEachIndexed { i, op ->
            val n = numbers[i + 1]
            when (op) {
                '×' -> terms[terms.lastIndex] = terms.last().multiply(n)
                '÷' -> {
                    if (n.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(n, 10, RoundingMode.HALF_UP)
                }
                else -> {
                    terms += n
                    signs += op
                }
            }
        }
        var total = terms[0]
        signs.forEachIndexed { i, op -> total = if (op == '+') total.add(terms[i + 1]) else total.subtract(terms[i + 1]) }
        val minor = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (minor.signum() < 0 || minor > MAX) return null
        return minor.toLong()
    }

    /** [expression] spaced out for reading, each number put through [number] ("1,200 + 45 ×"). */
    fun pretty(expression: String, number: (String) -> String): String = buildString {
        val current = StringBuilder()
        for (ch in expression) {
            if (ch in OPERATORS) {
                append(number(current.toString())).append(' ').append(ch).append(' ')
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) append(number(current.toString()))
    }.trimEnd()

    /** The most the amount field takes: 999,999,999.99. */
    private val MAX = BigDecimal("99999999999")
}
