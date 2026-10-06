package com.leftovers.app.ui.screens

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
)

class HistoryViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(TypeFilter.ALL)
    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> =
        combine(repository.allTransactions, month, filter, query) { all, m, f, q ->
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
            } else {
                monthItems
            }
            val filtered = when (f) {
                TypeFilter.ALL -> scoped
                TypeFilter.EXPENSE -> scoped.filter { it.type == TxType.EXPENSE }
                TypeFilter.INCOME -> scoped.filter { it.type == TxType.INCOME }
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
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setMonth(value: YearMonth) { month.value = value }
    fun setFilter(value: TypeFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }

    fun delete(item: TransactionItem) {
        viewModelScope.launch { repository.deleteTransaction(item.toTransaction()) }
    }

    fun restore(item: TransactionItem) {
        viewModelScope.launch { repository.saveTransaction(item.toTransaction()) }
    }
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ActivityView.LIST) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }

    fun deleteWithUndo(item: TransactionItem) {
        viewModel.delete(item)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Deleted ${money.format(item.amountMinor)}", actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(item)
        }
    }

    GlassScreen(
        title = "Activity",
        onBack = null,
        snackbar = snackbar,
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
                    GlassTextField(state.query, viewModel::setQuery, placeholder = "Search notes, categories or amounts")
                } else {
                    MonthSwitcher(state.month, {
                        viewModel.setMonth(it)
                        selectedDay = (if (it == YearMonth.now()) LocalDate.now() else it.atDay(1)).toEpochDay()
                    })
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
                            if (state.query.isNotBlank()) "Spent in results" else "Spent this month",
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
                    if (state.query.isNotBlank()) {
                        EmptyState(Lucide.Search, "No matches", "Try a different word.")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(item: TransactionItem, onDelete: () -> Unit, onEdit: () -> Unit) {
    val c = LocalAppColors.current
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.StartToEnd -> {
                onEdit()
                state.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            // Rows are see-through glass, so only draw the action layer while a swipe is in progress.
            if (state.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val editing = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (state.progress * 3f).coerceIn(0f, 1f) }
                    .background((if (editing) c.textSecondary else c.negative).copy(alpha = 0.18f))
                    .padding(horizontal = 24.dp),
                contentAlignment = if (editing) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (editing) Lucide.Pencil else Lucide.Trash2,
                    contentDescription = if (editing) "Edit" else "Delete",
                    tint = if (editing) c.textPrimary else c.negative,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        TransactionRow(item, onClick = onEdit)
    }
}

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
