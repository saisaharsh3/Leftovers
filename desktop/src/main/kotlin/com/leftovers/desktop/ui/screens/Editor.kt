package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.Category
import com.leftovers.app.data.Transfer
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.EntryDraft
import com.leftovers.desktop.data.Sum
import com.leftovers.desktop.data.deleteEntry
import com.leftovers.desktop.data.draftOf
import com.leftovers.desktop.data.saveEntry
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.Toggle
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.io.File
import java.time.LocalDate

/** What the panel is for: a new entry (of [type]) or the saved one with [id], or a transfer. */
data class EditRequest(val id: Long? = null, val type: TxType = TxType.EXPENSE, val transfer: Boolean = false, val nonce: Long = System.nanoTime())

private enum class Kind { SPENDING, INCOME, TRANSFER }

/** The add and edit panel: it comes in from the right edge, over the dimmed window, and goes back the same way. */
@Composable
fun EditorPanel(request: EditRequest?, onClose: () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = request != null
    var shown by remember { mutableStateOf(request) }
    if (request != null) shown = request
    val c = T.c
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state, enter = fadeIn(Motion.settle()), exit = fadeOut(Motion.quick())) {
            Box(Modifier.fillMaxSize().background(c.scrim.copy(alpha = c.scrim.alpha * 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        }
        AnimatedVisibility(
            state,
            Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(Motion.offset) { it } + fadeIn(Motion.settle()),
            exit = slideOutHorizontally(Motion.offset) { it } + fadeOut(Motion.quick()),
        ) {
            val req = shown ?: return@AnimatedVisibility
            Column(
                Modifier.width(460.dp).fillMaxHeight().padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (c.dark) Color(0xFF16161C) else Color.White)
                    .border(1.dp, c.line, RoundedCornerShape(20.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                androidx.compose.runtime.key(req.nonce) { EditorBody(req, onClose) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorBody(req: EditRequest, onClose: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    val book = store.book
    val start = remember { req.id?.let { store.draftOf(it) } ?: EntryDraft(type = req.type, accountId = book.prefs.defaultAccountId.takeIf { id -> book.accounts.any { it.id == id } } ?: book.accounts.firstOrNull()?.id) }
    var kind by remember { mutableStateOf(if (req.transfer) Kind.TRANSFER else if (start.type == TxType.INCOME) Kind.INCOME else Kind.SPENDING) }
    var amountText by remember { mutableStateOf(if (start.amountMinor <= 0) "" else if (start.amountMinor % 100 == 0L) (start.amountMinor / 100).toString() else java.math.BigDecimal.valueOf(start.amountMinor, 2).toPlainString()) }
    var draft by remember { mutableStateOf(start) }
    var personText by remember { mutableStateOf("") }
    var needCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Transfers: from and to.
    var fromId by remember { mutableStateOf(book.accounts.getOrNull(0)?.id) }
    var toId by remember { mutableStateOf(book.accounts.getOrNull(1)?.id) }
    val amount = Sum.minor(amountText) ?: 0L
    val isSum = amountText.any { it in "+-*/×÷x" } && amountText.trimStart('-').any { it in "+-*/×÷x" }
    val type = if (kind == Kind.INCOME) TxType.INCOME else TxType.EXPENSE
    val amountFocus = remember { FocusRequester() }

    fun save() {
        if (kind == Kind.TRANSFER) {
            val from = fromId ?: return
            val to = toId ?: return
            if (amount <= 0 || from == to) return
            store.saveTransfer(Transfer(fromAccountId = from, toAccountId = to, amountMinor = amount, epochDay = draft.day.toEpochDay(), note = draft.note.trim()))
            app.tell("${money.format(amount)} moved")
            onClose()
            return
        }
        if (draft.categoryId == null || book.categories.none { it.id == draft.categoryId && it.type == type }) { needCategory = true; return }
        val people = (draft.people + personText.split(',')).map { it.trim() }.filter { it.isNotEmpty() }
        val message = store.saveEntry(draft.copy(type = type, amountMinor = amount, people = people), money::format) ?: return
        app.tell(message)
        onClose()
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                it.key == Key.Escape -> { onClose(); true }
                it.key == Key.Enter && it.isCtrlPressed -> { save(); true }
                else -> false
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (draft.isEditing) "Edit entry" else "Add", style = T.type.title, color = c.text, modifier = Modifier.weight(1f))
            IconButton(Lucide.X, onClose, tooltip = "Close")
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!draft.isEditing) {
                Segmented(
                    listOfNotNull(Kind.SPENDING to "Spending", Kind.INCOME to "Income", (Kind.TRANSFER to "Transfer").takeIf { book.accounts.size >= 2 }),
                    kind, { kind = it; needCategory = false },
                )
            }
            // The amount, large; a sum works too, and shows what it comes to.
            Column {
                Field(
                    amountText, { t -> if (t.length <= 40 && t.all { it.isDigit() || it in ".,+-*/×÷x() " }) amountText = t },
                    Modifier.fillMaxWidth(), placeholder = money.symbol + " 0", style = T.type.display, focusRequester = amountFocus, onEnter = ::save,
                )
                AnimatedVisibility(isSum && amount > 0) {
                    Text("= " + money.format(amount), style = T.type.bodyStrong, color = c.text2, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                }
            }
            if (kind == Kind.TRANSFER) {
                AccountChooser("From", fromId) { fromId = it }
                AccountChooser("To", toId) { toId = it }
            } else {
                CategoryChooser(type, draft.categoryId, needCategory) { draft = draft.copy(categoryId = it); needCategory = false }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Note")
                Field(draft.note, { draft = draft.copy(note = it) }, Modifier.fillMaxWidth(), placeholder = if (kind == Kind.TRANSFER) "What it's for" else "Where, or what", onEnter = ::save)
                if (kind != Kind.TRANSFER) {
                    Field(draft.details, { draft = draft.copy(details = it) }, Modifier.fillMaxWidth(), placeholder = "Details (optional)", singleLine = false, minLines = 2)
                }
            }
            DayChooser(draft.day) { draft = draft.copy(day = it) }
            if (kind != Kind.TRANSFER && book.accounts.size > 1) AccountChooser("Account", draft.accountId) { draft = draft.copy(accountId = it) }
            if (kind == Kind.SPENDING) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Split with")
                    if (draft.people.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            draft.people.forEach { p ->
                                Chip(p, selected = true, icon = Lucide.X) { draft = draft.copy(people = draft.people - p) }
                            }
                        }
                    }
                    Field(
                        personText, { personText = it }, Modifier.fillMaxWidth(), placeholder = "A name, then Enter",
                        leading = Lucide.Users,
                        onEnter = {
                            val add = personText.split(',').map { it.trim() }.filter { it.isNotEmpty() && draft.people.none { p -> p.equals(it, true) } }
                            if (add.isNotEmpty()) { draft = draft.copy(people = draft.people + add); personText = "" } else save()
                        },
                    )
                    if (draft.people.isNotEmpty() && amount > 0) {
                        val share = amount / (draft.people.size + 1)
                        Text(
                            "Your share ${money.format(amount - share * draft.people.size)} · ${if (draft.people.size == 1) "they owe" else "each owes"} you ${money.format(share)}",
                            style = T.type.small, color = c.text2,
                        )
                    }
                }
            }
            if (kind != Kind.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Photo")
                    val photo = draft.receiptPath
                    if (photo != null && File(photo).exists()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.ReceiptText, null, Modifier.size(16.dp), tint = c.text2)
                            Spacer(Modifier.width(8.dp))
                            Text(File(photo).name, style = T.type.small, color = c.text2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Button("Open", { runCatching { java.awt.Desktop.getDesktop().open(File(photo)) } }, kind = ButtonKind.Quiet)
                            Button("Remove", { draft = draft.copy(receiptPath = null) }, kind = ButtonKind.Quiet)
                        }
                    } else {
                        Button("Add a photo of the bill", {
                            Files.open("Pick a photo of the bill", listOf("jpg", "jpeg", "png", "webp", "heic"))?.let { f ->
                                store.photosDir.mkdirs()
                                val copy = File(store.photosDir, "receipt-${System.currentTimeMillis()}.${f.extension.lowercase()}")
                                runCatching { f.copyTo(copy) }.onSuccess { draft = draft.copy(receiptPath = copy.absolutePath) }
                            }
                        }, icon = Lucide.Camera)
                    }
                }
                if (!draft.isEditing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Repeat every month", style = T.type.bodyStrong, color = c.text)
                            Text("On the ${com.leftovers.desktop.data.ordinal(draft.day.dayOfMonth)}, as a subscription.", style = T.type.small, color = c.text2)
                        }
                        Toggle(draft.repeatMonthly) { draft = draft.copy(repeatMonthly = it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // The actions stay at the bottom, whatever the scroll.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draft.isEditing) {
                AnimatedContent(confirmDelete, transitionSpec = { fadeIn(Motion.settle()) togetherWith fadeOut(Motion.quick()) }, label = "delete") { asking ->
                    if (asking) Button("Delete it", {
                        val undo = store.deleteEntry(draft.id)
                        app.tell("Entry deleted", undo)
                        onClose()
                    }, kind = ButtonKind.Danger, icon = Lucide.Trash2)
                    else IconButton(Lucide.Trash2, { confirmDelete = true }, tint = c.negative, tooltip = "Delete")
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Ctrl+Enter", style = T.type.small, color = c.text3)
            Button(if (draft.isEditing) "Save" else "Add", ::save, kind = ButtonKind.Primary, enabled = amount > 0)
        }
    }
    LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }
    // A photo dropped on the window goes to this entry.
    LaunchedEffect(app.droppedPhoto) {
        val f = app.droppedPhoto ?: return@LaunchedEffect
        app.droppedPhoto = null
        if (kind == Kind.TRANSFER) return@LaunchedEffect
        store.photosDir.mkdirs()
        val copy = File(store.photosDir, "receipt-${System.currentTimeMillis()}.${f.extension.lowercase()}")
        runCatching { f.copyTo(copy) }.onSuccess { draft = draft.copy(receiptPath = copy.absolutePath); app.tell("Photo added") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChooser(type: TxType, selected: Long?, needed: Boolean, onPick: (Long) -> Unit) {
    val app = LocalApp.current
    val view = app.store.view.value
    val c = T.c
    val since = LocalDate.now().minusDays(120).toEpochDay()
    val usage = remember(view) { view.entries.filter { it.epochDay >= since }.groupingBy { it.categoryId }.eachCount() }
    val list = view.book.categories.filter { it.type == type && (!it.hidden || it.id == selected) }.sortedByDescending { usage[it.id] ?: 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            SectionLabel("Category", Modifier.weight(1f))
            AnimatedVisibility(needed, enter = fadeIn(), exit = fadeOut()) { Text("Pick one", style = T.type.small, color = c.negative) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { cat -> CategoryChip(cat, cat.id == selected) { onPick(cat.id) } }
        }
    }
}

@Composable
private fun CategoryChip(cat: Category, selected: Boolean, onClick: () -> Unit) {
    val c = T.c
    val bg by animateColorAsState(if (selected) Color(cat.color).copy(alpha = if (c.dark) 0.28f else 0.32f) else c.raised, Motion.quick())
    val edge by animateColorAsState(if (selected) Color(cat.color) else Color.Transparent, Motion.quick())
    Row(
        Modifier.clip(CircleShape).background(bg).border(1.5.dp, edge, CircleShape).pressable(scaleTo = 0.95f, onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(cat.emoji, cat.color, 24.dp, LocalApp.current.store.book.icons)
        Spacer(Modifier.width(6.dp))
        Text(cat.name, style = T.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val c = T.c
    val bg by animateColorAsState(if (selected) c.accent else c.raised, Motion.quick())
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, Motion.quick())
    Row(
        Modifier.clip(CircleShape).background(bg).pressable(scaleTo = 0.95f, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = T.type.small.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
        if (icon != null) { Spacer(Modifier.width(6.dp)); Icon(icon, null, Modifier.size(12.dp), tint = fg) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChooser(label: String, selected: Long?, onPick: (Long) -> Unit) {
    val accounts = LocalApp.current.store.view.value.accounts
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            accounts.forEach { a -> Chip(a.name, a.id == selected) { onPick(a.id) } }
        }
    }
}

/** Today, yesterday, or any day from a small calendar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChooser(day: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var calendar by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Day")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Today", day == today) { onPick(today); calendar = false }
            Chip("Yesterday", day == today.minusDays(1)) { onPick(today.minusDays(1)); calendar = false }
            val other = day != today && day != today.minusDays(1)
            Chip(if (other) day.friendlyLabel() else "Another day", other || calendar, icon = Lucide.Calendar) { calendar = !calendar }
        }
        AnimatedVisibility(calendar) { MonthCalendar(day) { onPick(it); calendar = false } }
    }
}

/** A month of days to pick from, with arrows for the months around it. */
@Composable
fun MonthCalendar(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = T.c
    var month by remember { mutableStateOf(java.time.YearMonth.from(selected)) }
    val today = LocalDate.now()
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(c.raised).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(Lucide.ChevronLeft, { month = month.minusMonths(1) }, size = 28.dp)
            Text(
                month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + month.year,
                style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(Lucide.ChevronRight, { month = month.plusMonths(1) }, size = 28.dp)
        }
        Spacer(Modifier.height(6.dp))
        val first = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek
        Row {
            (0 until 7).forEach { i ->
                Text(
                    first.plus(i.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                    style = T.type.label, color = c.text3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val cells = lead + month.lengthOfMonth()
        (0 until (cells + 6) / 7).forEach { week ->
            Row {
                (0 until 7).forEach { col ->
                    val n = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val d = month.atDay(n)
                            val on = d == selected
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(if (on) c.accent else Color.Transparent)
                                    .pressable(scaleTo = 0.9f) { onPick(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    n.toString(),
                                    style = if (d == today) T.type.bodyStrong else T.type.body,
                                    color = when { on -> c.onAccent; d > today -> c.text3; else -> c.text },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
