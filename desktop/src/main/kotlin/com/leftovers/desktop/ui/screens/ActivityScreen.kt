package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import com.leftovers.desktop.data.setCategory
import com.leftovers.desktop.data.setAccount
import com.leftovers.desktop.data.duplicate
import com.leftovers.desktop.data.deleteEntries
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.allTags
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.ActivityModel
import com.leftovers.desktop.data.ActivityQuery
import com.leftovers.desktop.data.ActivitySort
import com.leftovers.desktop.data.DayGroup
import com.leftovers.desktop.data.TypeFilter
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.CardShape
import com.leftovers.desktop.ui.components.Empty
import com.leftovers.desktop.ui.components.EntryRow
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Activity's search and filters stay while the app is open, as they were left. */
object ActivityMemory {
    var query by mutableStateOf(ActivityQuery())
    var started = false
    val searchFocus = FocusRequester()
    /** Entries picked with Ctrl or Shift and a click, for changing them together. */
    val selected = androidx.compose.runtime.mutableStateListOf<Long>()
    /** Where a Shift+click range starts. */
    var anchor: Long? = null
    /** The entries on screen in order, for Shift+click and Ctrl+A. */
    var visible: List<Long> = emptyList()

    fun toggle(id: Long) { if (id in selected) selected.remove(id) else selected.add(id); anchor = id }
    fun range(id: Long) {
        val from = visible.indexOf(anchor ?: id).takeIf { it >= 0 } ?: visible.indexOf(id)
        val to = visible.indexOf(id)
        if (from < 0 || to < 0) return toggle(id)
        (minOf(from, to)..maxOf(from, to)).map { visible[it] }.forEach { if (it !in selected) selected.add(it) }
    }
}

@Composable
fun ActivityScreen(view: View) {
    val app = LocalApp.current
    val c = T.c
    val money = LocalMoney.current
    // Shared with the command palette, which can open Activity on an account or category.
    var q by ActivityMemory::query
    LaunchedEffect(Unit) {
        // The first time, Activity shows the account Home is showing.
        if (!ActivityMemory.started) { ActivityMemory.started = true; if (q.accountId == null) q = q.copy(accountId = view.book.prefs.homeAccountId) }
    }
    val model = remember(view, q) { ActivityModel.of(view, q) }
    ActivityMemory.visible = model.groups.flatMap { g -> g.items.map { it.id } }
    // Entries that went away (deleted, filtered out) leave the selection.
    LaunchedEffect(model) { ActivityMemory.selected.retainAll(ActivityMemory.visible.toSet()) }
    val list = rememberLazyListState()
    LaunchedEffect(q.month, q.allTime, q.type, q.sort) { list.scrollToItem(0) }
    val searching = q.search.isNotBlank()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 32.dp),
        ) {
            item(key = "head") {
                Column(Modifier.widthIn(max = 1080.dp).fillMaxWidth().padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activity", style = T.type.display, color = c.text, modifier = Modifier.weight(1f))
                        Field(
                            q.search, { q = q.copy(search = it) }, Modifier.width(300.dp), placeholder = "Search  (Ctrl+F)",
                            leading = Lucide.Search, focusRequester = ActivityMemory.searchFocus,
                        )
                        Spacer(Modifier.width(10.dp))
                        Button("Add entry", { app.edit() }, kind = ButtonKind.Primary, icon = Lucide.Plus)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!searching) MonthSwitcher(q) { q = it }
                        Segmented(TypeFilter.entries.map { it to it.label }, q.type, { q = q.copy(type = it) })
                        Spacer(Modifier.weight(1f))
                        if (view.accounts.size > 1) {
                            FilterMenu("Account", view.accounts.firstOrNull { it.id == q.accountId }?.name, view.accounts.map { it.id to it.name }) { q = q.copy(accountId = it) }
                        }
                        val cats = view.entries.distinctBy { it.categoryId }.sortedBy { it.categoryName }
                        FilterMenu("Category", cats.firstOrNull { it.categoryId == q.categoryId }?.categoryName, cats.map { it.categoryId to it.categoryName }) { q = q.copy(categoryId = it) }
                        val tags = remember(view) { view.entries.allTags() }
                        if (tags.isNotEmpty()) FilterMenu("Tag", q.tag?.let { "#$it" }, tags.mapIndexed { i, t -> i.toLong() to "#$t" }) { i -> q = q.copy(tag = i?.let { tags[it.toInt()] }) }
                        SortMenu(q.sort) { q = q.copy(sort = it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Summary("Spent", money.format(model.expense), c.text)
                        Summary("Income", money.format(model.income), c.positive)
                        Summary("Entries", model.count.toString(), c.text2)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            if (model.groups.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.widthIn(max = 1080.dp).fillMaxWidth().padding(horizontal = 40.dp)) {
                        when {
                            searching || q.narrows -> Empty(Lucide.Search, "No matches", if (q.narrows) "Nothing matches these filters." else "Try a different word.")
                            q.allTime -> Empty(Lucide.ReceiptText, "No entries yet", "Press Add entry, or Ctrl+N, to add your first one.")
                            else -> Empty(Lucide.ReceiptText, "No entries this month", "Press Add entry to log something you forgot.")
                        }
                    }
                }
            }
            items(model.groups, key = { (it.label ?: "") + it.date.toEpochDay() }) { g ->
                Box(Modifier.widthIn(max = 1080.dp).fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp).animateItem()) { Day(view, g) }
            }
            item(key = "end") { Spacer(Modifier.height(72.dp)) }
        }
        SelectionBar(view, Modifier.align(Alignment.BottomCenter))
        VerticalScrollbar(
            rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
            style = defaultScrollbarStyle().copy(unhoverColor = c.text.copy(alpha = 0.12f), hoverColor = c.text.copy(alpha = 0.28f), shape = RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun Summary(label: String, value: String, color: Color) {
    Column {
        Text(label, style = T.type.small, color = T.c.text2)
        Text(value, style = T.type.title, color = color)
    }
}

@Composable
private fun Day(view: View, g: DayGroup) {
    val c = T.c
    val app = LocalApp.current
    val money = LocalMoney.current
    val ids = g.items.map { it.id }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(g.label ?: g.date.friendlyLabel(), style = T.type.heading, color = c.text, modifier = Modifier.weight(1f))
            if (g.label == null) {
                val bits = listOfNotNull(
                    g.spent.takeIf { it > 0 }?.let { money.format(it) + " spent" },
                    g.earned.takeIf { it > 0 }?.let { "+" + money.format(it) },
                )
                Text(bits.joinToString(" · "), style = T.type.small, color = c.text2)
            }
        }
        Column(Modifier.fillMaxWidth().clip(CardShape).background(c.surface).border(1.dp, c.line, CardShape).padding(6.dp)) {
            g.items.forEach { item ->
                androidx.compose.runtime.key(item.id) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        EntryRow(
                            item, { app.edit(item.id) }, part = item.splitOf != null && item.splitOf in ids, showDay = g.label != null,
                            account = view.account(item.accountId)?.name?.takeIf { view.accounts.size > 1 },
                            selected = item.id in ActivityMemory.selected,
                            clicks = selectionClicks(item.id, onOpen = { app.edit(item.id) }, onMenu = { menu = true }),
                        )
                        EntryMenu(view, item.id, menu) { menu = false }
                    }
                }
            }
            g.transfers.forEach { line ->
                HoverRow(null, Modifier.fillMaxWidth()) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Repeat, null, Modifier.size(17.dp), tint = c.text2)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${line.from} to ${line.to}", style = T.type.bodyStrong, color = c.text)
                        Text(listOf("Transfer", line.transfer.note).filter { it.isNotBlank() }.joinToString(" · "), style = T.type.small, color = c.text2)
                    }
                    Text(
                        (if (line.sign > 0) "+" else if (line.sign < 0) "−" else "") + money.format(line.transfer.amountMinor),
                        style = T.type.bodyStrong, color = if (line.sign > 0) c.positive else c.text,
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(Lucide.Trash2, {
                        val t = line.transfer
                        app.store.deleteTransfer(t)
                        app.tell("Transfer deleted") { app.store.saveTransfer(t) }
                    }, tint = c.text3, tooltip = "Delete transfer")
                }
            }
        }
    }
}

/** ‹ November 2026 › and All time. The month slides the way it moved. */
@Composable
private fun MonthSwitcher(q: ActivityQuery, onChange: (ActivityQuery) -> Unit) {
    val c = T.c
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.raised).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!q.allTime) {
            IconButton(Lucide.ChevronLeft, { onChange(q.copy(month = q.month.minusMonths(1))) }, size = 28.dp, tint = c.text)
            AnimatedContent(
                q.month,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(Motion.offset) { if (forward) it / 3 else -it / 3 } + fadeIn(Motion.settle()))
                        .togetherWith(slideOutHorizontally(Motion.offset) { if (forward) -it / 3 else it / 3 } + fadeOut(Motion.quick()))
                },
                label = "month",
            ) { m ->
                Text(
                    m.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + (if (m.year != YearMonth.now().year) " ${m.year}" else ""),
                    style = T.type.bodyStrong, color = c.text, modifier = Modifier.widthIn(min = 96.dp).padding(horizontal = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            IconButton(Lucide.ChevronRight, { if (q.month < YearMonth.now()) onChange(q.copy(month = q.month.plusMonths(1))) }, size = 28.dp, tint = if (q.month < YearMonth.now()) c.text else c.text3)
        }
        Text(
            "All time",
            style = T.type.small.copy(fontWeight = FontWeight.SemiBold),
            color = if (q.allTime) c.text else c.text2,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (q.allTime) (if (c.dark) Color(0xFF2A2A33) else Color.White) else Color.Transparent)
                .pressable(scaleTo = 0.96f) { onChange(q.copy(allTime = !q.allTime)) }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** A filter as a chip: its name, or what's picked, and a menu to pick from or clear. */
@Composable
private fun FilterMenu(label: String, picked: String?, options: List<Pair<Long, String>>, onPick: (Long?) -> Unit) {
    val c = T.c
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(CircleShape).background(if (picked != null) c.accent else c.raised).pressable(scaleTo = 0.96f) { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(picked ?: label, style = T.type.small.copy(fontWeight = FontWeight.SemiBold), color = if (picked != null) c.onAccent else c.text, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, null, Modifier.size(14.dp), tint = if (picked != null) c.onAccent else c.text2)
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(12.dp), containerColor = if (c.dark) Color(0xFF1E1E26) else Color.White) {
            if (picked != null) DropdownMenuItem({ Text("Any ${label.lowercase()}", style = T.type.bodyStrong, color = c.text) }, { onPick(null); open = false })
            options.forEach { (id, name) ->
                DropdownMenuItem({ Text(name, style = T.type.body, color = c.text) }, { onPick(id); open = false })
            }
        }
    }
}

@Composable
private fun SortMenu(sort: ActivitySort, onPick: (ActivitySort) -> Unit) {
    val c = T.c
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Lucide.SlidersHorizontal, { open = true }, tint = if (sort != ActivitySort.NEWEST) c.text else c.text2, tooltip = "Order")
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(12.dp), containerColor = if (c.dark) Color(0xFF1E1E26) else Color.White) {
            ActivitySort.entries.forEach { s ->
                DropdownMenuItem(
                    { Text(s.label, style = T.type.body, color = c.text) }, { onPick(s); open = false },
                    trailingIcon = { if (s == sort) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = c.text) },
                )
            }
        }
    }
}

/**
 * Clicks on an entry: Ctrl adds or removes it from the selection, Shift adds the run up to it, a plain click
 * opens it (or, while some are selected, picks it too), and a right-click opens its menu.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun selectionClicks(id: Long, onOpen: () -> Unit, onMenu: () -> Unit): Modifier = Modifier
    .pointerHoverIcon(PointerIcon.Hand)
    .onClick(keyboardModifiers = { isCtrlPressed }) { ActivityMemory.toggle(id) }
    .onClick(keyboardModifiers = { isShiftPressed && !isCtrlPressed }) { ActivityMemory.range(id) }
    .onClick(keyboardModifiers = { !isCtrlPressed && !isShiftPressed }) {
        if (ActivityMemory.selected.isNotEmpty()) ActivityMemory.toggle(id) else { ActivityMemory.anchor = id; onOpen() }
    }
    .onClick(matcher = androidx.compose.foundation.PointerMatcher.mouse(androidx.compose.ui.input.pointer.PointerButton.Secondary)) { onMenu() }

private val menuColor: Color @Composable get() = if (T.c.dark) Color(0xFF1E1E26) else Color.White

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, color: Color = T.c.text, action: () -> Unit) {
    val c = T.c
    DropdownMenuItem(
        { Text(text, style = T.type.body, color = color) }, action,
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp), tint = if (color == c.text) c.text2 else color) } },
    )
}

/** Right-click on an entry: for it, or for all that are selected when it's one of them. */
@Composable
private fun EntryMenu(view: View, id: Long, open: Boolean, onClose: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    var step by remember(open) { mutableStateOf("") }
    val ids = if (id in ActivityMemory.selected) ActivityMemory.selected.toSet() else setOf(id)
    val many = ids.size > 1
    DropdownMenu(open, onClose, shape = RoundedCornerShape(12.dp), containerColor = menuColor) {
        when (step) {
            "category" -> {
                val type = view.entries.firstOrNull { it.id == id }?.type
                view.book.categories.filter { it.type == type && !it.hidden }.forEach { cat ->
                    MenuItem(cat.name, null) {
                        val n = store.setCategory(ids, cat.id)
                        app.tell("${if (n == 1) "1 entry" else "$n entries"} moved to ${cat.name}") { store.undo() }
                        onClose()
                    }
                }
            }
            "account" -> view.accounts.forEach { a ->
                MenuItem(a.name, null) {
                    val n = store.setAccount(ids, a.id)
                    app.tell("${if (n == 1) "1 entry" else "$n entries"} moved to ${a.name}") { store.undo() }
                    onClose()
                }
            }
            else -> {
                if (!many) MenuItem("Edit", Lucide.Pencil) { onClose(); app.edit(id) }
                if (!many) MenuItem("Duplicate for today", Lucide.Plus) {
                    store.duplicate(id)
                    app.tell("Added a copy for today") { store.undo() }
                    onClose()
                }
                MenuItem(if (many) "Change category of ${ids.size}" else "Change category", Lucide.LayoutGrid) { step = "category" }
                if (view.accounts.size > 1) MenuItem(if (many) "Move ${ids.size} to an account" else "Move to an account", Lucide.Wallet) { step = "account" }
                if (!many) MenuItem(if (id in ActivityMemory.selected) "Unselect" else "Select", Lucide.CircleCheck) { ActivityMemory.toggle(id); onClose() }
                MenuItem(if (many) "Delete ${ids.size}" else "Delete", Lucide.Trash2, c.negative) {
                    store.deleteEntries(ids)
                    ActivityMemory.selected.clear()
                    app.tell(if (many) "${ids.size} entries deleted" else "Entry deleted") { store.undo() }
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun BarButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).pressable(onClick = onClick).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, style = T.type.small.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
}

/** While entries are picked: how many, what they come to, and what to do with them. Slides up from the bottom. */
@Composable
private fun SelectionBar(view: View, modifier: Modifier) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    val ids = ActivityMemory.selected.toSet()
    var shown by remember { mutableStateOf(ids) }
    if (ids.isNotEmpty()) shown = ids
    var menu by remember { mutableStateOf("") }
    androidx.compose.animation.AnimatedVisibility(
        ids.isNotEmpty(), modifier.padding(bottom = 22.dp),
        enter = androidx.compose.animation.slideInVertically(com.leftovers.desktop.ui.theme.Motion.offset) { it } + fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(com.leftovers.desktop.ui.theme.Motion.offset) { it } + fadeOut(),
    ) {
        val items = view.entries.filter { it.id in shown }
        val spent = items.filter { it.type == com.leftovers.app.data.TxType.EXPENSE }.sumOf { it.amountMinor }
        val earned = items.filter { it.type == com.leftovers.app.data.TxType.INCOME }.sumOf { it.amountMinor }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0xFF26262E) else Color(0xFF18181D))
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${shown.size} selected · " + listOfNotNull(spent.takeIf { it > 0 }?.let { money.format(it) }, earned.takeIf { it > 0 }?.let { "+" + money.format(it) }).joinToString(" · "),
                style = T.type.bodyStrong, color = Color.White,
            )
            Spacer(Modifier.width(10.dp))
            Box {
                BarButton("Category", Lucide.LayoutGrid) { menu = "category" }
                DropdownMenu(menu == "category", { menu = "" }, shape = RoundedCornerShape(12.dp), containerColor = menuColor) {
                    val types = items.map { it.type }.toSet()
                    view.book.categories.filter { it.type in types && !it.hidden }.forEach { cat ->
                        val kind = if (types.size > 1) (if (cat.type == com.leftovers.app.data.TxType.INCOME) " · income" else " · spending") else ""
                        MenuItem(cat.name + kind, null) {
                            val n = store.setCategory(shown, cat.id)
                            app.tell("${if (n == 1) "1 entry" else "$n entries"} moved to ${cat.name}") { store.undo() }
                            menu = ""
                        }
                    }
                }
            }
            if (view.accounts.size > 1) Box {
                BarButton("Account", Lucide.Wallet) { menu = "account" }
                DropdownMenu(menu == "account", { menu = "" }, shape = RoundedCornerShape(12.dp), containerColor = menuColor) {
                    view.accounts.forEach { a ->
                        MenuItem(a.name, null) {
                            val n = store.setAccount(shown, a.id)
                            app.tell("${if (n == 1) "1 entry" else "$n entries"} moved to ${a.name}") { store.undo() }
                            menu = ""
                        }
                    }
                }
            }
            BarButton("Delete", Lucide.Trash2, Color(0xFFF58A8A)) {
                val n = shown.size
                store.deleteEntries(shown)
                ActivityMemory.selected.clear()
                app.tell("$n ${if (n == 1) "entry" else "entries"} deleted") { store.undo() }
            }
            IconButton(Lucide.X, { ActivityMemory.selected.clear() }, tint = Color.White.copy(alpha = 0.7f), tooltip = "Clear the selection (Esc)")
        }
    }
}
