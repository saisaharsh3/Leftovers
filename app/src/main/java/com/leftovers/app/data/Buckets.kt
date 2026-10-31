package com.leftovers.app.data

/** The 50/30/20 rule (#39): half of income for needs, 30% for wants, 20% for savings. */
enum class Bucket(val label: String, val share: Int) {
    NEED("Needs", 50), WANT("Wants", 30), SAVE("Savings", 20);

    companion object {
        /** Spending categories that are usually needs; the rest are wants until the user moves them. */
        private val needs = setOf("rent", "groceries", "bills & utilities", "bills", "utilities", "transport", "health", "education", "insurance", "emi", "loan")
        private val saves = setOf("savings", "investments", "investment")

        fun of(category: Category): Bucket = category.bucket?.let { b -> entries.firstOrNull { it.name == b } } ?: guess(category.name)

        fun guess(name: String): Bucket = name.trim().lowercase().let { n ->
            when {
                n in saves -> SAVE
                n in needs -> NEED
                else -> WANT
            }
        }
    }
}

/** One bucket's month: how much it may take, and how much went to it. */
data class BucketUse(val bucket: Bucket, val allowed: Long, val used: Long)

/**
 * How [income] splits by 50/30/20, and what went to each: spending by its category's bucket, and money put
 * into savings goals counted as savings.
 */
fun bucketUse(income: Long, expenses: List<TransactionItem>, categories: List<Category>, savedToGoals: Long): List<BucketUse> {
    val bucketOf = categories.associate { it.id to Bucket.of(it) }
    val spent = expenses.filter { it.type == TxType.EXPENSE && !it.outOfBudget }.groupBy { bucketOf[it.categoryId] ?: Bucket.WANT }
        .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    return Bucket.entries.map { b ->
        BucketUse(b, income * b.share / 100, (spent[b] ?: 0L) + if (b == Bucket.SAVE) savedToGoals.coerceAtLeast(0) else 0)
    }
}
