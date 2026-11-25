package com.leftovers.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.Shopping
import com.leftovers.app.data.ShoppingItem
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The shopping list: things to buy with how much and their rate, and a shop's page for them. Tick what's in the
 * basket, then add it to spending: as one entry for the whole shop, or one for each item.
 */
@Composable
fun ShoppingListScreen(onBack: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shopping = (context.applicationContext as LeftoversApp).container.shopping
    val items by shopping.items.collectAsState(initial = null)
    val list = items ?: return
    val toBuy = list.filter { !it.inCart }
    val cart = list.filter { it.inCart }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var line by rememberSaveable { mutableStateOf("") }

    fun add() {
        val item = Shopping.parse(line) ?: return
        line = ""
        scope.launch { shopping.save(item) }
    }

    editing?.let { item -> ShoppingItemSheet(item, onDismiss = { editing = null }) }
    if (checkout && cart.isNotEmpty()) CheckoutSheet(cart, onDismiss = { checkout = false })

    GlassScreen(
        title = "Shopping list",
        onBack = onBack,
        bottomBar = {
            if (cart.isNotEmpty()) {
                val sum = Shopping.sum(cart)
                Glass(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).navigationBarsPadding(), strong = true, shape = RoundedCornerShape(26.dp)) {
                    Row(Modifier.padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("In the basket · ${cart.size}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Text(money.format(sum.totalMinor), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        }
                        PrimaryButton("Add to spending", { checkout = true })
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Adding: a line like "Rice 2 kg 60" fills in the name, how much and the rate.
            item {
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassTextField(
                            value = line,
                            onValueChange = { line = it },
                            placeholder = "Add an item, like Rice 2 kg 60",
                            modifier = Modifier.weight(1f),
                            onDone = { add() },
                        )
                        RoundButton(Lucide.Plus, "Add", { add() }, size = 44.dp, container = if (line.isNotBlank()) c.accent else null, tint = if (line.isNotBlank()) c.onAccent else c.textSecondary)
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Lucide.ShoppingBasket,
                        "Nothing on the list",
                        "Type what you need, how much and its rate. Tick things as they go in the basket, then add them to your spending in one go.",
                    )
                }
                return@LazyColumn
            }
            if (toBuy.isNotEmpty()) {
                item {
                    val sum = Shopping.sum(toBuy)
                    SectionHeader("To buy · about ${money.formatWhole(sum.totalMinor)}" + if (sum.unpriced > 0) " + ${sum.unpriced} without a rate" else "")
                }
                item { ItemCard(toBuy, onTick = { scope.launch { shopping.save(it.copy(inCart = true)) } }, onOpen = { editing = it }) }
            }
            if (cart.isNotEmpty()) {
                item { SectionHeader("In the basket") }
                item { ItemCard(cart, onTick = { scope.launch { shopping.save(it.copy(inCart = false)) } }, onOpen = { editing = it }) }
            }
        }
    }
}

@Composable
private fun ItemCard(items: List<ShoppingItem>, onTick: (ShoppingItem) -> Unit, onOpen: (ShoppingItem) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val context = LocalContext.current
    Glass(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) RowDivider(inset = 56.dp)
                Row(
                    Modifier.fillMaxWidth().pressable({ onOpen(item) }, pressedScale = 0.99f).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.inCart,
                        onCheckedChange = { onTick(item) },
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent),
                    )
                    Column(Modifier.weight(1f).alpha(if (item.inCart) 0.7f else 1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.inCart) TextDecoration.LineThrough else null,
                        )
                        val detail = listOfNotNull(
                            item.amountText.takeIf { it.isNotEmpty() },
                            item.rateMinor?.let { money.format(it) + if (item.unit.isNotBlank()) "/${item.unit}" else if (item.quantity != 1.0) " each" else "" },
                        ).joinToString(" × ")
                        Text(
                            detail.ifEmpty { "Tap to add how much and the rate" },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (detail.isEmpty()) c.textTertiary else c.textSecondary,
                            maxLines = 1,
                        )
                    }
                    item.link?.let { link ->
                        Icon(
                            Lucide.ExternalLink, contentDescription = "Open the shop's page", tint = c.textSecondary,
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).pressable({
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
                            }).padding(8.dp),
                        )
                    }
                    item.totalMinor?.let { Text(money.format(it), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
    }
}

/** Units to pick from; the rate is for one of them. */
private val Units = listOf("" to "Each", "kg" to "kg", "g" to "g", "L" to "L", "ml" to "ml", "pack" to "Pack", "dozen" to "Dozen")

/** One item, to change: name, how much, the rate per unit, a shop's page and its price there. */
@Composable
private fun ShoppingItemSheet(item: ShoppingItem, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val scope = rememberCoroutineScope()
    val shopping = (LocalContext.current.applicationContext as LeftoversApp).container.shopping
    var name by rememberSaveable { mutableStateOf(item.name) }
    var quantity by rememberSaveable { mutableStateOf(item.amountText.substringBefore(' ').ifEmpty { "1" }) }
    var unit by rememberSaveable { mutableStateOf(item.unit) }
    var rate by rememberSaveable { mutableStateOf(item.rateMinor?.let { AmountInput.fromMinor(it) } ?: "") }
    var link by rememberSaveable { mutableStateOf(item.link.orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val q = quantity.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    val rateMinor = AmountInput.toMinor(rate)?.takeIf { it > 0 }
    val edited = item.copy(name = name.trim().ifBlank { item.name }, quantity = q, unit = unit, rateMinor = rateMinor, link = link.trim().takeIf { it.startsWith("https://") })

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Item", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            GlassTextField(name, { name = it }, "Name", label = "Name")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    quantity,
                    { new -> if (new.isEmpty() || Regex("\\d{0,5}([.,]\\d{0,3})?").matches(new)) quantity = new },
                    "1",
                    label = "How much",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                MoneyField(rate, { rate = it }, Modifier.weight(1.4f), label = if (unit.isBlank()) "Rate, each" else "Rate per $unit")
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Units) { (u, label) -> Chip(label, { unit = u }, selected = unit == u) }
            }
            edited.totalMinor?.let {
                Text("Comes to ${money.format(it)}", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            GlassTextField(link, { link = it.trim() }, "https://… (optional)", label = "Shop's page", keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
            if (link.startsWith("https://")) {
                SecondaryButton(if (checking) "Checking…" else "Check the price there", {
                    if (checking) return@SecondaryButton
                    checking = true
                    message = null
                    scope.launch {
                        runCatching { com.leftovers.app.util.ProductPrice.check(link) }
                            .onSuccess {
                                rate = AmountInput.fromMinor(it.minor)
                                message = "It's ${money.format(it.minor)} there now" + if (unit.isNotBlank()) ", set as the rate per $unit." else "."
                            }
                            .onFailure { message = it.message }
                        checking = false
                    }
                }, Modifier.fillMaxWidth(), icon = Lucide.Link)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Lucide.Trash2, "Delete", {
                    scope.launch {
                        shopping.delete(item)
                        undo.show("${item.name} removed") { shopping.restore(listOf(item)) }
                    }
                    onDismiss()
                }, size = 56.dp)
                PrimaryButton("Save", { scope.launch { shopping.save(edited) }; onDismiss() }, Modifier.weight(1f), enabled = name.isNotBlank())
            }
        }
    }
}

/**
 * Adding what's in the basket to spending: one entry for the whole shop (its items in the details) or one each,
 * under a category (Groceries or Food first), from an account, on a day. They leave the list; Undo puts it all back.
 */
@Composable
private fun CheckoutSheet(cart: List<ShoppingItem>, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val scope = rememberCoroutineScope()
    val container = (LocalContext.current.applicationContext as LeftoversApp).container
    val categories by container.repository.categories.collectAsState(initial = null)
    val accounts by container.accounts.accounts.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = null)
    val cats = categories ?: return
    val accs = accounts ?: return
    val s = settings ?: return
    var oneEntry by rememberSaveable { mutableStateOf(true) }
    val spend = cats.filter { it.type == TxType.EXPENSE && !it.hidden }
    var categoryId by rememberSaveable { mutableStateOf(Shopping.category(cats)?.id) }
    var accountId by rememberSaveable { mutableStateOf(s.defaultAccountId.takeIf { id -> accs.any { it.id == id } } ?: accs.firstOrNull()?.id) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var pickDay by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("Groceries") }
    val sum = Shopping.sum(cart)
    val priced = cart.count { it.rateMinor != null }
    if (pickDay) DayPickerDialog(LocalDate.ofEpochDay(day), "When did you buy them?", onPick = { day = it.toEpochDay(); pickDay = false }, onDismiss = { pickDay = false })

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add to spending", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "${cart.size} ${if (cart.size == 1) "item" else "items"} · ${money.format(sum.totalMinor)}" + if (sum.unpriced > 0) " (${sum.unpriced} without a rate count as nothing)" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            SegmentedToggle(listOf(true, false), oneEntry, { if (it) "One entry" else "One per item" }, { oneEntry = it }, Modifier.fillMaxWidth())
            if (oneEntry) GlassTextField(title, { title = it }, "Groceries", label = "Name")
            Text("Category", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(spend) { cat -> Chip(cat.name, { categoryId = cat.id }, icon = com.leftovers.app.ui.icons.CategoryIcons[cat.emoji], iconTint = Color(cat.color), selected = cat.id == categoryId) }
            }
            if (accs.size > 1) {
                Text("Paid from", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accs) { a -> Chip(a.name, { accountId = a.id }, icon = com.leftovers.app.ui.icons.CategoryIcons[a.icon], iconTint = Color(a.color), selected = a.id == accountId) }
                }
            }
            Chip(LocalDate.ofEpochDay(day).friendlyLabel(), { pickDay = true }, icon = Lucide.Calendar)
            val count = if (oneEntry) (if (sum.totalMinor > 0) 1 else 0) else priced
            PrimaryButton(
                when {
                    count == 0 -> "Add a rate first"
                    oneEntry -> "Add ${money.format(sum.totalMinor)}"
                    else -> "Add $count entries"
                },
                {
                    val cat = categoryId ?: return@PrimaryButton
                    scope.launch {
                        val restore = container.shopping.addToSpending(cart, oneEntry, title, cat, accountId, day, money::format)
                        onDismiss()
                        undo.show(if (oneEntry) "Added to spending" else "$count entries added") { restore() }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = count > 0 && categoryId != null,
            )
            Text("They come off the list once added.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

/** On the Plan page: what's left to buy, and roughly what it'll come to. */
@Composable
fun ShoppingCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val shopping = (LocalContext.current.applicationContext as LeftoversApp).container.shopping
    val items by shopping.items.collectAsState(initial = null)
    val list = items ?: return
    val toBuy = list.filter { !it.inCart }
    Glass(modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(vertical = 6.dp)) {
            com.leftovers.app.ui.components.ListRow(
                title = when {
                    list.isEmpty() -> "Make a shopping list"
                    toBuy.isEmpty() -> "All in the basket"
                    else -> "${toBuy.size} to buy · about ${money.formatWhole(Shopping.sum(toBuy).totalMinor)}"
                },
                subtitle = if (list.isEmpty()) "Rates and shop links, then add what you bought to spending" else toBuy.take(3).joinToString(", ") { it.name } + if (toBuy.size > 3) " and more" else "",
                leading = { com.leftovers.app.ui.components.IconTile(Lucide.ShoppingBasket, c.textPrimary, size = 42.dp) },
                trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}
