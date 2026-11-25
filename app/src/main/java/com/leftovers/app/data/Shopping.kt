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
    /** When it was bought (added to spending): kept off the list, for Buy again and last time's price. */
    val boughtAt: Long? = null,
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
    @Query("SELECT * FROM shopping_items WHERE boughtAt IS NULL ORDER BY inCart, position, id")
    fun observeAll(): Flow<List<ShoppingItem>>

    /** What was bought before, newest first. */
    @Query("SELECT * FROM shopping_items WHERE boughtAt IS NOT NULL ORDER BY boughtAt DESC")
    fun observeBought(): Flow<List<ShoppingItem>>

    @Query("UPDATE shopping_items SET boughtAt = :at, inCart = 0 WHERE id IN (:ids)")
    suspend fun markBought(ids: List<Long>, at: Long?)

    /** Keeps the last [keep] things bought. */
    @Query("DELETE FROM shopping_items WHERE boughtAt IS NOT NULL AND id NOT IN (SELECT id FROM shopping_items WHERE boughtAt IS NOT NULL ORDER BY boughtAt DESC LIMIT :keep)")
    suspend fun trimBought(keep: Int)

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
    val bought: Flow<List<ShoppingItem>> = dao.observeBought()

    /** New items go at the end of the list. */
    suspend fun save(item: ShoppingItem): Long = dao.upsert(if (item.id == 0L) item.copy(position = dao.nextPosition()) else item)

    /** Adds several at once (from the assistant). */
    suspend fun addAll(items: List<ShoppingItem>): List<Long> = items.map { save(it.copy(id = 0)) }

    suspend fun delete(item: ShoppingItem) = dao.delete(item)
    suspend fun restore(items: List<ShoppingItem>) = dao.upsertAll(items)

    /**
     * Adds [bought] to spending as [Shopping.entries] says, then takes them off the list. Returns how to put it
     * all back, for Undo.
     */
    suspend fun addToSpending(bought: List<ShoppingItem>, oneEntry: Boolean, title: String, categoryId: Long, accountId: Long?, epochDay: Long, format: (Long) -> String): suspend () -> Unit {
        val made = Shopping.entries(bought, oneEntry, title, categoryId, accountId, epochDay, format)
        val ids = made.map { transactions.saveTransaction(it) }
        // Off the list, but remembered: Buy again, and what things cost last time.
        dao.markBought(bought.map { it.id }, System.currentTimeMillis())
        dao.trimBought(300)
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

    /** Aisles, in the order most shops are walked; an item goes in the first whose words it has. */
    val sections: List<Pair<String, List<String>>> = listOf(
        "Fruit & veg" to listOf("apple", "banana", "orange", "mango", "grape", "berr", "lemon", "lime", "melon", "pear", "peach", "plum", "kiwi", "pineapple", "papaya", "guava", "pomegranate", "avocado", "tomato", "potato", "onion", "garlic", "ginger", "carrot", "cucumber", "lettuce", "spinach", "cabbage", "cauliflower", "broccoli", "pepper", "capsicum", "chilli", "chili", "bean", "pea", "okra", "brinjal", "eggplant", "aubergine", "zucchini", "courgette", "pumpkin", "mushroom", "coriander", "cilantro", "mint", "herb", "veg", "fruit", "salad", "celery", "radish", "beet", "corn", "leek", "kale"),
        "Dairy & eggs" to listOf("milk", "curd", "yogurt", "yoghurt", "cheese", "butter", "ghee", "cream", "paneer", "egg", "dahi"),
        "Bakery" to listOf("bread", "bun", "roll", "bagel", "croissant", "cake", "muffin", "pav", "tortilla", "pita", "naan"),
        "Meat & fish" to listOf("chicken", "mutton", "lamb", "beef", "pork", "fish", "prawn", "shrimp", "salmon", "tuna", "meat", "sausage", "bacon", "ham", "turkey", "keema"),
        "Frozen" to listOf("frozen", "ice cream", "ice-cream", "peas frozen"),
        "Pantry" to listOf("rice", "atta", "flour", "maida", "dal", "lentil", "pasta", "noodle", "oat", "cereal", "sugar", "salt", "oil", "spice", "masala", "turmeric", "cumin", "sauce", "ketchup", "jam", "honey", "vinegar", "tea", "coffee", "cocoa", "bean", "chickpea", "rajma", "poha", "suji", "semolina", "besan", "soy", "stock", "can", "tin"),
        "Snacks" to listOf("chips", "biscuit", "cookie", "chocolate", "namkeen", "crisps", "nuts", "almond", "cashew", "peanut", "popcorn", "candy", "sweet"),
        "Drinks" to listOf("water", "juice", "soda", "cola", "coke", "pepsi", "beer", "wine", "drink", "squash", "lassi"),
        "Household" to listOf("soap", "detergent", "dish", "cleaner", "tissue", "toilet", "paper", "foil", "bag", "bin", "sponge", "bleach", "phenyl", "battery", "bulb", "candle", "matches", "mop", "broom"),
        "Personal care" to listOf("shampoo", "conditioner", "toothpaste", "toothbrush", "brush", "deodorant", "razor", "lotion", "cream face", "sanitary", "pad", "diaper", "nappy", "wipes", "medicine", "tablet", "vitamin"),
        "Pets" to listOf("dog", "cat food", "pet", "litter"),
    )
    const val OTHER_SECTION = "Everything else"

    /** Which aisle [name] is in. */
    fun section(name: String): String {
        val n = name.lowercase()
        // The aisle whose matching word is longest: "peanut" is a snack though it starts with "pea", "toilet roll" isn't bread.
        return sections.flatMap { (section, words) -> words.filter { w -> Regex("(^|[^a-z])" + Regex.escape(w)).containsMatchIn(n) }.map { section to it.length } }
            .maxByOrNull { it.second }?.first ?: OTHER_SECTION
    }

    /** [items] in aisles, in shop order, each aisle keeping the list's own order. */
    fun bySection(items: List<ShoppingItem>): List<Pair<String, List<ShoppingItem>>> {
        val order = sections.map { it.first } + OTHER_SECTION
        return items.groupBy { section(it.name) }.toList().sortedBy { order.indexOf(it.first) }
    }

    /**
     * Things bought before that aren't on the list now, most often bought first, then most recently: each as it was
     * last bought, ready to add again.
     */
    fun buyAgain(bought: List<ShoppingItem>, onList: List<ShoppingItem>, max: Int = 10): List<ShoppingItem> {
        val have = onList.map { it.name.trim().lowercase() }.toSet()
        return bought.groupBy { it.name.trim().lowercase() }
            .filterKeys { it !in have }
            .values
            .sortedWith(compareByDescending<List<ShoppingItem>> { it.size }.thenByDescending { l -> l.maxOf { it.boughtAt ?: 0 } })
            .take(max)
            .map { l -> l.maxBy { it.boughtAt ?: 0 } }
    }

    /** The last time [name] was bought with a price, if ever. */
    fun lastPrice(name: String, bought: List<ShoppingItem>): ShoppingItem? =
        bought.filter { it.rateMinor != null && it.name.trim().equals(name.trim(), ignoreCase = true) }.maxByOrNull { it.boughtAt ?: 0 }

    /** A new item from one bought before: same name, amount, unit and rate, back on the list. */
    fun again(old: ShoppingItem) = ShoppingItem(name = old.name, quantity = old.quantity, unit = old.unit, rateMinor = old.rateMinor, link = old.link)

    /** The list as text to send: what's still to buy, by aisle. */
    fun shareText(items: List<ShoppingItem>): String = buildString {
        append("Shopping list")
        bySection(items.filter { !it.inCart }).forEach { (section, list) ->
            append("\n\n").append(section)
            list.forEach { i -> append("\n• ").append(i.name).append(i.amountText.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "") }
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

    /**
     * Like [parse], filling in what wasn't typed from the last time it was bought: the rate, and the unit when none
     * was given ("Rice" after buying rice by the kg).
     */
    fun parse(text: String, bought: List<ShoppingItem>): ShoppingItem? {
        val item = parse(text) ?: return null
        val last = lastPrice(item.name, bought) ?: return item
        val sameUnit = item.unit.isBlank() || item.unit.equals(last.unit, ignoreCase = true)
        return item.copy(
            unit = item.unit.ifBlank { last.unit },
            quantity = if (item.unit.isBlank() && item.quantity == 1.0) last.quantity else item.quantity,
            rateMinor = item.rateMinor ?: last.rateMinor?.takeIf { sameUnit },
            link = item.link ?: last.link,
        )
    }
}
