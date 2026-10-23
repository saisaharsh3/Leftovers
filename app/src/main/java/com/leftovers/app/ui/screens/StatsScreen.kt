package com.leftovers.app.ui.screens

import com.leftovers.app.ui.components.toggleHaptic
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.data.Trend
import com.leftovers.app.data.spendingTrends
import com.leftovers.app.util.Money
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.util.friendlyLabel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.CategoryTotal
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.data.categoryTotals
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.ChartSlice
import com.leftovers.app.ui.components.DailyBars
import com.leftovers.app.ui.components.DockClearance
import com.leftovers.app.ui.components.DonutChart
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.MonthSwitcher
import com.leftovers.app.ui.components.ProgressLine
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.SpendingCalendar
import com.leftovers.app.ui.components.StatTile
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.shortLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class StatsUiState(
    val month: YearMonth = YearMonth.now(),
    val type: TxType = TxType.EXPENSE,
    val total: Long = 0,
    val previousTotal: Long = 0,
    val budget: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val daily: List<Long> = emptyList(),
    val count: Int = 0,
    val max: TransactionItem? = null,
    /** This month's entries of the chosen type, newest first. */
    val items: List<TransactionItem> = emptyList(),
    val trends: List<Trend> = emptyList(),
)

class StatsViewModel(
    private val repository: TransactionRepository,
    private val settings: SettingsRepository,
    private val planning: com.leftovers.app.data.PlanningRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val type = MutableStateFlow(TxType.EXPENSE)

    val state: StateFlow<StatsUiState> =
        combine(repository.allTransactions, settings.settings, month, type) { all, s, m, t ->
            val current = all.filter { YearMonth.from(it.date) == m }
            val ofType = current.filter { it.type == t }
            val daily = LongArray(m.lengthOfMonth())
            ofType.forEach { daily[it.date.dayOfMonth - 1] += it.amountMinor }
            StatsUiState(
                month = m,
                type = t,
                total = current.totalOf(t),
                previousTotal = all.filter { YearMonth.from(it.date) == m.minusMonths(1) }.totalOf(t),
                budget = if (s.plan.isSet) s.plan.budgetFor(m, all) else 0L,
                categories = current.categoryTotals(t),
                daily = daily.toList(),
                count = ofType.size,
                max = ofType.maxByOrNull { it.amountMinor },
                items = ofType,
                trends = spendingTrends(all, m, t, Money(s.currencyCode)::formatWhole),
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setMonth(value: YearMonth) { month.value = value }

    /** Makes the PDF statement for the month shown, or its whole year, and hands back the file to share. */
    fun makeStatement(context: android.content.Context, chosen: YearMonth, yearly: Boolean, notes: Boolean, entries: Boolean, onReady: (android.net.Uri, String) -> Unit) {
        viewModelScope.launch {
            val all = repository.allTransactions.first()
            val s = settings.settings.first()
            val m = chosen
            val statement = kotlinx.coroutines.withContext(Dispatchers.Default) {
                com.leftovers.app.util.StatementPdf.build(if (yearly) null else m, m.year, all, s.plan, planning.recurring.first())
            }
            val uri = com.leftovers.app.util.StatementPdf.write(context, statement, Money(s.currencyCode), notes, entries)
            onReady(uri, statement.title)
        }
    }
    fun setType(value: TxType) { type.value = value }
}

@Composable
fun StatsScreen(
    onOpenRecap: (YearMonth) -> Unit,
    onOpenYear: (Int) -> Unit = {},
    onOpenTransaction: (Long) -> Unit,
    /** Opens the assistant with this question; the row only shows when an AI is connected. */
    onAskAi: (String) -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val isExpense = state.type == TxType.EXPENSE
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val context = androidx.compose.ui.platform.LocalContext.current
    val aiConfig by (context.applicationContext as com.leftovers.app.LeftoversApp).container.assistant.config.collectAsStateWithLifecycle()
    val aiConnected = aiConfig != null
    // Tapping an entry opens a card about it; Edit there opens the full editor.
    val peek = rememberTransactionPeek()
    TransactionPeekCard(peek, onEdit = onOpenTransaction)
    var showStatement by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showStatement) {
        StatementSheet(
            start = state.month,
            onDismiss = { showStatement = false },
            onMake = { chosen, yearly, notes, entries ->
                viewModel.makeStatement(context, chosen, yearly, notes, entries) { uri, title ->
                    showStatement = false
                    context.startActivity(com.leftovers.app.util.StatementPdf.shareIntent(uri, title))
                }
            },
        )
    }

    GlassScreen(
        title = "Insights",
        onBack = null,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(Lucide.Download, "Statement as a PDF", { showStatement = true })
                RoundButton(Lucide.Sparkles, "Monthly recap", { onOpenRecap(state.month) })
            }
        },
    ) { padding ->
        // Sideways swipes switch tabs here like everywhere else; the arrows change month.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSwitcher(state.month, viewModel::setMonth) }
            item {
                SegmentedToggle(
                    TxType.entries, state.type,
                    { if (it == TxType.EXPENSE) "Spending" else "Income" },
                    viewModel::setType, Modifier.fillMaxWidth(),
                )
            }

            item {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text(
                            if (isExpense) "Spent in $monthName" else "Earned in $monthName",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        RollingText(money.format(state.total), MaterialTheme.typography.displaySmall, c.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ComparisonChip(state)
                            if (isExpense && state.budget > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(state.total * 100 / state.budget)}% of budget",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        val today = LocalDate.now()
                        DailyBars(
                            values = state.daily,
                            highlight = if (state.month == YearMonth.from(today)) today.dayOfMonth - 1 else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            tooltip = { i -> "${state.month.atDay(i + 1).shortLabel()} · ${money.format(state.daily.getOrElse(i) { 0L })}" },
                        )
                        Text(
                            "Touch the bars to see each day",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            // A read of the month from the AI, when one is connected: what changed and what to do about it.
            if (aiConnected && state.count > 0) {
                item {
                    val previous = state.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    val question = if (isExpense) {
                        "How did my spending go in $monthName ${state.month.year}? Compare it with $previous and my budget, " +
                            "say what changed most, and suggest one or two things I could do differently."
                    } else {
                        "How did my income go in $monthName ${state.month.year} compared with $previous? Anything worth noticing?"
                    }
                    Glass(Modifier.fillMaxWidth().padding(top = 12.dp).appear(1), onClick = { onAskAi(question) }) {
                        com.leftovers.app.ui.components.ListRow(
                            "Ask AI about $monthName",
                            subtitle = if (isExpense) "What changed, and what to do about it" else "How your income compares",
                            leading = { com.leftovers.app.ui.components.IconTile(Lucide.MessageCircle, c.accent, size = 38.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary) },
                        )
                    }
                }
            }

            if (state.trends.isNotEmpty()) {
                item {
                    Glass(Modifier.fillMaxWidth().padding(top = 12.dp).appear(1)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.trends.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Lucide.TrendingUp,
                                        contentDescription = null,
                                        tint = when (t.good) {
                                            true -> c.positive
                                            false -> c.warning
                                            null -> c.accent
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(t.text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            if (state.total == 0L) {
                item {
                    EmptyState(
                        Lucide.ChartPie,
                        if (isExpense) "No spending in $monthName" else "No income in $monthName",
                        "Your charts fill in as you add entries.",
                    )
                }
                return@LazyColumn
            }

            item {
                Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val today = LocalDate.now()
                    val days = if (state.month == YearMonth.from(today)) today.dayOfMonth else state.month.lengthOfMonth()
                    StatTile("Daily average", money.formatWhole(state.total / days), Modifier.weight(1f))
                    StatTile(
                        if (isExpense) "Biggest spend" else "Biggest income",
                        money.format(state.max?.amountMinor ?: 0),
                        Modifier.weight(1f),
                        footnote = state.max?.let { "${it.categoryName} · ${it.date.shortLabel()}" },
                    )
                }
            }

            item { SectionHeader("By category", Modifier.appear(2)) }
            item {
                Glass(Modifier.fillMaxWidth().appear(2)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                slices = state.categories.map { ChartSlice(it.totalMinor.toFloat(), Color(it.color)) },
                                modifier = Modifier.size(132.dp),
                            ) {
                                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${state.count}", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                                    Text("entries", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                                }
                            }
                            Spacer(Modifier.width(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.categories.take(4).forEach { cat ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(cat.color)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(cat.name, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        var openCategory by rememberSaveable(state.month, state.type) { mutableStateOf<Long?>(null) }
                        state.categories.forEach { cat ->
                            val open = openCategory == cat.categoryId
                            CategoryShare(cat, state.total, open) { openCategory = if (open) null else cat.categoryId }
                            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(Modifier.padding(start = 30.dp, bottom = 6.dp)) {
                                    state.items.filter { it.categoryId == cat.categoryId }.forEach { item ->
                                        TransactionRow(item, onClick = { peek.show(item) }, subtitle = item.date.friendlyLabel(), modifier = Modifier.peekSource(peek, item.id))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Calendar", Modifier.appear(3)) }
            item {
                Glass(Modifier.fillMaxWidth().appear(3)) {
                    Column(Modifier.padding(16.dp)) {
                        var selectedDay by rememberSaveable(state.month) { mutableStateOf<Int?>(null) }
                        SpendingCalendar(
                            state.month,
                            state.daily,
                            selected = selectedDay,
                            onSelect = { selectedDay = if (selectedDay == it) null else it },
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Legend(c.positive, "Lighter day")
                            Legend(c.warning, "Typical")
                            Legend(c.negative, "Heavier")
                        }
                        AnimatedContent(selectedDay, label = "dayDetail") { day ->
                            if (day == null) {
                                Text(
                                    "Tap a day to see what you ${if (state.type == TxType.EXPENSE) "spent" else "received"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textTertiary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                val date = state.month.atDay(day)
                                val entries = state.items.filter { it.date == date }
                                Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                                    RowDivider()
                                    Row(Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(date.friendlyLabel(), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                                        Text(
                                            money.format(entries.sumOf { it.amountMinor }),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (state.type == TxType.INCOME) c.positive else c.textPrimary,
                                        )
                                    }
                                    if (entries.isEmpty()) {
                                        Text(
                                            if (state.type == TxType.EXPENSE) "No spending on this day" else "No income on this day",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textTertiary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                        )
                                    } else {
                                        entries.forEach { item ->
                                            TransactionRow(item, onClick = { peek.show(item) }, modifier = Modifier.peekSource(peek, item.id))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // The whole year so far, as a story like the monthly recap.
            item {
                val year = state.month.year
                Glass(Modifier.fillMaxWidth().appear(4), onClick = { onOpenYear(year) }) {
                    com.leftovers.app.ui.components.ListRow(
                        if (year == java.time.LocalDate.now().year) "Your $year so far" else "$year in review",
                        subtitle = "The year as a story, like the monthly recap",
                        leading = { com.leftovers.app.ui.components.IconTile(Lucide.Sparkles, c.accent, size = 38.dp) },
                        trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

@Composable
private fun CategoryShare(cat: CategoryTotal, total: Long, open: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val share = if (total > 0) cat.totalMinor.toFloat() / total else 0f
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Row(Modifier.fillMaxWidth().pressable(onClick, pressedScale = 0.98f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(cat.emoji, cat.color, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(cat.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                Text(money.format(cat.totalMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(share, Modifier.weight(1f), color = Color(cat.color), height = 4.dp)
                Spacer(Modifier.width(10.dp))
                Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Lucide.ChevronRight, contentDescription = if (open) "Hide entries" else "Show entries", tint = c.textTertiary, modifier = Modifier.size(16.dp).rotate(turn))
    }
}

@Composable
private fun ComparisonChip(state: StatsUiState) {
    val c = LocalAppColors.current
    if (state.previousTotal <= 0) {
        Text("No data last month", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        return
    }
    val change = (state.total - state.previousTotal).toFloat() / state.previousTotal * 100
    val pct = abs(change).roundToInt()
    val good = if (state.type == TxType.EXPENSE) change <= 0 else change >= 0
    val tint = if (good) c.positive else c.negative
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (change <= 0) Lucide.TrendingDown else Lucide.TrendingUp, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("$pct% vs last month", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Choices for the PDF statement: the month shown or its whole year, and what goes in it. */
@Composable
private fun StatementSheet(start: YearMonth, onDismiss: () -> Unit, onMake: (month: YearMonth, yearly: Boolean, notes: Boolean, entries: Boolean) -> Unit) {
    val c = LocalAppColors.current
    var yearly by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Starts on the month Insights is showing; the arrows pick any other month, or year.
    var chosen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(start.toString()) }
    val month = YearMonth.parse(chosen)
    val now = YearMonth.now()
    val canGoForward = if (yearly) month.year < now.year else month < now
    var notes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var entries by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var making by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Statement as a PDF", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            com.leftovers.app.ui.components.SegmentedToggle(
                options = listOf(false, true),
                selected = yearly,
                label = { if (it) "Yearly" else "Monthly" },
                onSelect = { yearly = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Lucide.ChevronLeft, if (yearly) "Previous year" else "Previous month", {
                    chosen = (if (yearly) month.minusYears(1) else month.minusMonths(1)).toString()
                })
                Text(
                    if (yearly) "${month.year}" else "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                RoundButton(
                    Lucide.ChevronRight,
                    if (yearly) "Next year" else "Next month",
                    { chosen = (if (yearly) month.plusYears(1) else month.plusMonths(1)).toString() },
                    enabled = canGoForward,
                    tint = if (canGoForward) c.textPrimary else c.textTertiary,
                )
            }
            Text(
                "Income and spending, categories, biggest expenses and subscriptions" +
                    (if (yearly) ", with each month's totals" else "") + ". Made on your phone; nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    StatementSwitch("Include notes", "Left out by default, as they can be personal", notes) { notes = it }
                    com.leftovers.app.ui.components.RowDivider()
                    StatementSwitch("List every entry", "Adds pages after the summary", entries) { entries = it }
                }
            }
            com.leftovers.app.ui.components.PrimaryButton(
                if (making) "Making it…" else "Share PDF",
                {
                    making = true
                    // A future year can't be picked, but its later months could be from going back and switching to yearly.
                    onMake(if (month > now) now else month, yearly, notes, entries)
                },
                Modifier.fillMaxWidth(),
                enabled = !making,
                icon = Lucide.Share2,
            )
        }
    }
}

@Composable
private fun StatementSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    val flip = toggleHaptic(onChange)
    com.leftovers.app.ui.components.ListRow(
        title,
        subtitle = subtitle,
        trailing = {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = flip,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
            )
        },
        modifier = Modifier.pressable({ flip(!checked) }, pressedScale = 0.99f),
    )
}
