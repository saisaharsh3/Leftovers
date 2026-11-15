package com.leftovers.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import com.leftovers.desktop.data.Store
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.Notice
import com.leftovers.desktop.ui.components.NoticeBar
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.screens.Placeholder
import com.leftovers.desktop.ui.screens.SettingsScreen
import com.leftovers.desktop.ui.screens.WelcomeScreen
import com.leftovers.desktop.ui.theme.LeftoversTheme
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T

enum class Page(val title: String, val icon: ImageVector) {
    HOME("Home", Lucide.House),
    ACTIVITY("Activity", Lucide.ReceiptText),
    INSIGHTS("Insights", Lucide.ChartPie),
    BUDGETS("Budgets", Lucide.Target),
    SUBSCRIPTIONS("Subscriptions", Lucide.Repeat),
    GOALS("Goals", Lucide.PiggyBank),
    OWED("Money owed", Lucide.HandCoins),
    ACCOUNTS("Accounts", Lucide.Wallet),
    CATEGORIES("Categories", Lucide.LayoutGrid),
    SETTINGS("Settings", Lucide.Settings),
}

private val navGroups = listOf(
    null to listOf(Page.HOME, Page.ACTIVITY, Page.INSIGHTS),
    "Plan" to listOf(Page.BUDGETS, Page.SUBSCRIPTIONS, Page.GOALS, Page.OWED),
    "Money" to listOf(Page.ACCOUNTS, Page.CATEGORIES),
)

/** What every screen can reach: the data, where it is, and the notice bar. */
class AppState(val store: Store) {
    var page by mutableStateOf(Page.HOME)
    var notice by mutableStateOf<Notice?>(null)
    var editing by mutableStateOf<com.leftovers.desktop.ui.screens.EditRequest?>(null)
    var palette by mutableStateOf(false)
    var shortcuts by mutableStateOf(false)
    /** Something the palette asked a page to do once it's open ("new", "plan", "backup"); the page clears it. */
    var intent by mutableStateOf<String?>(null)
    val importer = com.leftovers.desktop.ui.screens.Importer(this)
    val backups = com.leftovers.desktop.ui.screens.BackupOpener()
    /** A photo dropped on the window while the add panel is open, for it to take. */
    var droppedPhoto by mutableStateOf<java.io.File?>(null)

    /**
     * A file dropped on the window or opened: a Leftovers backup opens as one, a photo goes to the entry being
     * added, and anything else is read as a bank statement.
     */
    fun openFile(f: java.io.File) {
        val ext = f.extension.lowercase()
        if (ext in setOf("jpg", "jpeg", "png", "webp", "heic")) {
            if (editing != null) droppedPhoto = f else tell("Open an entry first, then drop its photo on it")
            return
        }
        if (ext == "json" && isBackup(f)) backups.open(f) else importer.start(f)
    }

    private fun isBackup(f: java.io.File): Boolean = runCatching {
        val json = org.json.JSONObject(f.readText())
        com.leftovers.app.util.BackupCrypto.isEncrypted(json) || json.optString("app") in setOf("leftovers", "cash-tracker")
    }.getOrDefault(false)

    /** Takes [intent] if it's [what], so it's acted on once. */
    fun take(what: String): Boolean = (intent == what).also { if (it) intent = null }
    fun tell(text: String, undo: (() -> Unit)? = null) { notice = Notice(text, undo) }
    /** Opens the add panel, or the entry with [id] in it. */
    fun edit(id: Long? = null, type: com.leftovers.app.data.TxType = com.leftovers.app.data.TxType.EXPENSE, transfer: Boolean = false) {
        if (store.book.prefs.onboarded) editing = com.leftovers.desktop.ui.screens.EditRequest(id, type, transfer)
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("No app") }

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun App(state: AppState) {
    val view by state.store.view.collectAsState()
    val prefs = view.book.prefs
    LeftoversTheme(prefs.theme, prefs.glass) {
        androidx.compose.runtime.CompositionLocalProvider(LocalApp provides state, LocalMoney provides Money(view.book.currency)) {
            var dragging by remember { mutableStateOf(false) }
            val target = remember {
                object : androidx.compose.ui.draganddrop.DragAndDropTarget {
                    override fun onEntered(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { dragging = true }
                    override fun onExited(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { dragging = false }
                    override fun onEnded(event: androidx.compose.ui.draganddrop.DragAndDropEvent) { dragging = false }
                    override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                        dragging = false
                        val files = (event.dragData() as? androidx.compose.ui.draganddrop.DragData.FilesList)?.readFiles().orEmpty()
                        val f = files.firstOrNull()?.let { runCatching { java.io.File(java.net.URI(it)) }.getOrNull() } ?: return false
                        if (!state.store.book.prefs.onboarded) return false
                        state.openFile(f)
                        return true
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(T.c.ground).dragAndDropTarget(
                    shouldStartDragAndDrop = { it.dragData() is androidx.compose.ui.draganddrop.DragData.FilesList }, target = target,
                ),
            ) {
                if (T.c.glass) GlassField()
                if (!prefs.onboarded) WelcomeScreen() else Shell(state, view)
                if (prefs.onboarded) {
                    com.leftovers.desktop.ui.screens.ImportPanel(state.importer)
                    com.leftovers.desktop.ui.screens.BackupDialogs(state.backups, askHow = true) { json, replace ->
                        if (replace) { state.store.replaceWith(json); state.tell("Backup opened") }
                        else {
                            val n = state.store.mergeWith(json).add.transactions.size
                            state.tell(if (n == 0) "Nothing new in it" else "Added $n ${if (n == 1) "entry" else "entries"}") { state.store.undo() }
                        }
                    }
                }
                com.leftovers.desktop.ui.screens.EditorPanel(state.editing) { state.editing = null }
                if (prefs.onboarded) CommandPalette(state, view, state.palette) { state.palette = false }
                ShortcutsDialog(state.shortcuts) { state.shortcuts = false }
                DropHint(dragging && prefs.onboarded, state.editing != null)
                NoticeBar(state.notice) { state.notice = null }
            }
        }
    }
}

/** The soft colour field glass sits on: three blurred washes of the phone's aurora colours. */
@Composable
private fun GlassField() {
    val c = T.c
    Canvas(Modifier.fillMaxSize().blur(120.dp)) {
        fun wash(color: Color, x: Float, y: Float, r: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = if (c.dark) 0.55f else 0.45f), Color.Transparent), Offset(size.width * x, size.height * y), size.minDimension * r),
            radius = size.minDimension * r, center = Offset(size.width * x, size.height * y),
        )
        wash(c.fieldA, 0.18f, 0.12f, 0.75f)
        wash(c.fieldB, 0.85f, 0.35f, 0.7f)
        wash(c.fieldC, 0.45f, 1.0f, 0.65f)
    }
}

@Composable
private fun Shell(state: AppState, view: View) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(state.page) { state.page = it }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
                state.page,
                transitionSpec = {
                    (fadeIn(Motion.settle()) + slideInVertically(Motion.offset) { 18 }) togetherWith fadeOut(Motion.quick())
                },
                label = "page",
            ) { page ->
                when (page) {
                    Page.HOME -> com.leftovers.desktop.ui.screens.HomeScreen(view)
                    Page.ACTIVITY -> com.leftovers.desktop.ui.screens.ActivityScreen(view)
                    Page.INSIGHTS -> com.leftovers.desktop.ui.screens.InsightsScreen(view)
                    Page.BUDGETS -> com.leftovers.desktop.ui.screens.BudgetsScreen(view)
                    Page.SUBSCRIPTIONS -> com.leftovers.desktop.ui.screens.SubscriptionsScreen(view)
                    Page.GOALS -> com.leftovers.desktop.ui.screens.GoalsScreen(view)
                    Page.SETTINGS -> SettingsScreen(view)
                    else -> Placeholder(page)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(page: Page, onPick: (Page) -> Unit) {
    val c = T.c
    val tops = remember { mutableStateMapOf<Page, Float>() }
    val density = LocalDensity.current
    val pillY by animateDpAsState(with(density) { (tops[page] ?: 0f).toDp() }, Motion.moved(), label = "pill")
    Column(
        Modifier.width(232.dp).fillMaxHeight().background(c.sidebar).padding(horizontal = 12.dp, vertical = 18.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, bottom = 22.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Mark()
            Spacer(Modifier.width(10.dp))
            Text("Leftovers", style = T.type.heading.copy(fontSize = T.type.heading.fontSize * 1.08f), color = c.text)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The highlight behind the page that's open: it slides from one to the next.
            if (tops.containsKey(page)) {
                Box(
                    Modifier.offset(y = pillY).fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (c.dark) Color.White.copy(alpha = 0.07f) else if (c.glass) Color.White.copy(alpha = 0.7f) else Color.White),
                )
            }
            Column(Modifier.fillMaxSize()) {
                navGroups.forEach { (label, pages) ->
                    if (label != null) SectionLabel(label, Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp))
                    pages.forEach { p -> NavItem(p, p == page, onPick, Modifier.onGloballyPositioned { tops[p] = it.positionInParent().y }) }
                }
                Spacer(Modifier.weight(1f))
                NavItem(Page.SETTINGS, page == Page.SETTINGS, onPick, Modifier.onGloballyPositioned { tops[Page.SETTINGS] = it.positionInParent().y })
            }
        }
    }
}

@Composable
private fun NavItem(p: Page, selected: Boolean, onPick: (Page) -> Unit, modifier: Modifier) {
    val c = T.c
    HoverRow(onClick = { onPick(p) }, modifier = modifier.fillMaxWidth().height(38.dp), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
        Icon(p.icon, null, Modifier.size(18.dp), tint = if (selected) c.text else c.text2)
        Spacer(Modifier.width(12.dp))
        Text(p.title, style = if (selected) T.type.bodyStrong else T.type.body, color = if (selected) c.text else c.text2)
    }
}

/** The app's mark: what's left of a full circle. */
@Composable
fun Mark(size: androidx.compose.ui.unit.Dp = 26.dp) {
    val c = T.c
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.accent), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.56f)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = this.size.minDimension * 0.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(c.onAccent, startAngle = -90f, sweepAngle = 270f, useCenter = false, style = stroke)
        }
    }
}

private val shortcutList = listOf(
    "Ctrl+K" to "Go to anything, or do anything",
    "Ctrl+N" to "Add an entry",
    "Ctrl+T" to "Move money between accounts",
    "Ctrl+F" to "Search entries",
    "Ctrl+Z" to "Undo the last change",
    "Ctrl+Y" to "Redo",
    "Ctrl+1 to 9" to "Open a page, in the sidebar's order",
    "Ctrl+," to "Settings",
    "Ctrl+Enter" to "Save, in a panel",
    "Esc" to "Close a panel or dialog",
    "Ctrl+/" to "This list",
)

@Composable
private fun ShortcutsDialog(visible: Boolean, onDismiss: () -> Unit) {
    val c = T.c
    com.leftovers.desktop.ui.components.Dialog(visible, onDismiss, width = 480.dp) {
        Text("Keyboard shortcuts", style = T.type.title, color = c.text)
        Spacer(Modifier.height(16.dp))
        shortcutList.forEach { (keys, what) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(what, style = T.type.body, color = c.text2, modifier = Modifier.weight(1f))
                Text(
                    keys, style = T.type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.raised).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** While a file is dragged over the window: what dropping it will do. */
@Composable
private fun DropHint(visible: Boolean, editing: Boolean) {
    val c = T.c
    androidx.compose.animation.AnimatedVisibility(
        visible, enter = androidx.compose.animation.fadeIn(Motion.settle()), exit = androidx.compose.animation.fadeOut(Motion.quick()),
    ) {
        Box(Modifier.fillMaxSize().background(c.scrim).padding(28.dp)) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                    .border(2.dp, c.accent.copy(alpha = 0.9f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Lucide.Download, null, Modifier.size(40.dp), tint = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Text(if (editing) "Drop a photo of the bill" else "Drop a bank statement or a backup", style = T.type.title, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(if (editing) "It's added to this entry." else "CSV, Excel and PDF statements are checked before anything is added.", style = T.type.body, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
