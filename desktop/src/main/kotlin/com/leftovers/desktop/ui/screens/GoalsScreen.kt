package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.ShopLink
import com.leftovers.app.data.ceilDiv
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.ProductPrice
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.Palette
import com.leftovers.desktop.data.Sum
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Card
import com.leftovers.desktop.ui.components.Empty
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.Meter
import com.leftovers.desktop.ui.components.RollingText
import com.leftovers.desktop.ui.components.SectionHeader
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.components.SidePanel
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy")
private fun String.asMonthLabel() = runCatching { YearMonth.parse(this).format(monthYear) }.getOrDefault(this)

@Composable
fun GoalsScreen(view: View) {
    val c = T.c
    val money = LocalMoney.current
    var selected by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<GoalWithSaved?>(null) }
    var creating by remember { mutableStateOf(false) }
    val goals = view.goals
    val active = goals.filter { !it.reached }
    val shown = goals.firstOrNull { it.id == selected } ?: goals.firstOrNull()
    Screen(
        "Savings goals",
        actions = { Button("New goal", { creating = true }, kind = ButtonKind.Primary, icon = Lucide.Plus) },
    ) {
        if (goals.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Empty(
                    Lucide.PiggyBank, "Save for something you want",
                    "A new laptop, a trip, a bike. Set the price and when you want it, and you'll see how much to put aside each month.",
                ) { Button("New goal", { creating = true }, kind = ButtonKind.Primary, icon = Lucide.Plus) }
            }
        } else {
            Card(Modifier.fillMaxWidth(), padding = 26.dp) {
                Text("Saved so far", style = T.type.bodyStrong, color = c.text2)
                Spacer(Modifier.height(8.dp))
                RollingText(money.formatWhole(goals.sumOf { it.savedMinor }), T.type.hero, c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (active.isEmpty()) "Every goal reached" else "Put aside ${money.format(active.sumOf { it.monthlyNeeded() })} this month to stay on track",
                    style = T.type.small, color = c.text2,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Goals")
                    Card(Modifier.fillMaxWidth(), padding = 6.dp) {
                        goals.forEach { g ->
                            androidx.compose.runtime.key(g.id) {
                                HoverRow({ selected = g.id }, Modifier.fillMaxWidth(), selected = g.id == shown?.id) {
                                    IconBadge(g.emoji, g.color, 38.dp, view.book.icons)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row {
                                            Text(g.name, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (g.reached) "Reached" else "${(g.fraction * 100).toInt()}%", style = T.type.small, color = if (g.reached) c.positive else c.text2)
                                        }
                                        Meter(g.fraction, height = 5.dp, color = Color(g.color))
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1.35f)) {
                    AnimatedContent(shown?.id, transitionSpec = { fadeIn(Motion.settle()) togetherWith fadeOut(Motion.quick()) }, label = "goal") { id ->
                        goals.firstOrNull { it.id == id }?.let { GoalDetail(it, view) { editing = it } }
                    }
                }
            }
        }
    }
    SidePanel(creating || editing != null, if (editing != null) "Edit goal" else "New savings goal", { creating = false; editing = null }, width = 480.dp) {
        androidx.compose.runtime.key(editing?.id, creating) { GoalEditor(editing) { id -> creating = false; editing = null; if (id != null) selected = id } }
    }
}

/** How far along, as a ring that fills when it comes into view. */
@Composable
private fun Ring(fraction: Float, color: Color, modifier: Modifier) {
    val c = T.c
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) { grow.animateTo(fraction, Motion.settle()) }
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        drawArc(c.raised, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        drawArc(color, -90f, 360f * grow.value, false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun GoalDetail(g: GoalWithSaved, view: View, onEdit: (GoalWithSaved) -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    var moving by remember(g.id) { mutableStateOf(0) }
    var amountText by remember(g.id) { mutableStateOf("") }
    var askDelete by remember(g.id) { mutableStateOf(false) }
    var checking by remember(g.id) { mutableStateOf(false) }
    var checkMessage by remember(g.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val deposits = view.book.deposits.filter { it.goalId == g.id }.sortedWith(compareByDescending<GoalDeposit> { it.epochDay }.thenByDescending { it.createdAt })
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(g.emoji, g.color, 44.dp, view.book.icons)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = T.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Target ${g.targetMonth.asMonthLabel()}", style = T.type.small, color = c.text2)
                }
                IconButton(Lucide.Pencil, { onEdit(g) }, tooltip = "Edit goal")
                IconButton(Lucide.Trash2, { askDelete = true }, tint = c.negative, tooltip = "Delete goal")
            }
            AnimatedVisibility(askDelete) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete it? Its savings history goes too.", style = T.type.small, color = c.text2, modifier = Modifier.weight(1f))
                    Button("Cancel", { askDelete = false }, kind = ButtonKind.Quiet)
                    Spacer(Modifier.width(6.dp))
                    Button("Delete", {
                        val before = store.book
                        store.deleteGoal(g.toGoal())
                        app.tell("${g.name} deleted") { store.edit { copy(goals = before.goals, deposits = before.deposits) } }
                    }, kind = ButtonKind.Danger)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Ring(g.fraction, Color(g.color), Modifier.size(150.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(g.fraction * 100).toInt()}%", style = T.type.title, color = c.text)
                        Text(if (g.reached) "Reached" else "saved", style = T.type.small, color = c.text2)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RollingText(money.formatWhole(g.savedMinor), T.type.display, c.text)
                    Text("of ${money.format(g.targetMinor)}", style = T.type.body, color = c.text2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button("Add money", { moving = 1 }, kind = ButtonKind.Primary, icon = Lucide.Plus)
                        Button("Withdraw", { moving = -1 }, icon = Lucide.Minus)
                    }
                }
            }
            AnimatedVisibility(moving != 0) {
                val focus = remember { FocusRequester() }
                fun move() {
                    val amount = Sum.minor(amountText) ?: return
                    if (amount <= 0) return
                    val id = store.saveDeposit(GoalDeposit(goalId = g.id, amountMinor = amount * moving, epochDay = LocalDate.now().toEpochDay()))
                    app.tell(if (moving > 0) "${money.format(amount)} added to ${g.name}" else "${money.format(amount)} taken from ${g.name}") {
                        store.edit { copy(deposits = deposits.filterNot { it.id == id }) }
                    }
                    moving = 0; amountText = ""
                }
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField(amountText, { amountText = it }, if (moving > 0) "How much to add" else "How much to take out", Modifier.weight(1f), focus = focus, onEnter = ::move)
                    Button("Cancel", { moving = 0; amountText = "" }, kind = ButtonKind.Quiet)
                    Button(if (moving > 0) "Add" else "Withdraw", ::move, kind = ButtonKind.Primary)
                }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
            if (!g.reached) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Save each month", money.format(g.monthlyNeeded()), Modifier.weight(1f))
                    Tile("Time left", "${g.monthsLeft()} ${if (g.monthsLeft() == 1) "month" else "months"}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                val expected = g.expectedByNow()
                Text(
                    if (g.savedMinor >= expected) "On track. Keep saving ${money.format(g.monthlyNeeded())} a month to reach it by ${g.targetMonth.asMonthLabel()}."
                    else "${money.format(expected - g.savedMinor)} behind where an even pace would be. ${money.format(g.monthlyNeeded())} a month gets it there by ${g.targetMonth.asMonthLabel()}.",
                    style = T.type.small, color = if (g.savedMinor >= expected) c.positive else c.warning,
                )
            }
        }
        if (g.links.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text("Shops", style = T.type.heading, color = c.text)
                Spacer(Modifier.height(8.dp))
                val cheapest = g.links.filter { it.priceMinor != null }.minByOrNull { it.priceMinor!! }
                g.links.forEach { l ->
                    HoverRow({ runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(l.url)) } }, Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(l.shop, style = T.type.bodyStrong, color = c.text)
                            Text(
                                l.checkedAt?.let { "Checked " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().friendlyLabel().replaceFirstChar { ch -> ch.lowercase() } } ?: "Not checked yet",
                                style = T.type.small, color = c.text2,
                            )
                        }
                        if (g.links.size > 1 && l == cheapest) { Text("Cheapest", style = T.type.small, color = c.positive); Spacer(Modifier.width(10.dp)) }
                        l.priceMinor?.let { Text(money.format(it), style = T.type.bodyStrong, color = c.text); Spacer(Modifier.width(8.dp)) }
                        Icon(Lucide.ExternalLink, null, Modifier.size(15.dp), tint = c.text3)
                    }
                }
                checkMessage?.let { Text(it, style = T.type.small, color = c.warning, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(if (checking) "Checking…" else if (g.links.size > 1) "Check prices" else "Check price", {
                        if (checking) return@Button
                        checking = true; checkMessage = null
                        scope.launch {
                            val failed = mutableListOf<String>()
                            val now = System.currentTimeMillis()
                            val checked = withContext(Dispatchers.IO) {
                                g.links.map { l -> runCatching { ProductPrice.check(l.url) }.fold({ l.copy(priceMinor = it.minor, checkedAt = now) }, { failed += l.shop; l }) }
                            }
                            val first = checked.first()
                            store.saveGoal(g.toGoal().copy(linkPriceMinor = first.priceMinor, linkCheckedAt = first.checkedAt, moreLinks = ShopLink.encode(checked.drop(1))))
                            if (failed.isNotEmpty()) checkMessage = "Couldn't read the price on ${failed.joinToString(" and ")}."
                            checking = false
                        }
                    }, icon = Lucide.RotateCcw)
                    cheapest?.priceMinor?.takeIf { it != g.targetMinor }?.let { price ->
                        Button("Make ${money.format(price)} the target", { store.saveGoal(g.toGoal().copy(targetMinor = price)) }, icon = Lucide.Target)
                    }
                }
            }
        }
        if (deposits.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("History")
                Card(Modifier.fillMaxWidth(), padding = 6.dp) {
                    deposits.forEach { d ->
                        androidx.compose.runtime.key(d.id) {
                            HoverRow(null, Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (d.amountMinor >= 0) "Added" else "Withdrawn", style = T.type.bodyStrong, color = c.text)
                                    Text(LocalDate.ofEpochDay(d.epochDay).friendlyLabel(), style = T.type.small, color = c.text2)
                                }
                                Text((if (d.amountMinor >= 0) "+" else "−") + money.format(kotlin.math.abs(d.amountMinor)), style = T.type.bodyStrong, color = if (d.amountMinor >= 0) c.positive else c.text)
                                Spacer(Modifier.width(6.dp))
                                IconButton(Lucide.X, {
                                    store.deleteDeposit(d)
                                    app.tell("Removed from the history") { store.saveDeposit(d) }
                                }, tint = c.text3, tooltip = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    val c = T.c
    Column(modifier.background(c.raised, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, style = T.type.small, color = c.text2)
        Text(value, style = T.type.heading, color = c.text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditor(initial: GoalWithSaved?, onDone: (Long?) -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    val now = YearMonth.now()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.emoji ?: "laptop") }
    var color by remember { mutableStateOf(initial?.color ?: Palette.SKY) }
    var targetText by remember { mutableStateOf(minorText(initial?.targetMinor ?: 0)) }
    var savedText by remember { mutableStateOf("") }
    var months by remember { mutableStateOf(initial?.monthsLeft() ?: 12) }
    var link by remember { mutableStateOf(initial?.link.orEmpty()) }
    var extra by remember { mutableStateOf(initial?.links?.drop(1)?.map { it.url }.orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val target = Sum.minor(targetText) ?: 0
    val already = (initial?.savedMinor ?: 0) + (Sum.minor(savedText) ?: 0)
    val monthly = ceilDiv((target - already).coerceAtLeast(0), months.toLong())
    val targetMonth = now.plusMonths((months - 1).toLong())
    fun save() {
        if (name.isBlank() || target <= 0) return
        val others = initial?.links?.drop(1).orEmpty()
        val more = extra.map { it.trim() }.filter { it.startsWith("http") }.map { u -> others.firstOrNull { it.url == u } ?: ShopLink(u) }
        val base = initial?.toGoal() ?: Goal(name = name, emoji = icon, color = color, targetMinor = target, targetMonth = targetMonth.toString())
        val keepPrice = initial?.link == link.trim()
        val goal = base.copy(
            name = name.trim(), emoji = icon, color = color, targetMinor = target, targetMonth = targetMonth.toString(),
            link = link.trim().takeIf { it.startsWith("http") }, linkPriceMinor = if (keepPrice) base.linkPriceMinor else null,
            linkCheckedAt = if (keepPrice) base.linkCheckedAt else null, moreLinks = ShopLink.encode(more),
        )
        val id = store.saveGoal(goal)
        Sum.minor(savedText)?.takeIf { it > 0 }?.let { store.saveDeposit(GoalDeposit(goalId = id, amountMinor = it, epochDay = LocalDate.now().toEpochDay())) }
        app.tell(if (initial == null) "${goal.name} added" else "Changes saved")
        onDone(id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Field(name, { name = it.take(30) }, Modifier.fillMaxWidth(), placeholder = "What are you saving for? Like a gaming laptop", onEnter = ::save)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryIcons.goalKeys.forEach { k ->
                    Box(Modifier.pressable(scaleTo = 0.9f) { icon = k }) {
                        IconBadge(k, if (k == icon) color else Palette.SLATE, 38.dp)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Palette.all.forEach { col ->
                    Box(
                        Modifier.size(26.dp).pressable(scaleTo = 0.9f) { color = col }
                            .then(Modifier.background(Color(col), androidx.compose.foundation.shape.CircleShape))
                            .then(if (col == color) Modifier.border(2.dp, c.text, androidx.compose.foundation.shape.CircleShape) else Modifier),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Product link (optional)")
            Field(link, { link = it.trim() }, Modifier.fillMaxWidth(), placeholder = "Paste from any shop")
            if (link.startsWith("https://")) {
                Row { Button(if (checking) "Checking the price…" else "Use its price as the target", {
                    if (checking) return@Button
                    checking = true; linkMessage = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { ProductPrice.check(link) } }
                            .onSuccess { targetText = minorText(it.minor); linkMessage = "It costs ${money.format(it.minor)} there now." }
                            .onFailure { linkMessage = it.message ?: "Couldn't read the price." }
                        checking = false
                    }
                }, icon = Lucide.Target) }
            }
            linkMessage?.let { Text(it, style = T.type.small, color = c.text2) }
            extra.forEachIndexed { i, u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(u, { v -> extra = extra.toMutableList().also { it[i] = v.trim() } }, Modifier.weight(1f), placeholder = "The same thing from another shop")
                    IconButton(Lucide.X, { extra = extra.filterIndexed { j, _ -> j != i } }, tooltip = "Remove")
                }
            }
            if (link.isNotBlank()) Row { Button("Add another shop", { extra = extra + "" }, kind = ButtonKind.Quiet, icon = Lucide.Plus) }
        }
        MoneyField(targetText, { targetText = it }, "Target amount", large = true, onEnter = ::save)
        if (initial == null) MoneyField(savedText, { savedText = it }, "Already saved (optional)")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("I want it in")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(Lucide.Minus, { if (months > 1) months-- }, tint = c.text, tooltip = "Fewer months")
                Text("$months ${if (months == 1) "month" else "months"}", style = T.type.heading, color = c.text, modifier = Modifier.padding(horizontal = 12.dp))
                IconButton(Lucide.Plus, { if (months < 120) months++ }, tint = c.text, tooltip = "More months")
                Spacer(Modifier.weight(1f))
                Text("by ${targetMonth.format(monthYear)}", style = T.type.small, color = c.text2)
            }
        }
        if (target > 0) {
            Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                Text("Save every month", style = T.type.small, color = c.text2)
                Text(money.format(monthly), style = T.type.title, color = c.text)
            }
        }
        Row {
            Spacer(Modifier.weight(1f))
            Button(if (initial == null) "Create goal" else "Save", ::save, kind = ButtonKind.Primary, enabled = name.isNotBlank() && target > 0)
        }
    }
}
