package com.leftovers.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class TxType { EXPENSE, INCOME }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** ARGB colour, e.g. 0xFFFF7043. */
    val color: Long,
    val type: TxType,
    /** Monthly budget in minor units (cents/paise); null when no budget is set. */
    val budgetMinor: Long? = null,
    /** Income only: money logged here is added on top of that month's budget (gifts, bonuses). */
    @ColumnInfo(defaultValue = "0") val addsToBudget: Boolean = false,
)

/** Amounts are stored in minor units (amount × 100) so money math never uses floating point. */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("epochDay"), Index("accountId")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long,
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Account the money came from or went to; null only for very old data. */
    val accountId: Long? = null,
    /** Photo of the receipt, stored in the app's private files. */
    val receiptPath: String? = null,
    /** Spent in another currency (travel): what was paid there, e.g. 4500 with [foreignCurrency] "AED". */
    val foreignMinor: Long? = null,
    val foreignCurrency: String? = null,
)

/** A transaction joined with its category, ready to display. */
data class TransactionItem(
    val id: Long,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long,
    val epochDay: Long,
    val note: String,
    val createdAt: Long,
    val categoryName: String,
    val categoryEmoji: String,
    val categoryColor: Long,
    val accountId: Long?,
    val receiptPath: String?,
    val addsToBudget: Boolean = false,
    val foreignMinor: Long? = null,
    val foreignCurrency: String? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    fun toTransaction() = Transaction(id, amountMinor, type, categoryId, epochDay, note, createdAt, accountId, receiptPath, foreignMinor, foreignCurrency)
}

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val color: Long,
    val totalMinor: Long,
)

fun List<TransactionItem>.totalOf(type: TxType): Long =
    filter { it.type == type }.sumOf { it.amountMinor }

fun List<TransactionItem>.categoryTotals(type: TxType): List<CategoryTotal> =
    filter { it.type == type }
        .groupBy { it.categoryId }
        .map { (id, items) ->
            val first = items.first()
            CategoryTotal(id, first.categoryName, first.categoryEmoji, first.categoryColor, items.sumOf { it.amountMinor })
        }
        .sortedByDescending { it.totalMinor }
