package com.leftovers.app.ui.screens

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onSizeChanged
import com.leftovers.app.ui.components.LocalUndo
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.allTags
import com.leftovers.app.data.hashtags
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.icons.CategoryIcons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.DockClearance
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.MonthSwitcher
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Spent"), INCOME("Income") }

data class DayGroup(val date: LocalDate, val items: List<TransactionItem>)

/** Narrows the list to one account, one category and/or one #tag. A tag looks across all months. */
data class ActivityFilters(val accountId: Long? = null, val categoryId: Long? = null, val tag: String? = null) {
    val active get() = accountId != null || categoryId != null || tag != null
}

data class FilterOption(val id: Long, val name: String, val icon: String, val color: Long)

data class HistoryUiState(
    val month: YearMonth = YearMonth.now(),
    val filter: TypeFilter = TypeFilter.ALL,
    val query: String = "",
    val groups: List<DayGroup> = emptyList(),
    /** Every entry of the month regardless of filter, for the calendar. */
    val monthItems: List<TransactionItem> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val count: Int = 0,
    val filters: ActivityFilters = ActivityFilters(),
    val accounts: List<FilterOption> = emptyList(),
    val categories: List<FilterOption> = emptyList(),
    val tags: List<String> = emptyList(),
)

class HistoryViewModel(private val repository: TransactionRepository, accountRepository: AccountRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(TypeFilter.ALL)
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(ActivityFilters())

    val state: StateFlow<HistoryUiState> =
        combine(combine(repository.allTransactions, accountRepository.accounts, ::Pair), month, filter, query, filters) { (all, accountList), m, f, q, fl ->
            val monthItems = all.filter { YearMonth.from(it.date) == m }
            val scoped = if (q.isNotBlank()) {
                val needle = q.trim()
                // "430" or "1,250.50" also finds entries of exactly that amount.
                val amount = AmountInput.toMinor(needle.replace(",", ""))
                all.filter {
                    it.note.contains(needle, ignoreCase = true) ||
                        it.categoryName.contains(needle, ignoreCase = true) ||
                        (amount != null && it.amountMinor == amount)
                }
            } else if (fl.tag != null) {
                all.filter { fl.tag in hashtags(it.note) }
            } else {
                monthItems
            }
            val narrowed = scoped.filter {
                (fl.accountId == null || it.accountId == fl.accountId) && (fl.categoryId == null || it.categoryId == fl.categoryId)
            }
            val filtered = when (f) {
                TypeFilter.ALL -> narrowed
                TypeFilter.EXPENSE -> narrowed.filter { it.type == TxType.EXPENSE }
                TypeFilter.INCOME -> narrowed.filter { it.type == TxType.INCOME }
            }
            HistoryUiState(
                month = m,
                filter = f,
                query = q,
                groups = filtered.groupBy { it.epochDay }.map { (day, items) -> DayGroup(LocalDate.ofEpochDay(day), items) },
                monthItems = monthItems,
                income = filtered.totalOf(TxType.INCOME),
                expense = filtered.totalOf(TxType.EXPENSE),
                count = filtered.size,
                filters = fl,
                accounts = if (accountList.size > 1) accountList.map { FilterOption(it.id, it.name, it.icon, it.color) } else emptyList(),
                categories = all.distinctBy { it.categoryId }.sortedBy { it.categoryName }
                    .map { FilterOption(it.categoryId, it.categoryName, it.categoryEmoji, it.categoryColor) },
                tags = all.allTags(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setMonth(value: YearMonth) { month.value = value }
    fun setFilter(value: TypeFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setFilters(value: ActivityFilters) { filters.value = value }

    fun delete(item: TransactionItem) {
        viewModelScope.launch { repository.deleteTransaction(item.toTransaction()) }
    }

    /** Puts a deleted entry back. Runs outside this screen, so Undo works after switching tabs. */
    suspend fun restore(item: TransactionItem) = repository.saveTransaction(item.toTransaction())
}

private enum class ActivityView { LIST, CALENDAR }

@Composable
fun HistoryScreen(
    onOpenTransaction: (Long) -> Unit,
    onAddForDay: (LocalDate) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ActivityView.LIST) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var filterSheet by rememberSaveable { mutableStateOf(false) }

    fun deleteWithUndo(item: TransactionItem) {
        viewModel.delete(item)
        undo.show("Deleted ${item.note.ifBlank { item.categoryName }} · ${money.format(item.amountMinor)}") { viewModel.restore(item) }
    }

    GlassScreen(
        title = "Activity",
        onBack = null,
        actions = {
            RoundButton(
                if (mode == ActivityView.LIST) Lucide.Calendar else Lucide.ReceiptText,
                if (mode == ActivityView.LIST) "Calendar view" else "List view",
                {
                    mode = if (mode == ActivityView.LIST) ActivityView.CALENDAR else ActivityView.LIST
                    searchOpen = false
                    viewModel.setQuery("")
                },
            )
            if (mode == ActivityView.LIST) {
                RoundButton(Lucide.ListFilter, "Filter", { filterSheet = true })
                RoundButton(
                    if (searchOpen) Lucide.X else Lucide.Search,
                    if (searchOpen) "Close search" else "Search",
                    {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.setQuery("")
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = DockClearance),
        ) {
            item {
                if (searchOpen) {
                    GlassTextField(state.query, viewModel::setQuery, placeholder = "Search notes, #tags, categories or amounts")
                } else if (state.filters.tag == null || mode == ActivityView.CALENDAR) {
                    MonthSwitcher(state.month, {
                        viewModel.setMonth(it)
                        selectedDay = (if (it == YearMonth.now()) LocalDate.now() else it.atDay(1)).toEpochDay()
                    })
                }
                if (state.filters.active && mode == ActivityView.LIST) {
                    ActiveFilters(state, onChange = viewModel::setFilters, modifier = Modifier.padding(top = if (searchOpen || state.filters.tag == null) 10.dp else 0.dp))
                }
                Spacer(Modifier.height(14.dp))
            }

            if (mode == ActivityView.CALENDAR) {
                item {
                    MonthCalendar(
                        month = state.month,
                        items = state.monthItems,
                        selected = LocalDate.ofEpochDay(selectedDay),
                        onSelect = { selectedDay = it.toEpochDay() },
                        modifier = Modifier.appear(0),
                    )
                }
                item {
                    val day = LocalDate.ofEpochDay(selectedDay)
                    DayPanel(day, state.monthItems.filter { it.epochDay == selectedDay }, onAdd = { onAddForDay(day) }, onOpen = onOpenTransaction)
                }
                return@LazyColumn
            }

            item {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(30.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text(
                            when {
                                state.query.isNotBlank() -> "Spent in results"
                                state.filters.tag != null -> "Spent on #${state.filters.tag} · all time"
                                state.filters.active -> "Spent this month · filtered"
                                else -> "Spent this month"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        RollingText(money.format(state.expense), MaterialTheme.typography.displaySmall, c.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text(
                                "${state.count} ${if (state.count == 1) "entry" else "entries"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.income > 0) {
                                Text("+${money.format(state.income)} income", style = MaterialTheme.typography.bodySmall, color = c.positive)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SegmentedToggle(TypeFilter.entries, state.filter, { it.label }, viewModel::setFilter, Modifier.fillMaxWidth())
                if (state.groups.isNotEmpty()) {
                    Text(
                        "Tap or swipe right to edit · swipe left to delete",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            }

            if (state.groups.isEmpty()) {
                item {
                    if (state.query.isNotBlank() || state.filters.active) {
                        EmptyState(Lucide.Search, "No matches", if (state.filters.active) "Nothing matches these filters." else "Try a different word.")
                    } else {
                        EmptyState(Lucide.ReceiptText, "No entries this month", "Switch to the calendar view to add something you forgot.")
                    }
                }
            }

            state.groups.forEachIndexed { groupIndex, group ->
                item(key = "header-${group.date}") {
                    val spent = group.items.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 8.dp)
                            .appear(groupIndex + 1),
                    ) {
                        Text(group.date.friendlyLabel(), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
                        if (spent > 0) Text(money.format(spent), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    }
                }
                item(key = "group-${group.date}") {
                    Glass(Modifier.fillMaxWidth().appear(groupIndex + 1)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            group.items.forEachIndexed { i, item ->
                                if (i > 0) RowDivider()
                                key(item.id) {
                                    SwipeableRow(item, onDelete = { deleteWithUndo(item) }, onEdit = { onOpenTransaction(item.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterSheet) {
        FilterSheet(state, onChange = viewModel::setFilters, onDismiss = { filterSheet = false })
    }
}

/** The filters in use, each removable with a tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(state: HistoryUiState, onChange: (ActivityFilters) -> Unit, modifier: Modifier = Modifier) {
    val f = state.filters
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.firstOrNull { it.id == f.accountId }?.let { Chip(it.name, { onChange(f.copy(accountId = null)) }, icon = Lucide.X, selected = true) }
        state.categories.firstOrNull { it.id == f.categoryId }?.let { Chip(it.name, { onChange(f.copy(categoryId = null)) }, icon = Lucide.X, selected = true) }
        f.tag?.let { Chip("#$it", { onChange(f.copy(tag = null)) }, icon = Lucide.X, selected = true) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: HistoryUiState, onChange: (ActivityFilters) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val f = state.filters
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            @Composable
            fun Section(title: String, content: @Composable () -> Unit) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
            if (state.accounts.isNotEmpty()) {
                Section("Account") {
                    state.accounts.forEach { a ->
                        Chip(a.name, { onChange(f.copy(accountId = a.id.takeIf { it != f.accountId })) }, icon = CategoryIcons[a.icon], iconTint = Color(a.color), selected = a.id == f.accountId)
                    }
                }
            }
            Section("Category") {
                state.categories.forEach { cat ->
                    Chip(cat.name, { onChange(f.copy(categoryId = cat.id.takeIf { it != f.categoryId })) }, icon = CategoryIcons[cat.icon], iconTint = Color(cat.color), selected = cat.id == f.categoryId)
                }
            }
            Section("Tag") {
                if (state.tags.isEmpty()) {
                    Text(
                        "Add #tags to notes, like \"Dinner #goa\", to group entries across categories and months.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                } else {
                    state.tags.forEach { t ->
                        Chip("#$t", { onChange(f.copy(tag = t.takeIf { it != f.tag })) }, selected = t == f.tag)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Clear", { onChange(ActivityFilters()) }, Modifier.weight(1f))
                PrimaryButton("Done", onDismiss, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(item: TransactionItem, onDelete: () -> Unit, onEdit: () -> Unit) {
    val c = LocalAppColors.current
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf(false) }
    // A row left swiped open by the edit gesture is put back as soon as the list shows again.
    LaunchedEffect(Unit) {
        if (state.settledValue == SwipeToDismissBoxValue.StartToEnd) state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    // Close the gap smoothly first, then delete, so the list doesn't jump.
    LaunchedEffect(removing) {
        if (removing) {
            delay(ROW_COLLAPSE_MS.toLong())
            onDelete()
        }
    }
    AnimatedVisibility(
        visible = !removing,
        enter = EnterTransition.None,
        exit = shrinkVertically(tween(ROW_COLLAPSE_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(ROW_COLLAPSE_MS - 60)),
    ) {
    SwipeToDismissBox(
        state = state,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> removing = true
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    // Snap back while the editor covers the list, instead of animating under it.
                    scope.launch {
                        delay(400)
                        state.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            // Keep the last direction while the row springs back, so the icon doesn't swap mid-way.
            val direction = state.dismissDirection
            var lastDirection by remember { mutableStateOf(SwipeToDismissBoxValue.StartToEnd) }
            if (direction != SwipeToDismissBoxValue.Settled) lastDirection = direction
            val editing = lastDirection == SwipeToDismissBoxValue.StartToEnd
            val reveal = with(LocalDensity.current) { 96.dp.toPx() }
            val travel = with(LocalDensity.current) { 56.dp.toPx() }
            // Everything follows the row's position, so it slides in and out with the finger
            // instead of popping in or vanishing. At rest it is fully transparent (rows are glass).
            fun shown(): Float = (abs(runCatching { state.requireOffset() }.getOrDefault(0f)) / reveal).coerceIn(0f, 1f)
            // Letting go past about half the width deletes; warn from that point on.
            var rowWidth by remember { mutableFloatStateOf(0f) }
            val pastPoint by remember {
                derivedStateOf { rowWidth > 0f && -runCatching { state.requireOffset() }.getOrDefault(0f) >= rowWidth * 0.5f }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { rowWidth = it.width.toFloat() }
                    .graphicsLayer { alpha = shown() }
                    .background((if (editing) c.textSecondary else c.negative).copy(alpha = 0.18f))
                    .padding(horizontal = 24.dp),
                contentAlignment = if (editing) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        val t = shown()
                        // Slides out from the edge it's revealed at, growing as it comes.
                        translationX = (1f - t) * travel * (if (editing) -1f else 1f)
                        scaleX = 0.7f + 0.3f * t
                        scaleY = 0.7f + 0.3f * t
                    },
                ) {
                    // A small warning once letting go would delete; Undo covers mistakes.
                    AnimatedVisibility(
                        visible = !editing && pastPoint,
                        enter = fadeIn(tween(120)) + expandHorizontally(expandFrom = Alignment.End),
                        exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.End),
                    ) {
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.negative,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                    Icon(
                        if (editing) Lucide.Pencil else Lucide.Trash2,
                        contentDescription = if (editing) "Edit" else "Delete",
                        tint = if (editing) c.textPrimary else c.negative,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        TransactionRow(item, onClick = onEdit)
    }
    }

}

private const val ROW_COLLAPSE_MS = 240

/** Month grid showing how much was spent each day. Future days are disabled. */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    items: List<TransactionItem>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val today = LocalDate.now()
    val spentByDay = items.filter { it.type == TxType.EXPENSE }.groupBy { it.date.dayOfMonth }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    val incomeDays = items.filter { it.type == TxType.INCOME }.map { it.date.dayOfMonth }.toSet()
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<Int?> = List(leading) { null } + (1..month.lengthOfMonth()).toList()
    val shape = RoundedCornerShape(14.dp)

    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(0.82f)) {
                            if (day == null) return@Box
                            val date = month.atDay(day)
                            val future = date > today
                            val isSelected = date == selected
                            val spent = spentByDay[day] ?: 0L
                            val bg by animateColorAsState(
                                when {
                                    isSelected -> c.accent
                                    spent > 0 -> c.glassStrong
                                    else -> Color.Transparent
                                },
                                label = "calBg",
                            )
                            val fg = when {
                                isSelected -> c.onAccent
                                future -> c.textTertiary.copy(alpha = 0.5f)
                                else -> c.textPrimary
                            }
                            Column(
                                Modifier
                                    .matchParentSize()
                                    .pressable({ onSelect(date) }, pressedScale = 0.9f, enabled = !future)
                                    .clip(shape)
                                    .background(bg)
                                    .then(if (date == today && !isSelected) Modifier.border(1.dp, c.textSecondary, shape) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(day.toString(), style = MaterialTheme.typography.labelLarge, color = fg)
                                if (spent > 0) {
                                    Text(
                                        compact(spent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) c.onAccent.copy(alpha = 0.75f) else c.textSecondary,
                                        maxLines = 1,
                                    )
                                } else if (day in incomeDays) {
                                    Box(
                                        Modifier
                                            .padding(top = 4.dp)
                                            .size(5.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) c.onAccent else c.positive),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPanel(day: LocalDate, items: List<TransactionItem>, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val spent = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    AnimatedContent(targetState = day, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "dayPanel") { d ->
        Column(Modifier.padding(top = 18.dp)) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.friendlyLabel(), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        if (spent > 0) "${money.format(spent)} spent" else "Nothing spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
                Chip("Add for this day", onClick = onAdd, icon = Lucide.Plus, selected = true)
            }
            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth()) {
                if (items.isNotEmpty()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        items.forEachIndexed { i, item ->
                            if (i > 0) RowDivider()
                            TransactionRow(item, onClick = { onOpen(item.id) })
                        }
                    }
                } else {
                    Text(
                        "Forgot to log something? Add it here and it will be saved on this date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        }
    }
}

/** 1250 → "1.2k", 125000 → "125k" (amounts are in minor units). */
private fun compact(minor: Long): String {
    val v = minor / 100.0
    return when {
        v >= 10_000_000 -> "%.0fM".format(v / 1_000_000)
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        v >= 10_000 -> "%.0fk".format(v / 1000)
        v >= 1000 -> "%.1fk".format(v / 1000)
        else -> "%.0f".format(v)
    }
}
