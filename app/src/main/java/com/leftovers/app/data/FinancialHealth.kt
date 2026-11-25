package com.leftovers.app.data

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A financial health score out of 100 (#73), from five things the app can see, each with a plain reason. A part
 * without enough to go on (no income yet, under three months of it) is left out, and the others count for more.
 */
object FinancialHealth {
    /** One measure: its [weight] in the score, how well it's doing from 0 to 1 ([score], null when it can't tell), and why. */
    data class Part(val name: String, val weight: Int, val score: Float?, val detail: String)

    data class Health(val score: Int, val parts: List<Part>) {
        val label: String get() = when {
            score >= 80 -> "Great"
            score >= 60 -> "Good"
            score >= 40 -> "Fair"
            else -> "Needs attention"
        }

        /** The part pulling the score down the most, to say what to look at. */
        val weakest: Part? get() = parts.filter { it.score != null }.minByOrNull { (it.score ?: 1f) * it.weight }?.takeIf { (it.score ?: 1f) < 0.7f }

        val summary: String get() = when {
            score >= 80 -> "You're in good shape. Keep it up."
            weakest != null -> "Most room to grow: ${weakest!!.name.lowercase()}."
            else -> "Steady. A little more saved each month would lift it."
        }
    }

    /**
     * The score for a period: what came in ([income]) and went out ([spent]) over [months] months, the budget for it
     * if there is one, what's owed to others now, the balance across the accounts, and each recent month's income.
     */
    fun of(
        income: Long,
        spent: Long,
        months: Double,
        budget: Long?,
        owe: Long,
        balance: Long,
        monthlyIncomes: List<Long>,
        format: (Long) -> String,
    ): Health? {
        if (income <= 0 && spent <= 0) return null
        val m = months.coerceAtLeast(1.0)
        val perMonthIncome = income / m
        val perMonthSpend = spent / m
        val parts = ArrayList<Part>()

        // Saving: 20% kept or more is full marks.
        parts += if (income > 0) {
            val rate = (income - spent).toDouble() / income
            Part("Saving", 25, scale(rate, 0.0, 0.2),
                if (rate >= 0) "You kept ${pct(rate)} of what came in" else "You spent ${pct(-rate)} more than came in")
        } else Part("Saving", 25, null, "No income in this period yet")

        // Spending: within the budget is full marks, 30% over is none. Without a budget, against income: 80% or less.
        parts += when {
            budget != null && budget > 0 -> {
                val used = spent.toDouble() / budget
                Part("Spending", 25, 1f - scale(used, 1.0, 1.3), "You spent ${pct(used)} of your budget")
            }
            income > 0 -> {
                val used = spent.toDouble() / income
                Part("Spending", 25, 1f - scale(used, 0.8, 1.1), "You spent ${pct(used)} of what came in")
            }
            else -> Part("Spending", 25, null, "Set a budget or log income to see this")
        }

        // Money you owe: none is full marks, a month's income or more is none.
        parts += when {
            owe <= 0 -> Part("Money you owe", 20, 1f, "You don't owe anyone")
            perMonthIncome > 0 -> {
                val share = owe / perMonthIncome
                Part("Money you owe", 20, 1f - scale(share, 0.0, 1.0), "You owe ${format(owe)}, ${pct(share)} of a month's income")
            }
            else -> Part("Money you owe", 20, 0.5f, "You owe ${format(owe)}")
        }

        // Cushion: six months of spending in the accounts is full marks.
        parts += if (perMonthSpend > 0) {
            val cover = balance / perMonthSpend
            Part("Cushion", 20, scale(cover, 0.0, 6.0), if (cover <= 0) "Nothing put by for a rainy day yet" else "Your balance would cover ${months(cover)} of spending")
        } else Part("Cushion", 20, null, "No spending to measure against yet")

        // Steady income: how much it moves month to month, over the months that had any.
        val earning = monthlyIncomes.filter { it > 0 }
        parts += if (earning.size >= 3) {
            val mean = earning.average()
            val sd = sqrt(earning.sumOf { (it - mean) * (it - mean) } / earning.size)
            val cv = sd / mean
            Part("Steady income", 10, 1f - scale(cv, 0.1, 0.6),
                if (cv <= 0.15) "Your income is steady month to month" else "Your income changes by about ${pct(cv)} month to month")
        } else Part("Steady income", 10, null, "Needs three months with income")

        val counted = parts.filter { it.score != null }
        if (counted.isEmpty()) return null
        val total = counted.sumOf { it.weight }
        val score = (counted.sumOf { (it.score!! * it.weight).toDouble() } / total * 100).roundToInt().coerceIn(0, 100)
        return Health(score, parts)
    }

    /** How far [v] is from [low] (0) to [high] (1). */
    private fun scale(v: Double, low: Double, high: Double): Float = ((v - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()

    private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

    private fun months(v: Double) = when {
        v >= 12 -> "over a year"
        v >= 1.95 -> "${v.roundToInt()} months"
        v >= 0.95 -> "a month"
        else -> "${(v * 4.3).roundToInt().coerceAtLeast(1)} weeks"
    }
}
