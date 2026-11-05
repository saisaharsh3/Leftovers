package com.leftovers.app.ui.screens

import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import com.leftovers.app.ui.components.glassBorder
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.PlanningRepository
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
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Whether [item] is found by a search: every word in [query] is somewhere in its note, category or account
 * ([accountName]), in any order. A word that's a number, "430" or "1,250.50", also finds entries of that amount.
 */
internal fun searchMatches(item: TransactionItem, query: String, accountName: String?): Boolean =
    query.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.all { word ->
        val amount = AmountInput.toMinor(word.replace(",", ""))
        item.note.contains(word, ignoreCase = true) ||
            item.categoryName.contains(word, ignoreCase = true) ||
            accountName?.contains(word, ignoreCase = true) == true ||
            (amount != null && item.amountMinor == amount)
    }

enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Spent"), INCOME("Income") }

/** A day's entries; with [label], one list in another order (by amount) instead of a day. */
data class DayGroup(val date: LocalDate, val items: List<TransactionItem>, val label: String? = null)

/** Narrows the list to one account, one category and/or one #tag. A tag looks across all months. */
/** [allTime]: every entry instead of one month's (#29); it isn't a filter, so it doesn't make the list "filtered". */
data class ActivityFilters(
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val tag: String? = null,
    val allTime: Boolean = false,
    val sort: ActivitySort = ActivitySort.NEWEST,
) {
    /** Some entries are left out; a different order alone leaves everything in. */
    val narrows get() = accountId != null || categoryId != null || tag != null
    /** Anything to show as a removable chip. */
    val active get() = narrows || sort != ActivitySort.NEWEST
}

/** The order entries are listed in: by day, newest first, or by amount. */
enum class ActivitySort(val label: String) { NEWEST("Newest"), HIGHEST("Highest"), LOWEST("Lowest") }

/** How another screen asks Activity to open: which tab, and optionally one account. */
data class ActivityRequest(val filter: TypeFilter, val accountId: Long? = null)

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
    /** Splits as one bill with their parts under them; off, every entry on its own. */
    val oneBill: Boolean = true,
    /** What people owe for bills split with them, by the entry (your share) each came from. */
    val shares: Map<Long, List<com.leftovers.app.data.Debt>> = emptyMap(),
)

class HistoryViewModel(
    private val repository: TransactionRepository,
    accountRepository: AccountRepository,
    planning: PlanningRepository,
    settings: com.leftovers.app.data.SettingsRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(TypeFilter.ALL)
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(ActivityFilters())

    init {
        // Activity shows the account Home is showing, however it's opened; all accounts on Home shows them all.
        // It can still be cleared here, until Home switches account again.
        viewModelScope.launch {
            settings.settings.map { it.homeAccountId }.distinctUntilChanged().collect { id ->
                filters.value = filters.value.copy(accountId = id)
            }
        }
    }

    val state: StateFlow<HistoryUiState> =
        combine(
            combine(repository.allTransactions, accountRepository.accounts, settings.settings.map { it.splitsAsOneBill }.distinctUntilChanged(), planning.debts) { a, b, c, d -> Books(a, b, c, d) },
            month, filter, query, filters,
        ) { (all, accountList, oneBill, debts), m, f, q, fl ->
            val monthItems = all.filter { YearMonth.from(it.date) == m }
            val scoped = if (q.isNotBlank()) {
                val accountNames = accountList.associate { it.id to it.name }
                all.filter { searchMatches(it, q, accountNames[it.accountId]) && (fl.tag == null || fl.tag in hashtags(it.note)) }
            } else if (fl.tag != null) {
                all.filter { fl.tag in hashtags(it.note) }
            } else if (fl.allTime) {
                all
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
                groups = if (fl.sort == ActivitySort.NEWEST) {
                    filtered.groupBy { it.epochDay }.map { (day, items) -> DayGroup(LocalDate.ofEpochDay(day), items) }
                } else {
                    // By amount: one list. A split bill counts as its whole amount and keeps its parts under it.
                    val ids = filtered.map { it.id }.toSet()
                    // A bill split with people counts as what you paid in all.
                    val owedFor = debts.filter { it.splitOf != null && !it.repayment }.groupBy { it.splitOf!! }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
                    val bills = if (!oneBill) filtered.map { listOf(it) }
                    else filtered.filter { it.splitOf == null || it.splitOf !in ids }
                        .map { main -> listOf(main) + filtered.filter { it.splitOf == main.id } }
                    fun whole(b: List<TransactionItem>) = b.sumOf { it.amountMinor } + if (oneBill) owedFor[b.first().id] ?: 0L else 0L
                    val sorted = if (fl.sort == ActivitySort.HIGHEST) bills.sortedByDescending(::whole) else bills.sortedBy(::whole)
                    if (sorted.isEmpty()) emptyList()
                    else listOf(DayGroup(sorted.first().first().date, sorted.flatten(), if (fl.sort == ActivitySort.HIGHEST) "Highest first" else "Lowest first"))
                },
                monthItems = monthItems,
                income = filtered.totalOf(TxType.INCOME),
                expense = filtered.totalOf(TxType.EXPENSE),
                count = filtered.size,
                filters = fl,
                accounts = if (accountList.size > 1) accountList.map { FilterOption(it.id, it.name, it.icon, it.color) } else emptyList(),
                categories = all.distinctBy { it.categoryId }.sortedBy { it.categoryName }
                    .map { FilterOption(it.categoryId, it.categoryName, it.categoryEmoji, it.categoryColor) },
                tags = all.allTags(),
                oneBill = oneBill,
                shares = debts.filter { it.splitOf != null && !it.repayment }.groupBy { it.splitOf!! },
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

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
    onOpenSubscriptions: (income: Boolean) -> Unit = {},
    /** Opens on this month with this filter (e.g. from Home's Spent or Income); null when not asked. */
    requestedFilter: ActivityRequest? = null,
    onRequestHandled: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // What's typed lives here, so every key lands in the field at once; the list follows it.
    var typed by rememberSaveable { mutableStateOf("") }
    fun search(text: String) {
        typed = text
        viewModel.setQuery(text)
    }
    var mode by rememberSaveable { mutableStateOf(ActivityView.LIST) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    // Tapping an entry opens a card about it; Edit there opens the full editor.
    val peek = rememberTransactionPeek()
    TransactionPeekCard(peek, onEdit = onOpenTransaction)
    LaunchedEffect(requestedFilter) {
        requestedFilter?.let {
            mode = ActivityView.LIST
            searchOpen = false
            search("")
            viewModel.setFilters(ActivityFilters(accountId = it.accountId))
            viewModel.setMonth(YearMonth.now())
            viewModel.setFilter(it.filter)
            onRequestHandled()
        }
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
                    search("")
                },
            )
            if (mode == ActivityView.LIST) {
                RoundButton(Lucide.ListFilter, "Filter", { filterSheet = true })
                RoundButton(
                    if (searchOpen) Lucide.X else Lucide.Search,
                    if (searchOpen) "Close search" else "Search",
                    {
                        searchOpen = !searchOpen
                        if (!searchOpen) search("")
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = DockClearance),
        ) {
            // Everything in the list moves together when rows are removed or come back, so nothing overlaps.
            item(key = "top") {
                Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = listMotion)) {
                if (searchOpen) {
                    GlassTextField(typed, ::search, placeholder = "Search notes, #tags, categories or amounts")
                } else if (state.filters.tag == null || mode == ActivityView.CALENDAR) {
                    // One month at a time, or everything (on the list; the calendar is always one month).
                    if (mode == ActivityView.LIST) {
                        SegmentedToggle(
                            listOf(false, true),
                            state.filters.allTime,
                            { if (it) "All time" else "By month" },
                            { viewModel.setFilters(state.filters.copy(allTime = it)) },
                            Modifier.fillMaxWidth().padding(bottom = if (state.filters.allTime) 0.dp else 10.dp),
                        )
                    }
                    if (!state.filters.allTime || mode == ActivityView.CALENDAR) {
                        MonthSwitcher(state.month, {
                            viewModel.setMonth(it)
                            selectedDay = (if (it == YearMonth.now()) LocalDate.now() else it.atDay(1)).toEpochDay()
                        })
                    }
                }
                if (state.filters.active && mode == ActivityView.LIST) {
                    ActiveFilters(state, onChange = viewModel::setFilters, modifier = Modifier.padding(top = if (searchOpen || state.filters.tag == null) 10.dp else 0.dp))
                }
                Spacer(Modifier.height(14.dp))
                }
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
                    DayPanel(day, state.monthItems.filter { it.epochDay == selectedDay }, onAdd = { onAddForDay(day) }, peek = peek)
                }
                return@LazyColumn
            }

            item(key = "summary") {
                Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = listMotion)) {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(30.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        // On the Income tab the card leads with income instead of spending.
                        val incomeView = state.filter == TypeFilter.INCOME
                        val what = if (incomeView) "Income" else "Spent"
                        Text(
                            when {
                                state.query.isNotBlank() -> "$what in results"
                                state.filters.tag != null -> "$what on #${state.filters.tag} · all time"
                                state.filters.allTime -> "$what · all time" + if (state.filters.narrows) " · filtered" else ""
                                state.filters.narrows -> "$what this month · filtered"
                                else -> "$what this month"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        RollingText(
                            money.format(if (incomeView) state.income else state.expense),
                            MaterialTheme.typography.displaySmall,
                            if (incomeView) c.positive else c.textPrimary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text(
                                "${state.count} ${if (state.count == 1) "entry" else "entries"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.income > 0 && state.filter != TypeFilter.INCOME) {
                                Text("+${money.format(state.income)} income", style = MaterialTheme.typography.bodySmall, color = c.positive)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SegmentedToggle(TypeFilter.entries, state.filter, { it.label }, viewModel::setFilter, Modifier.fillMaxWidth())
                }
            }

            if (state.groups.isEmpty()) {
                item {
                    if (state.query.isNotBlank() || state.filters.narrows) {
                        EmptyState(Lucide.Search, "No matches", if (state.filters.narrows) "Nothing matches these filters." else "Try a different word.")
                    } else {
                        if (state.filters.allTime) EmptyState(Lucide.ReceiptText, "No entries yet", "Tap the + button below to add your first one.")
                        else EmptyState(Lucide.ReceiptText, "No entries this month", "Switch to the calendar view to add something you forgot.")
                    }
                }
            }

            // Each entry is its own list item (drawn to look like one card per day), so deleting one, or
            // bringing it back with Undo, animates: it fades and the rest of the list glides into place.
            state.groups.forEachIndexed { groupIndex, group ->
                item(key = "header-${group.date}") {
                    val spent = group.items.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
                    Row(
                        Modifier
                            .animateItem(fadeInSpec = tween(220), placementSpec = listMotion, fadeOutSpec = tween(160))
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 8.dp)
                            .appear(groupIndex + 1),
                    ) {
                        Text(group.label ?: group.date.friendlyLabel(), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
                        if (spent > 0) Text(money.format(spent), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    }
                }
                // Each split bill on a card of its own: the entry, its parts, and who owes for it (#36).
                billCards(group.items, state.shares, state.oneBill).forEachIndexed { cardIndex, card ->
                    // A split bill is its row and where its money went; its parts and shares aren't rows of their own.
                    val bill = card.drop(1).any { it.under() }
                    card.forEachIndexed { i, line ->
                        if (line !is BillLine.Entry || line.under()) return@forEachIndexed
                        item(key = "tx-${line.item.id}") {
                            CardSlice(
                                first = i == 0,
                                last = card.drop(i + 1).all { it.under() },
                                modifier = Modifier
                                    .animateItem(fadeInSpec = tween(220), placementSpec = listMotion, fadeOutSpec = tween(160))
                                    .padding(top = if (i == 0 && cardIndex > 0) 10.dp else 0.dp)
                                    .appear(groupIndex + 1),
                            ) {
                                if (i > 0) RowDivider(inset = 74.dp)
                                // No swipe actions: they fired by accident. A tap opens the entry, where it can be edited or deleted.
                                TransactionRow(
                                    line.item,
                                    onClick = { peek.show(line.item) },
                                    modifier = Modifier.peekSource(peek, line.item.id),
                                    subtitle = billSubtitle(if (group.label != null) line.item.date.friendlyLabel() else line.item.timeLabel(), line),
                                    wholeMinor = line.whole(),
                                )
                                if (bill && i == 0) {
                                    BillBreakdown(
                                        line, onPart = { peek.show(it) }, onShare = { peek.share = it },
                                        partModifier = { Modifier.peekSource(peek, it.id) },
                                    )
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
        if (f.sort != ActivitySort.NEWEST) Chip(f.sort.label, { onChange(f.copy(sort = ActivitySort.NEWEST)) }, icon = Lucide.X, selected = true)
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
            Section("Sort") {
                ActivitySort.entries.forEach { o ->
                    Chip(
                        o.label, { onChange(f.copy(sort = o)) },
                        icon = when (o) { ActivitySort.NEWEST -> Lucide.Calendar; ActivitySort.HIGHEST -> Lucide.TrendingUp; ActivitySort.LOWEST -> Lucide.TrendingDown },
                        selected = o == f.sort,
                    )
                }
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

/** How rows glide into place when one is removed or comes back: smooth, no bounce. */
private val listMotion = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * One row of a day's card. Rows are separate list items so they can animate on their own, but together they
 * draw as one rounded glass card: the first rounds the top, the last the bottom, and the outline only runs
 * along the outside.
 */
@Composable
private fun CardSlice(first: Boolean, last: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    val r = 28.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.glass)
            .drawWithContent {
                drawContent()
                val w = 1.dp.toPx()
                val outline = shape.createOutline(size, layoutDirection, this)
                // Leave out the edges shared with the rows above and below.
                clipRect(top = if (first) 0f else w, bottom = if (last) size.height else size.height - w) {
                    drawOutline(outline, if (first) glassBorder(c) else SolidColor(c.borderBottom), style = Stroke(w * 2))
                }
            }
            .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp),
        content = content,
    )
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
private fun DayPanel(day: LocalDate, items: List<TransactionItem>, onAdd: () -> Unit, peek: TransactionPeek) {
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
                            TransactionRow(item, onClick = { peek.show(item) }, modifier = Modifier.peekSource(peek, item.id))
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

/** [items] with each split bill's parts right after the entry they were split from. */
internal fun splitOrder(items: List<TransactionItem>): List<TransactionItem> {
    val ids = items.map { it.id }.toSet()
    val partsOf = items.filter { it.splitOf != null && it.splitOf in ids }.groupBy { it.splitOf }
    return items.filter { it.splitOf == null || it.splitOf !in ids }.flatMap { listOf(it) + partsOf[it.id].orEmpty() }
}

/** What Activity is made from, before its filters. */
private data class Books(
    val all: List<TransactionItem>,
    val accounts: List<com.leftovers.app.data.AccountWithBalance>,
    val oneBill: Boolean,
    val debts: List<com.leftovers.app.data.Debt>,
)
