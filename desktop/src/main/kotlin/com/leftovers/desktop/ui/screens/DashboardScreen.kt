package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.leftovers.app.data.TxType
import com.leftovers.app.data.openBalances
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.ChartStyle
import com.leftovers.desktop.data.DashData
import com.leftovers.desktop.data.DashPeriod
import com.leftovers.desktop.data.Dashboard
import com.leftovers.desktop.data.GroupBy
import com.leftovers.desktop.data.HomeModel
import com.leftovers.desktop.data.Measure
import com.leftovers.desktop.data.Palette
import com.leftovers.desktop.data.Point
import com.leftovers.desktop.data.View
import com.leftovers.desktop.data.Widget
import com.leftovers.desktop.data.WidgetHeight
import com.leftovers.desktop.data.WidgetKind
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.CardShape
import com.leftovers.desktop.ui.components.DayBars
import com.leftovers.desktop.ui.components.Donut
import com.leftovers.desktop.ui.components.EntryRow
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.LineChart
import com.leftovers.desktop.ui.components.Meter
import com.leftovers.desktop.ui.components.PairBars
import com.leftovers.desktop.ui.components.RollingText
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.Series
import com.leftovers.desktop.ui.components.ShareBar
import com.leftovers.desktop.ui.components.SidePanel
import com.leftovers.desktop.ui.components.YearHeatmap
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Which dashboard tab is open, kept while the app runs. */
private object DashMemory { var index by mutableStateOf(0) }

/** For the off-screen snapshots: open the page already customising, or with a widget's settings. */
object DashboardTesting {
    var customise = false
    var settingsFor: Long? = null
}

/** Ready-made charts that can be drawn as bars or as a line. */
private val styleable = setOf(WidgetKind.CASH_FLOW, WidgetKind.NET_WORTH, WidgetKind.SAVINGS_RATE, WidgetKind.WEEKDAYS)

@Composable
fun DashboardScreen(view: View) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val boards = view.book.prefs.dashboards
    val index = DashMemory.index.coerceIn(0, boards.lastIndex)
    val board = boards[index]
    var editing by remember { mutableStateOf(DashboardTesting.customise) }
    var adding by remember { mutableStateOf(false) }
    var settingsFor by remember { mutableStateOf(DashboardTesting.settingsFor) }
    var boardMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    fun saveBoard(b: Dashboard) = store.setPrefs { copy(dashboards = dashboards.toMutableList().also { it[index] = b }) }
    fun saveWidget(w: Widget) = saveBoard(board.copy(widgets = board.widgets.map { if (it.id == w.id) w else it }))

    Screen(
        "Dashboards", maxWidth = 1400.dp,
        actions = {
            Segmented(DashPeriod.entries.map { it to it.label }, board.period, { saveBoard(board.copy(period = it)) })
            Spacer(Modifier.width(4.dp))
            if (editing) Button("Add widget", { adding = true }, icon = Lucide.Plus)
            Button(if (editing) "Done" else "Customise", { editing = !editing }, kind = if (editing) ButtonKind.Primary else ButtonKind.Secondary, icon = if (editing) Lucide.Check else Lucide.SlidersHorizontal)
        },
    ) {
        // The dashboards, as tabs: click to open, and the open one's menu to rename, resize or remove it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            boards.forEachIndexed { i, b ->
                val on = i == index
                Box {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) c.raised else Color.Transparent)
                            .pressable(scaleTo = 0.97f) { if (on) boardMenu = true else DashMemory.index = i }
                            .padding(start = 14.dp, end = if (on) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.name, style = T.type.bodyStrong, color = if (on) c.text else c.text2)
                        if (on) { Spacer(Modifier.width(4.dp)); Icon(Lucide.ChevronDown, null, Modifier.size(14.dp), tint = c.text3) }
                    }
                    if (on) DropdownMenu(boardMenu, { boardMenu = false }, shape = RoundedCornerShape(12.dp), containerColor = if (c.dark) Color(0xFF1E1E26) else Color.White) {
                        DropdownMenuItem({ Text("Rename", style = T.type.body, color = c.text) }, { boardMenu = false; renaming = true }, leadingIcon = { Icon(Lucide.Pencil, null, Modifier.size(16.dp), tint = c.text2) })
                        listOf(2, 3, 4).forEach { n ->
                            DropdownMenuItem(
                                { Text("$n columns", style = T.type.body, color = c.text) }, { boardMenu = false; saveBoard(board.copy(columns = n, widgets = board.widgets.map { it.copy(span = it.span.coerceAtMost(n)) })) },
                                leadingIcon = { Icon(Lucide.LayoutGrid, null, Modifier.size(16.dp), tint = c.text2) },
                                trailingIcon = { if (board.columns == n) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = c.text) },
                            )
                        }
                        DropdownMenuItem({ Text("Make a copy", style = T.type.body, color = c.text) }, {
                            boardMenu = false
                            store.setPrefs { copy(dashboards = dashboards + board.copy(name = board.name + " copy", widgets = board.widgets.mapIndexed { j, w -> w.copy(id = System.nanoTime() + j) })) }
                            DashMemory.index = boards.size
                        }, leadingIcon = { Icon(Lucide.Plus, null, Modifier.size(16.dp), tint = c.text2) })
                        if (boards.size > 1) DropdownMenuItem({ Text("Delete this dashboard", style = T.type.body, color = c.negative) }, {
                            boardMenu = false
                            val before = boards
                            store.setPrefs { copy(dashboards = dashboards.filterIndexed { j, _ -> j != index }) }
                            DashMemory.index = (index - 1).coerceAtLeast(0)
                            app.tell("${board.name} deleted") { store.setPrefs { copy(dashboards = before) } }
                        }, leadingIcon = { Icon(Lucide.Trash2, null, Modifier.size(16.dp), tint = c.negative) })
                    }
                }
            }
            IconButton(Lucide.Plus, {
                store.setPrefs { copy(dashboards = dashboards + Dashboard("Dashboard ${dashboards.size + 1}", emptyList())) }
                DashMemory.index = boards.size
                editing = true
            }, tooltip = "New dashboard")
        }
        if (board.widgets.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(CardShape).border(1.5.dp, c.line, CardShape).padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("An empty dashboard", style = T.type.title, color = c.text)
                Text("Add the charts and lists you want to see, in the order you want them.", style = T.type.body, color = c.text2)
                Spacer(Modifier.height(6.dp))
                Button("Add widget", { editing = true; adding = true }, kind = ButtonKind.Primary, icon = Lucide.Plus)
            }
        } else {
            Grid(view, board, editing, onMove = { from, to ->
                val list = board.widgets.toMutableList()
                list.add(to, list.removeAt(from))
                saveBoard(board.copy(widgets = list))
            }, onSettings = { settingsFor = it }, onRemove = { w ->
                saveBoard(board.copy(widgets = board.widgets.filterNot { it.id == w.id }))
                app.tell("${w.name} removed") { store.undoPrefsBoard(index, board) }
            })
        }
    }
    // Adding: every kind of widget, with what it shows.
    SidePanel(adding, "Add a widget", { adding = false }, width = 560.dp) {
        WidgetKind.entries.forEach { k ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.raised).pressable(scaleTo = 0.98f) {
                    val w = Widget(k, span = k.span.coerceAtMost(board.columns), chart = if (k == WidgetKind.NET_WORTH || k == WidgetKind.THIS_VS_LAST) ChartStyle.LINE else ChartStyle.BARS)
                    saveBoard(board.copy(widgets = board.widgets + w))
                    adding = false
                    if (k == WidgetKind.CUSTOM) settingsFor = w.id
                    app.tell("${w.name} added")
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(k.title, style = T.type.bodyStrong, color = c.text)
                    Text(k.line, style = T.type.small, color = c.text2)
                }
                Icon(Lucide.Plus, null, Modifier.size(16.dp), tint = c.text2)
            }
        }
    }
    val w = board.widgets.firstOrNull { it.id == settingsFor }
    SidePanel(w != null, w?.name.orEmpty(), { settingsFor = null }, width = 500.dp) {
        if (w != null) WidgetSettings(view, board, w, ::saveWidget, onRemove = {
            settingsFor = null
            saveBoard(board.copy(widgets = board.widgets.filterNot { it.id == w.id }))
        }, onCopy = {
            val i = board.widgets.indexOfFirst { it.id == w.id }
            saveBoard(board.copy(widgets = board.widgets.toMutableList().also { it.add(i + 1, w.copy(id = System.nanoTime(), title = w.name + " copy")) }))
        })
    }
    var name by remember(renaming) { mutableStateOf(board.name) }
    com.leftovers.desktop.ui.components.Dialog(renaming, { renaming = false }, width = 420.dp) {
        Text("Rename the dashboard", style = T.type.title, color = c.text)
        Spacer(Modifier.height(14.dp))
        fun done() { if (name.isNotBlank()) saveBoard(board.copy(name = name.trim().take(30))); renaming = false }
        Field(name, { name = it }, Modifier.fillMaxWidth(), onEnter = ::done)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Button("Cancel", { renaming = false }, kind = ButtonKind.Quiet)
            Button("Rename", ::done, kind = ButtonKind.Primary, enabled = name.isNotBlank())
        }
    }
}

/** Puts one dashboard back as it was (Undo after removing a widget). */
private fun com.leftovers.desktop.data.Store.undoPrefsBoard(index: Int, board: Dashboard) =
    setPrefs { copy(dashboards = dashboards.toMutableList().also { if (index in it.indices) it[index] = board }) }

/**
 * The widgets in rows, each taking its columns. While customising, a widget is dragged by its handle: it follows
 * the pointer, and the others make room as it passes over them.
 */
@Composable
private fun Grid(view: View, board: Dashboard, editing: Boolean, onMove: (Int, Int) -> Unit, onSettings: (Long) -> Unit, onRemove: (Widget) -> Unit) {
    val bounds = remember { mutableStateMapOf<Long, Rect>() }
    val handles = remember { mutableStateMapOf<Long, Rect>() }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val cols = board.columns
    // Rows filled left to right; a widget that doesn't fit starts the next row.
    val rows = mutableListOf<MutableList<Widget>>()
    var used = cols
    board.widgets.forEach { w ->
        val span = w.span.coerceIn(1, cols)
        if (used + span > cols) { rows += mutableListOf<Widget>(); used = 0 }
        rows.last() += w
        used += span
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { w ->
                    androidx.compose.runtime.key(w.id) {
                        val isDragged = dragging == w.id
                        val lift by animateFloatAsState(if (isDragged) 1f else 0f, Motion.moved())
                        val here = bounds[w.id]
                        Box(
                            Modifier.weight(w.span.coerceIn(1, cols).toFloat())
                                .onGloballyPositioned { bounds[w.id] = it.boundsInRoot() }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragged && here != null) {
                                        translationX = pointer.x - grab.x - here.left
                                        translationY = pointer.y - grab.y - here.top
                                    }
                                    scaleX = 1f + 0.02f * lift; scaleY = 1f + 0.02f * lift
                                    shadowElevation = 24f * lift
                                    shape = CardShape; clip = false
                                },
                        ) {
                            WidgetCard(view, board, w, editing, onSettings = { onSettings(w.id) }, onRemove = { onRemove(w) }) {
                                Modifier.pointerHoverIcon(PointerIcon.Hand).pointerInput(w.id) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val b = bounds[w.id] ?: return@detectDragGestures
                                            // Where on the card it was grabbed, so it doesn't jump to the pointer.
                                            val handle = handles[w.id] ?: b
                                            pointer = Offset(handle.left + start.x, handle.top + start.y)
                                            grab = pointer - b.topLeft
                                            dragging = w.id
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            pointer += delta
                                            // Over another widget: this one takes its place.
                                            val over = board.widgets.firstOrNull { o -> o.id != w.id && bounds[o.id]?.contains(pointer) == true }
                                            if (over != null) {
                                                val from = board.widgets.indexOfFirst { it.id == w.id }
                                                val to = board.widgets.indexOfFirst { it.id == over.id }
                                                if (from >= 0 && to >= 0) onMove(from, to)
                                            }
                                        },
                                        onDragEnd = { dragging = null },
                                        onDragCancel = { dragging = null },
                                    )
                                }.onGloballyPositioned { handles[w.id] = it.boundsInRoot() }
                            }
                        }
                    }
                }
                // Room left in the row stays empty, so widgets keep their size.
                val left = cols - row.sumOf { it.span.coerceIn(1, cols) }
                if (left > 0) Spacer(Modifier.weight(left.toFloat()))
            }
        }
    }
}

@Composable
private fun WidgetCard(view: View, board: Dashboard, w: Widget, editing: Boolean, onSettings: () -> Unit, onRemove: () -> Unit, handle: () -> Modifier) {
    val c = T.c
    val d = remember(view, w, board.period) { DashData(view, w, board.period) }
    val accent = w.color?.let { Color(it) }
    Column(
        Modifier.fillMaxWidth().height(w.height.dp.dp).clip(CardShape).background(c.surface)
            .border(if (editing) 1.5.dp else 1.dp, if (editing) c.text3.copy(alpha = 0.5f) else c.line, CardShape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(editing, enter = fadeIn(), exit = fadeOut()) {
                Box(handle().padding(end = 8.dp)) { Icon(com.leftovers.app.ui.icons.DesktopIcons.Grip, "Drag to move", Modifier.size(18.dp), tint = c.text2) }
            }
            Column(Modifier.weight(1f)) {
                Text(w.name, style = T.type.heading, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val bits = listOfNotNull(
                    (w.period ?: board.period).label.takeIf { w.kind !in setOf(WidgetKind.UPCOMING, WidgetKind.GOALS, WidgetKind.OWED, WidgetKind.BUDGET, WidgetKind.HEATMAP, WidgetKind.THIS_VS_LAST) },
                    w.accountId?.let { id -> view.account(id)?.name },
                    w.categoryIds.takeIf { it.isNotEmpty() }?.let { ids -> if (ids.size == 1) view.category(ids.first())?.name else "${ids.size} categories" },
                    w.tag?.let { "#$it" },
                )
                if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = T.type.small, color = c.text3, maxLines = 1)
            }
            AnimatedVisibility(editing, enter = fadeIn(), exit = fadeOut()) {
                Row {
                    IconButton(Lucide.Settings, onSettings, size = 28.dp, tooltip = "Change this widget")
                    IconButton(Lucide.X, onRemove, size = 28.dp, tooltip = "Remove")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) { WidgetBody(view, d, w, accent) }
    }
}

@Composable
private fun WidgetBody(view: View, d: DashData, w: Widget, accent: Color?) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    val main = accent ?: c.text
    val month = DateTimeFormatter.ofPattern("MMM yyyy")
    when (w.kind) {
        WidgetKind.CUSTOM -> CustomBody(d, w, main)
        WidgetKind.CASH_FLOW -> Column {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Key(c.positive, "In ${money.formatWhole(d.cashFlow.sumOf { it.income })}")
                Key(accent ?: c.negative, "Out ${money.formatWhole(d.cashFlow.sumOf { it.spent })}")
                Key(c.text2, "Kept ${money.formatWhole(d.cashFlow.sumOf { it.kept })}")
            }
            Spacer(Modifier.height(8.dp))
            if (w.chart == ChartStyle.LINE) LineChart(
                listOf(Series(d.cashFlow.map { it.income }, c.positive, fill = true), Series(d.cashFlow.map { it.spent }, accent ?: c.negative)),
                d.cashFlow.size, Modifier.fillMaxSize(),
            ) { i -> d.cashFlow[i].let { "${it.month.format(month)} · in ${money.formatWhole(it.income)} · out ${money.formatWhole(it.spent)}" } }
            else PairBars(d.cashFlow.map { it.income to it.spent }, c.positive, accent ?: c.negative, Modifier.fillMaxWidth().weight(1f)) { i ->
                d.cashFlow[i].let { "${it.month.format(month)} · in ${money.formatWhole(it.income)} · out ${money.formatWhole(it.spent)}" }
            }
            if (w.chart != ChartStyle.LINE) MonthLabels(d.cashFlow.map { it.month })
        }
        WidgetKind.NET_WORTH -> Column {
            val last = d.netWorth.lastOrNull()?.second ?: 0L
            val first = d.netWorth.firstOrNull()?.second ?: 0L
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(money.formatWhole(last), T.type.title, c.text)
                Spacer(Modifier.width(10.dp))
                Text((if (last >= first) "+" else "−") + money.formatWhole(abs(last - first)) + " in this time", style = T.type.small, color = if (last >= first) c.positive else c.negative)
            }
            Spacer(Modifier.height(8.dp))
            if (w.chart == ChartStyle.BARS) DayBars(d.netWorth.map { it.second.coerceAtLeast(0) }, main, Modifier.fillMaxSize()) { i -> "${d.netWorth[i].first.format(month)} · ${money.formatWhole(d.netWorth[i].second)}" }
            else LineChart(listOf(Series(d.netWorth.map { it.second }, accent ?: c.positive, fill = true)), d.netWorth.size, Modifier.fillMaxSize()) { i ->
                "${d.netWorth[i].first.format(month)} · ${money.formatWhole(d.netWorth[i].second)}"
            }
        }
        WidgetKind.THIS_VS_LAST -> Column {
            val (now, last) = d.thisVsLast
            val sameDay = last.getOrNull(now.size - 1) ?: last.lastOrNull() ?: 0L
            val diff = (now.lastOrNull() ?: 0L) - sameDay
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Key(main, "This month ${money.formatWhole(now.lastOrNull() ?: 0)}")
                Key(c.text3, "Last month by now ${money.formatWhole(sameDay)}")
            }
            Text((if (diff > 0) "${money.formatWhole(diff)} more" else "${money.formatWhole(-diff)} less") + " than at this point last month", style = T.type.small, color = if (diff > 0) c.negative else c.positive)
            Spacer(Modifier.height(8.dp))
            LineChart(listOf(Series(last, c.text3, dashed = true), Series(now, main)), maxOf(now.size, last.size), Modifier.fillMaxSize()) { i ->
                "Day ${i + 1} · ${now.getOrNull(i)?.let { money.formatWhole(it) } ?: "—"} vs ${last.getOrNull(i)?.let { money.formatWhole(it) } ?: "—"}"
            }
        }
        WidgetKind.BY_CATEGORY -> Ranked(d.byCategory, donut = w.span > 1 || w.chart == ChartStyle.DONUT)
        WidgetKind.TOP_PLACES -> Ranked(d.topPlaces, donut = false, counts = true)
        WidgetKind.BIGGEST -> Column(Modifier.verticalScroll(rememberScrollState())) {
            if (d.biggest.isEmpty()) Quiet("Nothing spent in this time")
            d.biggest.forEach { e -> EntryRow(e, { app.edit(e.id) }) }
        }
        WidgetKind.SAVINGS_RATE -> Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(d.overallRate?.let { "$it%" } ?: "—", style = T.type.display, color = if ((d.overallRate ?: 0) >= 0) c.text else c.negative)
                Spacer(Modifier.width(10.dp))
                Text("of income kept", style = T.type.small, color = c.text2, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            val rates = d.savingsRate.map { (it.second ?: 0).toLong().coerceAtLeast(0) }
            if (w.chart == ChartStyle.LINE) LineChart(listOf(Series(rates, accent ?: c.positive, fill = true)), rates.size, Modifier.fillMaxSize()) { i ->
                "${d.savingsRate[i].first.format(month)} · ${d.savingsRate[i].second?.let { "$it%" } ?: "no income"}"
            } else {
                DayBars(rates, accent ?: c.positive, Modifier.fillMaxWidth().weight(1f)) { i -> "${d.savingsRate[i].first.format(month)} · ${d.savingsRate[i].second?.let { "$it%" } ?: "no income"}" }
                MonthLabels(d.savingsRate.map { it.first })
            }
        }
        WidgetKind.WEEKDAYS -> Column {
            val top = d.weekdays.maxByOrNull { it.value }
            if (top != null && top.value > 0) Text("${top.label} costs most, ${money.formatWhole(top.value)} on average", style = T.type.small, color = c.text2)
            Spacer(Modifier.height(8.dp))
            if (w.chart == ChartStyle.LINE) LineChart(listOf(Series(d.weekdays.map { it.value }, main)), 7, Modifier.fillMaxSize().weight(1f)) { i -> "${d.weekdays[i].label} · ${money.formatWhole(d.weekdays[i].value)}" }
            else DayBars(d.weekdays.map { it.value }, main, Modifier.fillMaxSize().weight(1f)) { i -> "${d.weekdays[i].label} · ${money.formatWhole(d.weekdays[i].value)}" }
            Row { d.weekdays.forEach { Text(it.label, style = T.type.small, color = c.text3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        }
        WidgetKind.HEATMAP -> YearHeatmap(d.year, accent ?: c.negative, Modifier.fillMaxWidth()) { i ->
            d.year[i].let { (day, v) -> "${day.friendlyLabel()} · ${if (v > 0) money.format(v) + " spent" else "nothing spent"}" }
        }
        WidgetKind.BUDGET -> BudgetBody(view)
        WidgetKind.UPCOMING -> Column(Modifier.verticalScroll(rememberScrollState())) {
            val today = LocalDate.now()
            val soon = view.recurring.filter { it.active }.map { it to it.toRecurring().nextChargeDate(today) }.filter { it.second <= today.plusDays(30) }.sortedBy { it.second }
            if (soon.isEmpty()) Quiet("Nothing due in the next 30 days")
            val out = soon.filter { it.first.type == TxType.EXPENSE }.sumOf { it.first.amountMinor }
            if (out > 0) Text("${money.formatWhole(out)} going out", style = T.type.small, color = c.text2)
            soon.forEach { (r, day) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(r.categoryEmoji, r.categoryColor, 28.dp, view.book.icons)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = T.type.bodyStrong, color = c.text, maxLines = 1)
                        Text(day.friendlyLabel(), style = T.type.small, color = c.text2)
                    }
                    Text((if (r.type == TxType.INCOME) "+" else "") + money.format(r.amountMinor), style = T.type.bodyStrong, color = if (r.type == TxType.INCOME) c.positive else c.text)
                }
            }
        }
        WidgetKind.GOALS -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (view.goals.isEmpty()) Quiet("No savings goals yet")
            view.goals.forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row {
                        Text(g.name, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${money.formatWhole(g.savedMinor)} of ${money.formatWhole(g.targetMinor)}", style = T.type.small, color = c.text2)
                    }
                    Meter(g.fraction, height = 6.dp, color = Color(g.color))
                }
            }
        }
        WidgetKind.OWED -> Column(Modifier.verticalScroll(rememberScrollState())) {
            val owed = view.book.debts.openBalances().filter { it.open }
            if (owed.isEmpty()) Quiet("Nobody owes anything")
            owed.forEach { b ->
                val theyOwe = b.netMinor > 0
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.person.trim(), style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    Text(if (b.pendingOnly) "Pending" else (if (theyOwe) "owes you " else "you owe ") + money.format(abs(b.netMinor)), style = T.type.small, color = if (theyOwe) c.positive else c.negative)
                }
            }
        }
    }
}

@Composable
private fun CustomBody(d: DashData, w: Widget, main: Color) {
    val c = T.c
    val money = LocalMoney.current
    fun show(v: Long) = if (w.measure == Measure.COUNT) v.toString() else money.formatWhole(v)
    if (w.groupBy == GroupBy.NONE || w.chart == ChartStyle.NUMBER) {
        val total = d.total()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            RollingText(show(total), T.type.hero, if (w.measure == Measure.NET && total < 0) c.negative else main)
            Text(w.measure.label + " · " + d.period.label.lowercase(), style = T.type.small, color = c.text2)
        }
        return
    }
    val points = d.group()
    if (points.isEmpty() || points.all { it.value == 0L }) { Quiet("Nothing to show for this time"); return }
    when (w.chart) {
        ChartStyle.LINE -> LineChart(listOf(Series(points.map { it.value }, main, fill = true)), points.size, Modifier.fillMaxSize()) { i -> "${points[i].label} · ${show(points[i].value)}" }
        ChartStyle.BARS -> if (d.overTime()) DayBars(points.map { it.value.coerceAtLeast(0) }, main, Modifier.fillMaxSize()) { i -> "${points[i].label} · ${show(points[i].value)}" }
        else Ranked(points, donut = false, format = ::show)
        ChartStyle.DONUT -> Ranked(points, donut = true, format = ::show)
        else -> Ranked(points, donut = false, format = ::show)
    }
}

/** A ranking: optionally a donut beside it, then each with its share. Pointing at a row lights its slice. */
@Composable
private fun Ranked(points: List<Point>, donut: Boolean, counts: Boolean = false, format: ((Long) -> String)? = null) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    var hover by remember { mutableStateOf<Int?>(null) }
    if (points.isEmpty()) { Quiet("Nothing in this time"); return }
    val total = points.sumOf { abs(it.value) }.coerceAtLeast(1)
    val colors = points.mapIndexed { i, p -> p.color?.let { Color(it) } ?: Color(Palette.all[i % Palette.all.size]) }
    Row(Modifier.fillMaxSize()) {
        if (donut) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Donut(points.mapIndexed { i, p -> abs(p.value) to colors[i] }, hover, { hover = it }, Modifier.size(150.dp), thickness = 18.dp)
                Text((format ?: money::formatWhole)(hover?.let { points[it].value } ?: total), style = T.type.heading, color = c.text)
            }
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            points.take(12).forEachIndexed { i, p ->
                val lit by animateFloatAsState(if (hover == null || hover == i) 1f else 0.45f, Motion.quick())
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = lit }.pointerInput(i) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                hover = if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) null else i
                            }
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (p.icon != null) { IconBadge(p.icon, p.color ?: Palette.SLATE, 26.dp, app.store.book.icons); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(p.label, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (counts && p.count > 1) Text("${p.count}×  ", style = T.type.small, color = c.text3)
                            Text((format ?: money::formatWhole)(p.value), style = T.type.bodyStrong, color = c.text)
                        }
                        ShareBar(abs(p.value).toFloat() / total, colors[i])
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetBody(view: View) {
    val c = T.c
    val money = LocalMoney.current
    val home = remember(view) { HomeModel.of(view) }
    val b = home.today
    val spentBy = view.entries.filter { it.type == TxType.EXPENSE && it.date in view.book.plan.cycleOf(LocalDate.now()) }.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    val limited = view.book.categories.filter { it.budgetMinor != null }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (b == null) Quiet("No budget set") else {
            Text("${money.formatWhole(b.monthLeft.coerceAtLeast(0))} left · ${money.formatWhole(b.leftToday.coerceAtLeast(0))} today", style = T.type.heading, color = if (b.monthLeft < 0) c.negative else c.text)
            Meter((b.monthlyBudget - b.monthLeft).toFloat() / b.monthlyBudget.coerceAtLeast(1), height = 8.dp)
        }
        limited.forEach { cat ->
            val spent = spentBy[cat.id] ?: 0L
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text(cat.name, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    Text("${money.formatWhole(spent)} of ${money.formatWhole(cat.budgetMinor!!)}", style = T.type.small, color = if (spent > cat.budgetMinor!!) c.negative else c.text2)
                }
                Meter(spent.toFloat() / cat.budgetMinor!!.coerceAtLeast(1), height = 5.dp)
            }
        }
    }
}

@Composable
private fun Key(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = T.type.small, color = T.c.text2)
    }
}

/** Short month names under bars, one to a bar; with many months, every other one. */
@Composable
private fun MonthLabels(months: List<java.time.YearMonth>) {
    val step = if (months.size > 12) 3 else if (months.size > 7) 2 else 1
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        months.forEachIndexed { i, m ->
            Text(
                if (i % step == 0) m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) else "",
                style = T.type.small, color = T.c.text3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
            )
        }
    }
}

@Composable
private fun Quiet(text: String) = Text(text, style = T.type.body, color = T.c.text3)

/** Everything about one widget: its name, what it shows, its size, period, filters and colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSettings(view: View, board: Dashboard, w: Widget, onChange: (Widget) -> Unit, onRemove: () -> Unit, onCopy: () -> Unit) {
    val c = T.c
    var title by remember(w.id) { mutableStateOf(w.title.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.animateContentSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Name")
            Field(title, { title = it.take(40); onChange(w.copy(title = it.take(40).ifBlank { null })) }, Modifier.fillMaxWidth(), placeholder = w.copy(title = null).name)
        }
        if (w.kind == WidgetKind.CUSTOM) {
            Choice("Measure", Measure.entries.map { it to it.label }, w.measure) { onChange(w.copy(measure = it)) }
            Choice("Group by", GroupBy.entries.map { it to it.label }, w.groupBy) { onChange(w.copy(groupBy = it)) }
            if (w.groupBy != GroupBy.NONE) Choice("Show as", ChartStyle.entries.map { it to it.label }, w.chart) { onChange(w.copy(chart = it)) }
        } else if (w.kind in styleable || w.kind == WidgetKind.BY_CATEGORY) {
            val styles = if (w.kind == WidgetKind.BY_CATEGORY) listOf(ChartStyle.LIST to "List", ChartStyle.DONUT to "Donut and list") else listOf(ChartStyle.BARS to "Bars", ChartStyle.LINE to "Line")
            Choice("Show as", styles, w.chart) { onChange(w.copy(chart = it)) }
        }
        Choice("Width", (1..board.columns).map { it to if (it == board.columns) "Full" else "$it of ${board.columns}" }, w.span.coerceIn(1, board.columns)) { onChange(w.copy(span = it)) }
        Choice("Height", WidgetHeight.entries.map { it to it.label }, w.height) { onChange(w.copy(height = it)) }
        if (w.kind !in setOf(WidgetKind.UPCOMING, WidgetKind.GOALS, WidgetKind.OWED, WidgetKind.BUDGET, WidgetKind.HEATMAP, WidgetKind.THIS_VS_LAST)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Period")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("As the dashboard (${board.period.label.lowercase()})", w.period == null) { onChange(w.copy(period = null)) }
                    DashPeriod.entries.forEach { p -> Chip(p.label, w.period == p) { onChange(w.copy(period = p)) } }
                }
            }
        }
        if (w.kind !in setOf(WidgetKind.UPCOMING, WidgetKind.GOALS, WidgetKind.OWED, WidgetKind.BUDGET)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Only")
                if (view.accounts.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Every account", w.accountId == null) { onChange(w.copy(accountId = null)) }
                    view.accounts.forEach { a -> Chip(a.name, w.accountId == a.id) { onChange(w.copy(accountId = a.id)) } }
                }
                if (w.kind != WidgetKind.NET_WORTH) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Every category", w.categoryIds.isEmpty()) { onChange(w.copy(categoryIds = emptySet())) }
                        view.book.categories.filter { !it.hidden }.forEach { cat ->
                            Chip(cat.name + if (cat.type == TxType.INCOME) " · in" else "", cat.id in w.categoryIds) {
                                onChange(w.copy(categoryIds = if (cat.id in w.categoryIds) w.categoryIds - cat.id else w.categoryIds + cat.id))
                            }
                        }
                    }
                    var tag by remember(w.id) { mutableStateOf(w.tag.orEmpty()) }
                    Field(tag, { tag = it; onChange(w.copy(tag = it.trim().removePrefix("#").lowercase().ifBlank { null })) }, Modifier.fillMaxWidth(), placeholder = "Only entries tagged, like #trip")
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(c.raised).border(if (w.color == null) 2.dp else 0.dp, c.text, CircleShape).pressable(scaleTo = 0.9f) { onChange(w.copy(color = null)) },
                    contentAlignment = Alignment.Center,
                ) { Text("A", style = T.type.small.copy(fontWeight = FontWeight.Bold), color = c.text2) }
                Palette.all.forEach { col ->
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(col)).border(if (w.color == col) 2.dp else 0.dp, c.text, CircleShape).pressable(scaleTo = 0.9f) { onChange(w.copy(color = col)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Make a copy", onCopy, icon = Lucide.Plus)
            Button("Remove", onRemove, kind = ButtonKind.Danger, icon = Lucide.Trash2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <V> Choice(label: String, options: List<Pair<V, String>>, selected: V, onPick: (V) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (v, text) -> Chip(text, v == selected) { onPick(v) } }
        }
    }
}
