package com.leftovers.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onSizeChanged
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
    val boughtBefore by shopping.bought.collectAsState(initial = emptyList())
    val list = items ?: return
    // A limit for this shop, kept on this phone; 0 for none.
    val prefs = remember { context.getSharedPreferences("shopping", android.content.Context.MODE_PRIVATE) }
    var limit by remember { mutableStateOf(prefs.getLong("limit", 0L)) }
    // How to change and remove items, said under the list until an item's been swiped away once.
    var taught by remember { mutableStateOf(prefs.getBoolean("swipeTaught", false)) }
    var setLimit by remember { mutableStateOf(false) }
    val toBuy = list.filter { !it.inCart }
    val cart = list.filter { it.inCart }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var line by rememberSaveable { mutableStateOf("") }

    fun add() {
        // What isn't typed comes from last time: "Rice" after buying it by the kg is a kg at last time's rate.
        val item = Shopping.parse(line, boughtBefore) ?: return
        line = ""
        scope.launch { shopping.save(item) }
    }

    fun remove(item: ShoppingItem) = scope.launch {
        if (!taught) { taught = true; prefs.edit().putBoolean("swipeTaught", true).apply() }
        shopping.delete(item)
        undo.show("${item.name} removed") { shopping.restore(listOf(item)) }
    }
    if (setLimit) {
        var text by remember { mutableStateOf(if (limit > 0) AmountInput.fromMinor(limit) else "") }
        GlassAlertDialog(
            onDismissRequest = { setLimit = false },
            title = { Text("Spend limit for this shop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("The basket shows how close you are, and turns red past it.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    MoneyField(text, { text = it }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    limit = AmountInput.toMinor(text)?.takeIf { it > 0 } ?: 0L
                    prefs.edit().putLong("limit", limit).apply()
                    setLimit = false
                }) { Text("Save", color = c.accent) }
            },
            dismissButton = {
                if (limit > 0) androidx.compose.material3.TextButton(onClick = {
                    limit = 0L
                    prefs.edit().remove("limit").apply()
                    setLimit = false
                }) { Text("No limit", color = c.textSecondary) }
            },
        )
    }

    editing?.let { item -> ShoppingItemSheet(item, boughtBefore, onDismiss = { editing = null }) }
    if (checkout && cart.isNotEmpty()) CheckoutSheet(cart, onDismiss = { checkout = false })

    GlassScreen(
        title = "Shopping list",
        onBack = onBack,
        actions = {
            RoundButton(Lucide.Target, if (limit > 0) "Spend limit ${money.formatWhole(limit)}" else "Set a spend limit", { setLimit = true }, tint = if (limit > 0) c.accent else c.textPrimary)
            if (list.any { !it.inCart }) RoundButton(Lucide.Share2, "Share the list", {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, Shopping.shareText(list))
                runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share the list")) }
            })
        },
        bottomBar = {
            // The basket: what it comes to, then one wide button, so nothing has to squeeze.
            if (cart.isEmpty()) androidx.compose.runtime.SideEffect { undo.lift = 0.dp }
            if (cart.isNotEmpty()) {
                val sum = Shopping.sum(cart)
                // Solid, not glass: the list scrolls underneath and mustn't show through its text. Undo shows above it.
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { undo.lift = 0.dp } }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).navigationBarsPadding()
                        .onSizeChanged { undo.lift = with(density) { it.height.toDp() } }
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, com.leftovers.app.ui.components.glassBorder(c), RoundedCornerShape(28.dp)),
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "In the basket · ${cart.size} ${if (cart.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                money.format(sum.totalMinor) + if (limit > 0) " of ${money.formatWhole(limit)}" else "",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (limit > 0 && sum.totalMinor > limit) c.negative else c.textPrimary,
                            )
                        }
                        if (limit > 0) {
                            Spacer(Modifier.height(10.dp))
                            com.leftovers.app.ui.components.ProgressLine(sum.totalMinor.toFloat() / limit, Modifier.fillMaxWidth(), height = 6.dp)
                            if (sum.totalMinor > limit) Text("${money.format(sum.totalMinor - limit)} over", style = MaterialTheme.typography.labelMedium, color = c.negative, modifier = Modifier.padding(top = 6.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Add to spending", { checkout = true }, Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Adding: one field. A line like "Rice 2 kg 60" fills in the name, how much and the rate.
            item { AddLine(line, { line = it }, ::add) }
            item {
                Text(
                    "Type a name, how much and the rate: Rice 2 kg 60",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 12.dp),
                )
            }
            // Buy again: things bought before, most often first, as they were last time.
            val again = Shopping.buyAgain(boughtBefore, list)
            if (again.isNotEmpty()) {
                item { ListHeading("Buy again", null) }
                item {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(again, key = { it.id }) { old ->
                            Chip(
                                old.name + (old.totalMinor?.let { " · ${money.formatWhole(it)}" } ?: ""),
                                { scope.launch { shopping.save(Shopping.again(old)) } },
                                icon = Lucide.Plus,
                            )
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Lucide.ShoppingBasket,
                        "Nothing on the list",
                        "Tick things as they go in the basket, then add them to your spending in one go.",
                    )
                }
                return@LazyColumn
            }
            if (toBuy.isNotEmpty()) {
                item {
                    val sum = Shopping.sum(toBuy)
                    ListHeading(
                        "To buy",
                        when {
                            sum.totalMinor == 0L -> "${sum.unpriced} without a rate"
                            sum.unpriced > 0 -> "about ${money.formatWhole(sum.totalMinor)} + ${sum.unpriced} unpriced"
                            else -> "about ${money.formatWhole(sum.totalMinor)}"
                        },
                    )
                }
                // In aisles, in the order a shop is walked, once there's enough to sort.
                val aisles = if (toBuy.size >= 4) Shopping.bySection(toBuy) else listOf("" to toBuy)
                aisles.forEach { (aisle, inAisle) ->
                    if (aisle.isNotEmpty()) item(key = "aisle-$aisle") {
                        Text(aisle.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.1f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.textTertiary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                    }
                    item(key = "list-$aisle") { ItemCard(inAisle, onTick = { scope.launch { shopping.save(it.copy(inCart = true)) } }, onOpen = { editing = it }, onRemove = ::remove) }
                }
            }
            if (!taught && list.isNotEmpty()) {
                item(key = "hint") {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.ChevronLeft, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tap an item to change it · swipe left to remove", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                    }
                }
            }
            if (cart.isNotEmpty()) {
                item { ListHeading("In the basket", null, Modifier.padding(top = 16.dp)) }
                item { ItemCard(cart, onTick = { scope.launch { shopping.save(it.copy(inCart = false)) } }, onOpen = { editing = it }, onRemove = ::remove) }
            }
        }
    }
}

/** A section's name, with its sum on the right. */
@Composable
private fun ListHeading(title: String, trailing: String?, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
}

/** The add field: one pill, with its button inside, lit once there's something to add. */
@Composable
private fun AddLine(text: String, onChange: (String) -> Unit, onAdd: () -> Unit) {
    val c = LocalAppColors.current
    val ready = text.isNotBlank()
    val bg by androidx.compose.animation.animateColorAsState(if (ready) c.accent else c.glassStrong, label = "addBg")
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.height(56.dp).padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Add an item", style = MaterialTheme.typography.bodyLarge, color = c.textTertiary)
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onAdd() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bg).pressable(onAdd, pressedScale = 0.9f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Plus, contentDescription = "Add", tint = if (ready) c.onAccent else c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A round tick, filled once the item is in the basket. */
@Composable
private fun Tick(on: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.accent else Color.Transparent, label = "tickBg")
    val edge by androidx.compose.animation.animateColorAsState(if (on) c.accent else c.textTertiary, label = "tickEdge")
    Box(
        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).pressable(onClick, pressedScale = 0.85f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bg)
                .then(Modifier.border(1.5.dp, edge, androidx.compose.foundation.shape.CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Lucide.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ItemCard(items: List<ShoppingItem>, onTick: (ShoppingItem) -> Unit, onOpen: (ShoppingItem) -> Unit, onRemove: (ShoppingItem) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val context = LocalContext.current
    // No padding around the rows, so a row being swiped away is red right to the card's edges.
    Glass(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp)) {
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) RowDivider(inset = 64.dp)
                // Swipe left to take it off the list (Undo brings it back).
                androidx.compose.runtime.key(item.id) {
                SwipeToRemove(onRemove = { onRemove(item) }) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).pressable({ onOpen(item) }, pressedScale = 0.99f).padding(start = 8.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tick(item.inCart) { onTick(item) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).alpha(if (item.inCart) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                            // A single plain item's price is already on the right.
                            item.rateMinor?.takeIf { item.unit.isNotBlank() || item.quantity != 1.0 }?.let { money.format(it) + if (item.unit.isNotBlank()) " / ${item.unit}" else " each" },
                        ).joinToString("  ·  ")
                        if (detail.isNotEmpty() || item.rateMinor == null) {
                            Text(
                                detail.ifEmpty { "Add how much and the rate" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (detail.isEmpty()) c.textTertiary else c.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                    item.link?.let { link ->
                        Icon(
                            Lucide.ExternalLink, contentDescription = "Open the shop's page", tint = c.textSecondary,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).pressable({
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
                            }).padding(10.dp),
                        )
                    }
                    item.totalMinor?.let {
                        Text(money.format(it), style = MaterialTheme.typography.titleMedium, color = if (item.inCart) c.textSecondary else c.textPrimary, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                }
                }
            }
        }
    }
}

/**
 * A row that can be swiped left to remove it. It follows the finger 1:1; the red behind deepens and the bin grows as
 * it goes, with a tick once it's far enough. Let go past a third of the way (or flick it), and it carries on off at the
 * finger's speed, then its space closes smoothly before [onRemove]; let go short, and it springs back.
 */
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    // How far the finger has actually gone; where the row is drawn comes from it, so frames never build on each other.
    var dragged by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val give = with(density) { 28.dp.toPx() }
    val threshold = width * 0.35f
    val progress = (-offset.value / threshold).coerceIn(0f, 1f)
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        if (armed) 1.15f else 0.75f + 0.25f * progress,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "binScale",
    )
    fun settle(velocity: Float) = scope.launch {
        // Far enough, or flicked: off it goes, keeping the finger's speed; then the space closes.
        if (offset.value < -threshold || velocity < -1800f) {
            offset.animateTo(-width * 1.1f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 380f), initialVelocity = velocity)
            gone = true
        } else {
            offset.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 420f), initialVelocity = velocity)
            armed = false
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = !gone,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 500f)) +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
    ) {
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (gone) onRemove() } }
        Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
            // Behind: only while it's moving, as the rows are glass.
            if (offset.value < 0f) {
                Box(
                    Modifier.matchParentSize().background(c.negative.copy(alpha = 0.35f + 0.65f * progress)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Lucide.Trash2, contentDescription = null, tint = Color.White,
                        modifier = Modifier.padding(end = 28.dp).size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                }
            }
            Box(
                Modifier
                    .graphicsLayer { translationX = offset.value }
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        // Picks up from wherever the row is, even mid-spring.
                        onDragStarted = { dragged = offset.value; offset.stop() },
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            dragged = (dragged + delta).coerceIn(-width, width)
                            // Leftwards 1:1; rightwards it gives a little and eases to a stop, so it feels anchored.
                            val shown = if (dragged > 0f) give * (1f - kotlin.math.exp(-dragged / (give * 3f))) else dragged
                            scope.launch { offset.snapTo(shown) }
                            val now = shown < -threshold
                            if (now != armed) { armed = now; if (now) com.leftovers.app.ui.components.Haptics.tick(view) }
                        },
                        onDragStopped = { velocity -> settle(if (offset.value > 0f) 0f else velocity) },
                    ),
            ) { content() }
        }
    }
}

/** Units to pick from; the rate is for one of them. */
private val Units = listOf("" to "Each", "kg" to "kg", "g" to "g", "L" to "L", "ml" to "ml", "pack" to "Pack", "dozen" to "Dozen")

/** One item, to change: name, how much, the rate per unit, a shop's page and its price there. */
@Composable
private fun ShoppingItemSheet(item: ShoppingItem, boughtBefore: List<ShoppingItem>, onDismiss: () -> Unit) {
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
            // What it cost last time, to see prices move.
            Shopping.lastPrice(name, boughtBefore)?.let { last ->
                val then = java.time.Instant.ofEpochMilli(last.boughtAt ?: 0).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val was = money.format(last.rateMinor!!) + if (last.unit.isNotBlank()) "/${last.unit}" else ""
                val change = if (rateMinor != null && last.unit == unit && rateMinor != last.rateMinor) {
                    val diff = rateMinor - last.rateMinor
                    if (diff > 0) " · ${money.format(diff)} more now" else " · ${money.format(-diff)} less now"
                } else ""
                Text("Last time: $was on ${then.friendlyLabel()}$change", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
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
