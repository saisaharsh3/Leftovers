package com.leftovers.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One thing on the shopping list: how much ([quantity] of [unit], like 2 kg), its rate per unit, and a shop's page
 * for it if there is one. Ticked ([inCart]) once it's in the basket; added to spending from there.
 */
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** How many of [unit]: 1, 2, 0.5... */
    val quantity: Double = 1.0,
    /** "kg", "L", "pack"... blank for plain pieces. */
    val unit: String = "",
    /** The price of one [unit], if known. */
    val rateMinor: Long? = null,
    val link: String? = null,
    val inCart: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** What this line comes to: quantity × rate, rounded to the cent; null without a rate. */
    val totalMinor: Long?
        get() = rateMinor?.let { BigDecimal.valueOf(it).multiply(BigDecimal.valueOf(quantity)).setScale(0, RoundingMode.HALF_UP).toLong() }

    /** "2 kg", "3", "0.5 L"; empty for a single plain piece. */
    val amountText: String
        get() {
            val q = if (quantity % 1.0 == 0.0) quantity.toLong().toString() else BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()
            return when {
                unit.isNotBlank() -> "$q ${unit.trim()}"
                quantity != 1.0 -> q
                else -> ""
            }
        }
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY inCart, position, id")
    fun observeAll(): Flow<List<ShoppingItem>>

    @Upsert
    suspend fun upsert(item: ShoppingItem): Long

    @Upsert
    suspend fun upsertAll(items: List<ShoppingItem>)

    @Delete
    suspend fun delete(item: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shopping_items")
    suspend fun nextPosition(): Int
}

class ShoppingRepository(private val dao: ShoppingDao, private val transactions: TransactionRepository) {
    val items: Flow<List<ShoppingItem>> = dao.observeAll()

    /** New items go at the end of the list. */
    suspend fun save(item: ShoppingItem): Long = dao.upsert(if (item.id == 0L) item.copy(position = dao.nextPosition()) else item)

    suspend fun delete(item: ShoppingItem) = dao.delete(item)
    suspend fun restore(items: List<ShoppingItem>) = dao.upsertAll(items)

    /**
     * Adds [bought] to spending as [Shopping.entries] says, then takes them off the list. Returns how to put it
     * all back, for Undo.
     */
    suspend fun addToSpending(bought: List<ShoppingItem>, oneEntry: Boolean, title: String, categoryId: Long, accountId: Long?, epochDay: Long, format: (Long) -> String): suspend () -> Unit {
        val made = Shopping.entries(bought, oneEntry, title, categoryId, accountId, epochDay, format)
        val ids = made.map { transactions.saveTransaction(it) }
        dao.deleteIds(bought.map { it.id })
        return {
            ids.forEach { id -> transactions.getTransaction(id)?.let { transactions.deleteTransaction(it) } }
            transactions.deleteForever(ids)
            dao.upsertAll(bought)
        }
    }
}

/** The sums and the entries for a shopping list, kept apart from the screen so they can be checked. */
object Shopping {
    /** The list's total, of the items with a rate; and how many have none. */
    data class Sum(val totalMinor: Long, val unpriced: Int)

    fun sum(items: List<ShoppingItem>) = Sum(items.sumOf { it.totalMinor ?: 0L }, items.count { it.rateMinor == null })

    /**
     * Entries for what was bought: one for the whole shop ([title], with each item in its details), or one per
     * item. Items without a price can't be an entry of their own; in one entry they're listed but add nothing.
     */
    fun entries(bought: List<ShoppingItem>, oneEntry: Boolean, title: String, categoryId: Long, accountId: Long?, epochDay: Long, format: (Long) -> String): List<Transaction> {
        fun line(i: ShoppingItem): String = listOfNotNull(
            i.name.trim(),
            i.amountText.takeIf { it.isNotEmpty() },
            i.rateMinor?.let { r -> if (i.quantity != 1.0 || i.unit.isNotBlank()) "× ${format(r)}" + (if (i.unit.isNotBlank()) "/${i.unit.trim()}" else "") + " = ${format(i.totalMinor!!)}" else format(r) },
        ).joinToString(" ")
        return if (oneEntry) {
            val total = bought.sumOf { it.totalMinor ?: 0L }
            if (total <= 0) emptyList()
            else listOf(Transaction(amountMinor = total, type = TxType.EXPENSE, categoryId = categoryId, epochDay = epochDay, note = title.trim().ifBlank { "Shopping" }, accountId = accountId, details = bought.joinToString("\n") { line(it) }))
        } else {
            bought.filter { (it.totalMinor ?: 0L) > 0 }.map { i ->
                Transaction(amountMinor = i.totalMinor!!, type = TxType.EXPENSE, categoryId = categoryId, epochDay = epochDay, note = i.name.trim(), accountId = accountId, details = line(i).removePrefix(i.name.trim()).trim())
            }
        }
    }

    /** The category a shop most likely goes under: Groceries, then anything with food in it, then Other. */
    fun category(categories: List<Category>): Category? {
        val spend = categories.filter { it.type == TxType.EXPENSE && !it.hidden }
        return spend.firstOrNull { it.name.contains("grocer", ignoreCase = true) }
            ?: spend.firstOrNull { it.name.contains("food", ignoreCase = true) }
            ?: spend.firstOrNull { it.name.equals("Other", ignoreCase = true) }
            ?: spend.firstOrNull()
    }

    /** Units as they're usually written: "ltr" and "l" are L, "gms" is g, "pc" is pcs, "doz" is dozen. */
    internal fun tidyUnit(raw: String): String = when (val u = raw.lowercase()) {
        "l", "ltr", "litre", "litres", "liter", "liters" -> "L"
        "g", "gm", "gms", "gram", "grams" -> "g"
        "pc", "pcs", "piece", "pieces" -> "pcs"
        "doz", "dozen" -> "dozen"
        "packet", "packets", "packs" -> "pack"
        "lbs" -> "lb"
        else -> u
    }

    /**
     * Quick adding by typing a line: "Rice 2 kg 60" is 2 kg of rice at 60 a kg; "Milk 30" is one at 30; "Eggs 12"
     * reads 12 as the price. A number right before a unit is the quantity.
     */
    fun parse(text: String): ShoppingItem? {
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        val units = "kg|g|gm|gms|grams?|l|ltr|litres?|liters?|ml|pcs?|pieces?|pack|packs|packets?|dozen|doz|box|boxes|bottles?|cans?|bags?|loaf|loaves|bunch|bunches|lb|lbs|oz"
        val qty = Regex("(?i)(?:^|\\s)(\\d+(?:[.,]\\d+)?)\\s?($units)\\b").find(t)
        var rest = t
        var quantity = 1.0
        var unit = ""
        if (qty != null) {
            quantity = qty.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
            unit = tidyUnit(qty.groupValues[2])
            rest = rest.removeRange(qty.range).trim()
        }
        // A number at the end is the rate.
        val price = Regex("(?:^|\\s)(?:[₹$€£]\\s?)?(\\d+(?:[.,]\\d{1,2})?)$").find(rest)
        var rate: Long? = null
        if (price != null) {
            rate = BigDecimal(price.groupValues[1].replace(',', '.')).movePointRight(2).toLong()
            rest = rest.removeRange(price.range).trim()
        }
        val name = rest.trim().trimEnd(',', '-', '@', 'x', 'X').trim()
        if (name.isEmpty()) return null
        return ShoppingItem(name = name.replaceFirstChar { it.uppercase() }, quantity = quantity, unit = unit, rateMinor = rate)
    }
}
