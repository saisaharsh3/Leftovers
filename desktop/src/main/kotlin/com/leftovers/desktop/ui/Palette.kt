package com.leftovers.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.ActivityModel
import com.leftovers.desktop.data.Theme
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.screens.ActivityMemory
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T

/** One thing the palette can do: open a page, an entry, or run an action. */
private data class Command(
    val title: String,
    val group: String,
    val icon: ImageVector? = null,
    val badge: Pair<String, Long>? = null,
    val hint: String? = null,
    val keywords: String = "",
    val run: () -> Unit,
)

/** Scores how well [query] fits [text]: a word that starts the same counts most, letters in order count least. */
internal fun fit(query: String, text: String): Int {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 1
    val t = text.lowercase()
    if (t.startsWith(q)) return 100
    if (t.split(' ', '-', '&', '·').any { it.startsWith(q) }) return 80
    if (q in t) return 60
    var i = 0
    t.forEach { if (i < q.length && it == q[i]) i++ }
    return if (i == q.length) 20 else 0
}

/**
 * Ctrl+K: type to go anywhere or do anything. Up and down move, Enter runs, Esc closes. It grows from the top of
 * the window, where it opened, and goes back the same way.
 */
@Composable
fun CommandPalette(state: AppState, view: View, visible: Boolean, onClose: () -> Unit) {
    val anim = remember { MutableTransitionState(false) }
    anim.targetState = visible
    if (!anim.currentState && !anim.targetState && anim.isIdle) return
    val c = T.c
    val money = LocalMoney.current
    var query by remember(visible) { mutableStateOf("") }
    var index by remember(visible) { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val store = state.store
    fun go(page: Page) { state.page = page }
    val commands = remember(view, visible) {
        buildList {
            add(Command("Add an entry", "Actions", Lucide.Plus, hint = "Ctrl+N", keywords = "new expense spending") { state.edit() })
            add(Command("Add income", "Actions", Lucide.ArrowDownLeft, keywords = "salary money in") { state.edit(type = TxType.INCOME) })
            if (view.accounts.size > 1) add(Command("Move money between accounts", "Actions", Lucide.Repeat, hint = "Ctrl+T", keywords = "transfer") { state.edit(transfer = true) })
            add(Command("New subscription", "Actions", Lucide.Repeat, keywords = "recurring bill") { go(Page.SUBSCRIPTIONS); state.intent = "new" })
            add(Command("New savings goal", "Actions", Lucide.PiggyBank, keywords = "goal save") { go(Page.GOALS); state.intent = "new" })
            add(Command("Lent or borrowed", "Actions", Lucide.HandCoins, keywords = "owe debt money owed") { go(Page.OWED); state.intent = "new" })
            add(Command("Edit the budget", "Actions", Lucide.Target, keywords = "plan monthly") { go(Page.BUDGETS); state.intent = "plan" })
            add(Command("Search entries", "Actions", Lucide.Search, hint = "Ctrl+F", keywords = "find") {
                go(Page.ACTIVITY); java.awt.EventQueue.invokeLater { runCatching { ActivityMemory.searchFocus.requestFocus() } }
            })
            add(Command("Save a backup", "Actions", Lucide.Download, keywords = "export file") { go(Page.SETTINGS); state.intent = "backup" })
            add(Command("Undo", "Actions", Lucide.RotateCcw, hint = "Ctrl+Z") { if (store.undo()) state.tell("Undone") })
            add(Command(if (view.book.prefs.glass) "Turn glass off" else "Turn glass on", "Look", Lucide.Sparkles, keywords = "frosted look") { store.setPrefs { copy(glass = !glass) } })
            Theme.entries.filter { it != view.book.prefs.theme }.forEach { t ->
                add(Command(when (t) { Theme.SYSTEM -> "Follow Windows' theme"; Theme.LIGHT -> "Light theme"; Theme.DARK -> "Dark theme" }, "Look", if (t == Theme.DARK) Lucide.Moon else Lucide.Sun, keywords = "theme mode") { store.setPrefs { copy(theme = t) } })
            }
            add(Command("Keyboard shortcuts", "Help", Lucide.Info, hint = "Ctrl+/", keywords = "keys help") { state.shortcuts = true })
            Page.entries.forEachIndexed { i, p ->
                add(Command(p.title, "Go to", p.icon, hint = if (i < 9) "Ctrl+${i + 1}" else null, keywords = "page open") { go(p) })
            }
            view.accounts.forEach { a ->
                add(Command(a.name, "Accounts", badge = a.icon to a.color, hint = money.formatWhole(a.balanceMinor), keywords = "account balance") {
                    ActivityMemory.query = ActivityMemory.query.copy(accountId = a.id, search = ""); go(Page.ACTIVITY)
                })
            }
            view.book.categories.filter { !it.hidden }.forEach { cat ->
                add(Command(cat.name, "Categories", badge = cat.emoji to cat.color, hint = if (cat.type == TxType.INCOME) "Income" else null, keywords = "category") {
                    ActivityMemory.query = ActivityMemory.query.copy(categoryId = cat.id, search = "", allTime = true); go(Page.ACTIVITY)
                })
            }
        }
    }
    val results = remember(query, commands) {
        val q = query.trim()
        val matched = commands.map { it to maxOf(fit(q, it.title), fit(q, it.keywords) / 2) }.filter { it.second > 0 }
            .let { list -> if (q.isEmpty()) list.filter { it.first.group in setOf("Actions", "Go to") } else list }
            .sortedByDescending { it.second }.map { it.first }
        // Entries matching what's typed, newest first, as Activity's search finds them.
        val entries = if (q.length < 2) emptyList() else view.entries.asSequence()
            .filter { ActivityModel.matches(it, q, view.account(it.accountId)?.name) }.take(8)
            .map { item ->
                Command(
                    item.note.ifBlank { item.categoryName }, "Entries", badge = item.categoryEmoji to item.categoryColor,
                    hint = (if (item.type == TxType.INCOME) "+" else "") + money.format(item.amountMinor) + " · " + item.date.friendlyLabel(),
                ) { state.edit(item.id) }
            }.toList()
        (matched.take(12) + entries)
    }
    LaunchedEffect(results) { index = 0 }
    val list = rememberLazyListState()
    LaunchedEffect(index) { if (results.isNotEmpty()) list.animateScrollToItem(index.coerceIn(0, results.lastIndex)) }
    fun runAt(i: Int) { results.getOrNull(i)?.let { onClose(); it.run() } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(anim, enter = fadeIn(Motion.settle()), exit = fadeOut(Motion.quick())) {
            Box(Modifier.fillMaxSize().background(c.scrim.copy(alpha = c.scrim.alpha * 0.5f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        }
        AnimatedVisibility(
            anim, Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
            enter = fadeIn(Motion.settle()) + scaleIn(Motion.settle(), initialScale = 0.96f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)),
            exit = fadeOut(Motion.quick()) + scaleOut(Motion.quick(), targetScale = 0.97f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)),
        ) {
            Column(
                Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(18.dp)).background(if (c.dark) Color(0xFF18181E) else Color.White).border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (it.key) {
                            Key.DirectionDown -> { index = (index + 1).coerceAtMost(results.lastIndex.coerceAtLeast(0)); true }
                            Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                            Key.Enter, Key.NumPadEnter -> { runAt(index); true }
                            Key.Escape -> { onClose(); true }
                            else -> false
                        }
                    },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Search, null, Modifier.size(18.dp), tint = c.text3)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Go to, find an entry, or do something…", style = T.type.body.copy(fontSize = T.type.body.fontSize * 1.1f), color = c.text3)
                        BasicTextField(
                            query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                            textStyle = T.type.body.copy(fontSize = T.type.body.fontSize * 1.1f, color = c.text), cursorBrush = SolidColor(c.text),
                        )
                    }
                    Text("Esc", style = T.type.small, color = c.text3)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp).background(c.line).heightIn(min = 1.dp, max = 1.dp))
                if (results.isEmpty()) {
                    Text("Nothing matches \"$query\"", style = T.type.body, color = c.text2, modifier = Modifier.padding(20.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(vertical = 6.dp), state = list) {
                        itemsIndexed(results) { i, cmd ->
                            val showGroup = i == 0 || results[i - 1].group != cmd.group
                            Column {
                                if (showGroup) Text(cmd.group.uppercase(), style = T.type.label, color = c.text3, modifier = Modifier.padding(start = 20.dp, top = if (i == 0) 6.dp else 12.dp, bottom = 4.dp))
                                PaletteRow(cmd, i == index, onHover = { index = i }) { runAt(i) }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(visible) { if (visible) runCatching { focus.requestFocus() } }
}

@Composable
private fun PaletteRow(cmd: Command, selected: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val c = T.c
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val bg by animateColorAsState(if (selected) c.raised else Color.Transparent, Motion.quick())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(bg).hoverable(source)
            .clickable(source, null, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            cmd.badge != null -> IconBadge(cmd.badge.first, cmd.badge.second, 26.dp)
            cmd.icon != null -> Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { Icon(cmd.icon, null, Modifier.size(17.dp), tint = c.text2) }
        }
        Spacer(Modifier.width(12.dp))
        Text(cmd.title, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (cmd.hint != null) Text(cmd.hint, style = T.type.small, color = c.text3, maxLines = 1)
    }
}
