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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.util.friendlyLabel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.unit.sp
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
import com.leftovers.app.ui.components.DockClearance
import com.leftovers.app.ui.components.DonutChart
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
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
import kotlinx.coroutines.flow.update
import com.leftovers.app.data.BalancePoint
import com.leftovers.app.data.InsightsPeriod
import com.leftovers.app.data.MonthInOut
import com.leftovers.app.data.PeriodKind
import com.leftovers.app.data.balanceHistory
import com.leftovers.app.data.monthlyInOut
import com.leftovers.app.data.openBalances
import com.leftovers.app.ui.components.PairBars
import com.leftovers.app.ui.components.TrendLine
import com.leftovers.app.ui.components.ColumnChart
import androidx.compose.foundation.border
import com.leftovers.app.util.label
import androidx.compose.animation.togetherWith
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class StatsUiState(
    val loaded: Boolean = false,
    val period: InsightsPeriod = InsightsPeriod(),
    /** The account shown, or null for all of them. */
    val accountId: Long? = null,
    val accounts: List<com.leftovers.app.data.AccountWithBalance> = emptyList(),
    /** The day of the oldest entry, where "All time" starts. */
    val oldest: LocalDate? = null,
    val type: TxType = TxType.EXPENSE,
    val total: Long = 0,
    /** The same length of time before; null when there's nothing to compare with (all time, or no entries then). */
    val previousTotal: Long? = null,
    val budget: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    /** For a month, its days (for the calendar); empty for other periods. */
    val daily: List<Long> = emptyList(),
    /** The bars under the total: one a day, or one a month for longer periods ([barsByMonth]). */
    val bars: List<Long> = emptyList(),
    val barsByMonth: Boolean = false,
    val count: Int = 0,
    val days: Long = 1,
    val max: TransactionItem? = null,
    /** The period's entries of the chosen type, newest first. */
    val items: List<TransactionItem> = emptyList(),
    val trends: List<Trend> = emptyList(),
    val balance: List<BalancePoint> = emptyList(),
    val inOut: List<MonthInOut> = emptyList(),
    /** The financial health score for the period (#73); null with a category picked, or nothing to judge. */
    val health: com.leftovers.app.data.FinancialHealth.Health? = null,
    /** The category shown, or null for all of them; the ones with entries, most used first, to pick from. */
    val categoryId: Long? = null,
    val categoryOptions: List<CategoryOption> = emptyList(),
) {
    val month: YearMonth get() = period.anchor
    val isMonth: Boolean get() = period.kind == PeriodKind.MONTH
    val start: LocalDate get() = period.start(oldest)
    val accountName: String? get() = accountId?.let { id -> accounts.find { it.id == id }?.name }
    val categoryName: String? get() = categoryId?.let { id -> categoryOptions.find { it.id == id }?.name }
}

/** A category that has entries, for the Show sheet. */
data class CategoryOption(val id: Long, val name: String, val emoji: String, val color: Long, val type: TxType, val uses: Int)

/** What Insights is showing: the period, the account, and spending or income. */
private data class StatsFilter(
    val period: InsightsPeriod = InsightsPeriod(),
    val accountId: Long? = null,
    val type: TxType = TxType.EXPENSE,
    val categoryId: Long? = null,
)

class StatsViewModel(
    private val repository: TransactionRepository,
    private val settings: SettingsRepository,
    private val planning: com.leftovers.app.data.PlanningRepository,
    accounts: com.leftovers.app.data.AccountRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(StatsFilter())
    private val money = combine(accounts.accounts, accounts.transfers, planning.debts) { a, t, d -> Triple(a, t, d) }

    val state: StateFlow<StatsUiState> =
        combine(repository.allTransactions, settings.settings, filter, money) { everything, s, f, (accountList, transfers, debts) ->
            val today = LocalDate.now()
            val p = f.period
            // An account that's gone shows everything again.
            val accountId = f.accountId?.takeIf { id -> accountList.any { it.id == id } }
            val options = everything.groupBy { it.categoryId }.map { (id, list) ->
                val one = list.first()
                CategoryOption(id, one.categoryName, one.categoryEmoji, one.categoryColor, one.type, list.size)
            }.sortedByDescending { it.uses }
            // One category: its own kind (spending or income) is what's shown.
            val category = f.categoryId?.let { id -> options.find { it.id == id } }
            val type = category?.type ?: f.type
            val all = everything.filter { (accountId == null || it.accountId == accountId) && (category == null || it.categoryId == category.id) }
            val oldest = everything.minOfOrNull { it.epochDay }?.let(LocalDate::ofEpochDay)
            val start = p.start(oldest)
            val end = p.end()
            val current = all.filter { it.date >= start && it.date <= end }
            val ofType = current.filter { it.type == type }
            val byMonth = p.byMonth(oldest)
            val bars = if (byMonth) {
                val first = YearMonth.from(start)
                val months = (ChronoUnit.MONTHS.between(first, YearMonth.from(end)) + 1).toInt().coerceAtLeast(1)
                LongArray(months).also { a -> ofType.forEach { a[ChronoUnit.MONTHS.between(first, YearMonth.from(it.date)).toInt()] += it.amountMinor } }
            } else {
                LongArray((ChronoUnit.DAYS.between(start, end) + 1).toInt()).also { a -> ofType.forEach { a[ChronoUnit.DAYS.between(start, it.date).toInt()] += it.amountMinor } }
            }.toList()
            val before = p.previous()?.let { prev -> all.filter { prev.contains(it.date) }.takeIf { it.isNotEmpty() }?.totalOf(type) }
            // In and out by month: the period's months, or the six up to it when it's shorter.
            val lastMonth = minOf(YearMonth.from(end), YearMonth.from(today))
            // Never from before the first entry, so there are no empty months at the start.
            val firstMonth = maxOf(if (byMonth) YearMonth.from(start) else lastMonth.minusMonths(5), oldest?.let(YearMonth::from) ?: lastMonth)
            val counted = accountList.filter { accountId != null || !it.outOfTotal }.associate { it.id to it.balanceMinor }
            val m = p.anchor
            val monthBudget = if (p.kind == PeriodKind.MONTH && accountId == null && category == null && s.plan.isSet && s.plan.cycle(m).start == m.atDay(1)) s.plan.budgetFor(m, everything) else 0L
            // Financial health (#73): for the money as a whole, so not for one category.
            val health = if (category != null) null else {
                val thisMonth = YearMonth.from(today)
                com.leftovers.app.data.FinancialHealth.of(
                    income = current.totalOf(TxType.INCOME),
                    spent = current.totalOf(TxType.EXPENSE),
                    months = p.daysSoFar(today, oldest) / 30.44,
                    budget = monthBudget.takeIf { it > 0 },
                    owe = debts.openBalances().filter { it.netMinor < 0 }.sumOf { -it.netMinor },
                    balance = counted.values.sum(),
                    // The six whole months before this one, so a month half gone doesn't look like a drop.
                    monthlyIncomes = monthlyInOut(all, thisMonth.minusMonths(6), thisMonth.minusMonths(1)).map { it.inMinor },
                    format = Money(s.currencyCode)::formatWhole,
                )
            }
            StatsUiState(
                loaded = true,
                period = p,
                accountId = accountId,
                accounts = accountList,
                oldest = oldest,
                type = type,
                total = current.totalOf(type),
                previousTotal = before,
                budget = if (p.kind == PeriodKind.MONTH && accountId == null && category == null && s.plan.isSet && s.plan.cycle(m).start == m.atDay(1)) s.plan.budgetFor(m, everything) else 0L,
                categories = current.categoryTotals(type),
                daily = if (p.kind == PeriodKind.MONTH) bars else emptyList(),
                bars = bars,
                barsByMonth = byMonth,
                count = ofType.size,
                days = p.daysSoFar(today, oldest),
                max = ofType.maxByOrNull { it.amountMinor },
                items = ofType,
                trends = if (p.kind == PeriodKind.MONTH && category == null) spendingTrends(all, m, type, Money(s.currencyCode)::formatWhole) else emptyList(),
                balance = balanceHistory(counted, everything, transfers, debts, maxOf(start, oldest ?: start), end, accountId, today),
                inOut = monthlyInOut(all, firstMonth, lastMonth),
                categoryId = category?.id,
                categoryOptions = options,
                health = health,
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(value: InsightsPeriod) = filter.update { it.copy(period = value) }
    fun setAccount(id: Long?) = filter.update { it.copy(accountId = id) }
    // Switching between spending and income lets go of a category of the other kind.
    fun setType(value: TxType) = filter.update { it.copy(type = value, categoryId = if (value == it.type) it.categoryId else null) }
    fun setCategory(id: Long?) = filter.update { it.copy(categoryId = id) }


    /** Makes the PDF statement for the month shown, or its whole year, and hands back the file to share. */
    fun makeStatement(context: android.content.Context, chosen: YearMonth, yearly: Boolean, notes: Boolean, entries: Boolean, onReady: (android.net.Uri, String) -> Unit) {
        viewModelScope.launch {
            val all = repository.allTransactions.first()
            val s = settings.settings.first()
            val m = chosen
            val statement = kotlinx.coroutines.withContext(Dispatchers.Default) {
                com.leftovers.app.util.StatementPdf.build(if (yearly) null else m, m.year, all, s.plan, planning.recurring.first(), debts = planning.debts.first())
            }
            val uri = com.leftovers.app.util.StatementPdf.write(context, statement, Money(s.currencyCode), notes, entries)
            onReady(uri, statement.title)
        }
    }
}

@Composable
fun StatsScreen(
    onOpenRecap: (YearMonth) -> Unit,
    onOpenYear: (Int) -> Unit = {},
    onOpenTransaction: (Long) -> Unit,
    /** Opens the assistant with this question; the row only shows when an AI is connected. */
    onAskAi: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val isExpense = state.type == TxType.EXPENSE
    val label = state.period.label()
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val context = androidx.compose.ui.platform.LocalContext.current
    val aiConfig by (context.applicationContext as com.leftovers.app.LeftoversApp).container.assistant.config.collectAsStateWithLifecycle()
    val aiConnected = aiConfig != null
    // Tapping an entry opens a card about it; Edit there opens the full editor.
    val peek = rememberTransactionPeek()
    TransactionPeekCard(peek, onEdit = onOpenTransaction)
    var showStatement by rememberSaveable { mutableStateOf(false) }
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
                RoundButton(Lucide.Settings, "Settings", onOpenSettings)
            }
        },
    ) { padding ->
        if (!state.loaded) return@GlassScreen
        // Sideways swipes switch tabs here like everywhere else; the arrows change the period.
        // Top to bottom: what's shown, the money as a whole, then spending or income in detail.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PeriodBar(state, viewModel::setPeriod) }
            item { FilterBar(state, viewModel::setPeriod, viewModel::setAccount, viewModel::setCategory) }
            // Balance, and in and out: they don't depend on Spending or Income, so they come before that choice.
            // They're about accounts, so they step aside while one category is shown.
            if (state.categoryId == null) item { MoneyCard(state) }
            state.health?.let { h -> item { HealthCard(h) } }
            item {
                SegmentedToggle(
                    TxType.entries, state.type,
                    { if (it == TxType.EXPENSE) "Spending" else "Income" },
                    viewModel::setType, Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item { TotalCard(state) }
            item { NoticedCard(state, aiConnected, onAskAi) }

            if (state.total == 0L) {
                item {
                    EmptyState(
                        Lucide.ChartPie,
                        if (isExpense) "No spending in $label" else "No income in $label",
                        "Your charts fill in as you add entries.",
                    )
                }
            } else {
                if (state.categoryId == null) item { CategoryCard(state, peek) } else item { EntriesCard(state, peek) }
                if (state.isMonth) item { CalendarCard(state, peek) }
            }

            // The month and the year as a story.
            item {
                val year = state.month.year
                Glass(Modifier.fillMaxWidth().appear(4)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        com.leftovers.app.ui.components.ListRow(
                            "$monthName recap",
                            subtitle = "The month as a story",
                            leading = { com.leftovers.app.ui.components.IconTile(Lucide.Sparkles, c.accent, size = 38.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary) },
                            onClick = { onOpenRecap(state.month) },
                        )
                        RowDivider()
                        com.leftovers.app.ui.components.ListRow(
                            if (year == LocalDate.now().year) "Your $year so far" else "$year in review",
                            subtitle = "The year as a story",
                            leading = { com.leftovers.app.ui.components.IconTile(Lucide.Calendar, c.accent, size = 38.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary) },
                            onClick = { onOpenYear(year) },
                        )
                    }
                }
            }
        }
    }
}

/** A card's own title, with something small on the right when there's more to say. */
@Composable
private fun CardTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Row(modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        trailing?.invoke()
    }
}

/** A small figure with its caption, for the foot of a card. */
@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, footnote: String? = null) {
    val c = LocalAppColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
        if (footnote != null) Text(footnote, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
    }
}

/** How the total card draws its period: a bar a day (or month), or the running total as a line. */
private enum class TotalChart { BARS, LINE }

/**
 * What was spent or earned: the total, how it compares, its chart (bars, or a line adding up to the total, with the
 * budget as a dashed level), and the daily average and biggest at its foot.
 */
@Composable
private fun TotalCard(state: StatsUiState) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val isExpense = state.type == TxType.EXPENSE
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    var chart by rememberSaveable { mutableStateOf(TotalChart.BARS) }
    Glass(Modifier.fillMaxWidth().appear(1), strong = true, shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (isExpense) "Spent" else "Earned") + when {
                        state.categoryName != null -> " on ${state.categoryName}"
                        state.period.kind == PeriodKind.ALL -> " in all"
                        state.isMonth -> " in $monthName"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconTabs(
                    listOf(Lucide.ChartColumn to "Bars", Lucide.ChartLine to "Running total"),
                    chart.ordinal,
                ) { chart = TotalChart.entries[it] }
            }
            RollingText(money.format(state.total), MaterialTheme.typography.displaySmall, c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComparisonChip(state)
                if (isExpense && state.budget > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("${(state.total * 100 / state.budget)}% of budget", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            val today = LocalDate.now()
            val start = state.start
            val monthFmt = remember { java.time.format.DateTimeFormatter.ofPattern("MMM") }
            val dayFmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM") }
            val todayIndex = when {
                state.barsByMonth -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(today)).toInt()
                else -> ChronoUnit.DAYS.between(start, today).toInt()
            }.takeIf { it in state.bars.indices }
            val axis: (Int) -> String? = when {
                state.barsByMonth -> { i ->
                    val m = YearMonth.from(start).plusMonths(i.toLong())
                    val every = ((state.bars.size + 5) / 6).coerceAtLeast(1)
                    if (i % every == 0) (if (m.monthValue == 1 || i == 0) "${m.format(monthFmt)} ${m.year % 100}" else m.format(monthFmt)) else null
                }
                state.isMonth -> { i -> val d = i + 1; if (d == 1 || d % 5 == 0 && d <= 25) d.toString() else null }
                else -> { i ->
                    val d = start.plusDays(i.toLong())
                    if (i == 0 || d.dayOfMonth == 1) d.format(dayFmt) else if (d.dayOfMonth % 5 == 0 && d.dayOfMonth < 30) d.dayOfMonth.toString() else null
                }
            }
            fun titleOf(i: Int) = if (state.barsByMonth) YearMonth.from(start).plusMonths(i.toLong()).label() else start.plusDays(i.toLong()).format(dayFmt)
            AnimatedContent(chart, transitionSpec = { fadeIn(androidx.compose.animation.core.tween(200)).togetherWith(fadeOut(androidx.compose.animation.core.tween(120))) }, label = "totalChart") { kind ->
                if (kind == TotalChart.BARS) {
                    ColumnChart(
                        values = state.bars,
                        highlight = todayIndex,
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        axis = axis,
                        title = ::titleOf,
                        value = { i -> money.format(state.bars.getOrElse(i) { 0L }) },
                        scale = money::compact,
                    )
                } else {
                    // The total as it built up, stopping at today in a period that's still going.
                    val running = remember(state.bars) { state.bars.runningReduce { a, b -> a + b } }
                    val upTo = (todayIndex ?: running.lastIndex).coerceAtLeast(1)
                    val shown = running.take(upTo + 1)
                    val budget = state.budget.takeIf { isExpense && it > 0 && state.isMonth }
                    TrendLine(
                        values = if (shown.size >= 2) shown else running,
                        color = c.accent,
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        axis = axis,
                        title = ::titleOf,
                        value = { i -> money.format(running.getOrElse(i) { 0L }) },
                        scale = money::compact,
                        reference = budget,
                        referenceLabel = budget?.let { "Budget" },
                        span = running.size,
                    )
                }
            }
            if (state.total > 0) {
                Spacer(Modifier.height(14.dp))
                RowDivider(inset = 0.dp)
                Spacer(Modifier.height(14.dp))
                Row {
                    Figure("Daily average", money.formatWhole(state.total / state.days), Modifier.weight(1f))
                    Figure(
                        if (isExpense) "Biggest spend" else "Biggest income",
                        money.format(state.max?.amountMinor ?: 0),
                        Modifier.weight(1f),
                        footnote = state.max?.let { if (state.categoryId != null) it.date.shortLabel() else "${it.categoryName} · ${it.date.shortLabel()}" },
                    )
                }
            }
        }
    }
}

/** Small icon choices side by side, the picked one raised; for how a chart is drawn. */
@Composable
private fun IconTabs(options: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    val view = androidx.compose.ui.platform.LocalView.current
    Row(Modifier.clip(CircleShape).background(c.glass).padding(3.dp)) {
        options.forEachIndexed { i, (icon, name) ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) c.glassStrong else Color.Transparent, label = "iconTab")
            Box(
                Modifier.size(width = 38.dp, height = 30.dp).clip(CircleShape)
                    .pressable({ if (!on) { com.leftovers.app.ui.components.Haptics.select(view); onSelect(i) } }, pressedScale = 0.92f)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = name, tint = if (on) c.textPrimary else c.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** One category's entries, newest first, when Insights is narrowed to it. */
@Composable
private fun EntriesCard(state: StatsUiState, peek: TransactionPeek) {
    val c = LocalAppColors.current
    var all by rememberSaveable(state.categoryId, state.period) { mutableStateOf(false) }
    val shown = if (all) state.items else state.items.take(8)
    Glass(Modifier.fillMaxWidth().appear(2)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            CardTitle("Entries", Modifier.padding(horizontal = 8.dp)) {
                Text("${state.items.size}", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            }
            shown.forEach { item ->
                TransactionRow(item, onClick = { peek.show(item) }, subtitle = item.date.friendlyLabel(), modifier = Modifier.peekSource(peek, item.id))
            }
            if (state.items.size > shown.size) {
                Text(
                    "Show all ${state.items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).pressable({ all = true }).padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** The categories: a ring with what most went on, then each one, tapped to list its entries. */
@Composable
private fun CategoryCard(state: StatsUiState, peek: TransactionPeek) {
    val c = LocalAppColors.current
    Glass(Modifier.fillMaxWidth().appear(2)) {
        Column(Modifier.padding(20.dp)) {
            val top = state.categories.firstOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = state.categories.map { ChartSlice(it.totalMinor.toFloat(), Color(it.color)) },
                    modifier = Modifier.size(96.dp),
                ) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.categories.size}", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("By category", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("${state.count} ${if (state.count == 1) "entry" else "entries"}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    if (top != null && state.total > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Most on ${top.name}, ${(top.totalMinor * 100f / state.total).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(top.color),
                            maxLines = 2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            var openCategory by rememberSaveable(state.period, state.type, state.accountId) { mutableStateOf<Long?>(null) }
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

/** The period with arrows either side. */
@Composable
private fun PeriodBar(state: StatsUiState, onChange: (InsightsPeriod) -> Unit) {
    val c = LocalAppColors.current
    val p = state.period
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Lucide.ChevronLeft, "Earlier", { onChange(p.shift(-1)) }, size = 40.dp, enabled = p.steps, tint = if (p.steps) c.textPrimary else c.textTertiary)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                p,
                transitionSpec = {
                    val forward = targetState.start() > initialState.start()
                    (androidx.compose.animation.slideInVertically { if (forward) it / 2 else -it / 2 } + fadeIn())
                        .togetherWith(androidx.compose.animation.slideOutVertically { if (forward) -it / 2 else it / 2 } + fadeOut())
                },
                label = "period",
            ) { shown ->
                Text(shown.label(), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        val next = p.hasNext()
        RoundButton(Lucide.ChevronRight, "Later", { onChange(p.shift(1)) }, size = 40.dp, enabled = next, tint = if (next) c.textPrimary else c.textTertiary)
    }
}

/**
 * What Insights shows, on the page: a strip for how long, and two small menus for the account and category. Each
 * change applies at once; Clear lets go of the account and category.
 */
@Composable
private fun FilterBar(
    state: StatsUiState,
    onPeriod: (InsightsPeriod) -> Unit,
    onAccount: (Long?) -> Unit,
    onCategory: (Long?) -> Unit,
) {
    val c = LocalAppColors.current
    val p = state.period
    var pickDates by rememberSaveable { mutableStateOf(false) }
    if (pickDates) {
        RangeCalendarDialog(
            from = state.start,
            to = minOf(p.end(), LocalDate.now()),
            title = "Pick dates",
            onDone = { a, b -> pickDates = false; onPeriod(InsightsPeriod(PeriodKind.DATES, YearMonth.from(b), a, b)) },
            onDismiss = { pickDates = false },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RangeStrip(p.kind) { kind ->
            if (kind == PeriodKind.DATES) pickDates = true
            else if (kind != p.kind) {
                // Keeps to the month or year being looked at, never past this one.
                val anchor = minOf(p.anchor, YearMonth.now())
                onPeriod(InsightsPeriod(kind, if (kind == PeriodKind.ALL) YearMonth.now() else anchor))
            }
        }
        val categories = state.categoryOptions.filter { it.type == state.type }
        val showAccounts = state.accounts.size > 1
        val showCategories = categories.size > 1 || state.categoryId != null
        if (showAccounts || showCategories) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showAccounts) {
                    val picked = state.accounts.find { it.id == state.accountId }
                    MenuChip(
                        icon = picked?.let { com.leftovers.app.ui.icons.CategoryIcons[it.icon] } ?: Lucide.Wallet,
                        text = picked?.name ?: "All accounts",
                        active = picked != null,
                        modifier = Modifier.weight(1f, fill = false),
                    ) { close ->
                        MenuItem("All accounts", state.accountId == null, Lucide.Wallet, c.textSecondary) { onAccount(null); close() }
                        state.accounts.forEach { a ->
                            MenuItem(a.name, state.accountId == a.id, com.leftovers.app.ui.icons.CategoryIcons[a.icon], Color(a.color)) { onAccount(a.id); close() }
                        }
                    }
                }
                if (showCategories) {
                    val picked = state.categoryOptions.find { it.id == state.categoryId }
                    MenuChip(
                        icon = picked?.let { com.leftovers.app.ui.icons.CategoryIcons[it.emoji] } ?: Lucide.Tag,
                        text = picked?.name ?: "All categories",
                        active = picked != null,
                        modifier = Modifier.weight(1f, fill = false),
                    ) { close ->
                        // Many categories: a search box first. Most used come first either way, with how many entries each has.
                        var query by remember { mutableStateOf("") }
                        if (categories.size > SEARCH_FROM) MenuSearch(query) { query = it }
                        val found = if (query.isBlank()) categories else categories.filter { it.name.contains(query.trim(), ignoreCase = true) }
                        if (query.isBlank()) MenuItem("All categories", state.categoryId == null, Lucide.Tag, c.textSecondary) { onCategory(null); close() }
                        found.forEach { cat ->
                            MenuItem(cat.name, state.categoryId == cat.id, com.leftovers.app.ui.icons.CategoryIcons[cat.emoji], Color(cat.color), detail = "${cat.uses}") { onCategory(cat.id); close() }
                        }
                        if (found.isEmpty()) {
                            Text(
                                "No category called “${query.trim()}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textTertiary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
                if (state.accountId != null || state.categoryId != null) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.accent,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).pressable({ onAccount(null); onCategory(null) }, pressedScale = 0.94f).padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Short names for the strip, so all six fit across a phone. */
private val PeriodKind.short: String get() = when (this) {
    PeriodKind.MONTH -> "Month"
    PeriodKind.THREE_MONTHS -> "3M"
    PeriodKind.SIX_MONTHS -> "6M"
    PeriodKind.YEAR -> "Year"
    PeriodKind.ALL -> "All"
    PeriodKind.DATES -> "Dates"
}

/** How long, as one slim strip; the raised marker slides to the one picked, like a range switch on a chart. */
@Composable
private fun RangeStrip(selected: PeriodKind, onSelect: (PeriodKind) -> Unit) {
    val c = LocalAppColors.current
    val view = androidx.compose.ui.platform.LocalView.current
    val kinds = PeriodKind.entries
    var width by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slot = with(density) { (width / kinds.size).toDp() }
    val offset by androidx.compose.animation.core.animateDpAsState(
        slot * selected.ordinal,
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 500f),
        label = "rangeMarker",
    )
    Box(
        Modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(c.glass)
            .border(1.dp, c.textTertiary.copy(alpha = 0.14f), CircleShape)
            .padding(3.dp)
            .onSizeChanged { width = it.width },
    ) {
        if (width > 0) {
            Box(Modifier.offset(x = offset).width(slot).fillMaxHeight().clip(CircleShape).background(c.glassStrong))
        }
        Row(Modifier.fillMaxSize()) {
            kinds.forEach { kind ->
                val on = kind == selected
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .pressable({ com.leftovers.app.ui.components.Haptics.select(view); onSelect(kind) }, pressedScale = 0.94f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (kind == PeriodKind.DATES) {
                        Icon(Lucide.Calendar, contentDescription = null, tint = if (on) c.textPrimary else c.textTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(kind.short, style = MaterialTheme.typography.labelMedium, color = if (on) c.textPrimary else c.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** A small chip that opens a menu right under it; tinted and outlined once it narrows the page. */
@Composable
private fun MenuChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    items: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit,
) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    val bg by androidx.compose.animation.animateColorAsState(if (active) c.accent.copy(alpha = 0.14f) else c.glass, label = "menuChipBg")
    val edge by androidx.compose.animation.animateColorAsState(if (active) c.accent.copy(alpha = 0.6f) else c.textTertiary.copy(alpha = 0.14f), label = "menuChipEdge")
    Box(modifier) {
        Row(
            Modifier.height(38.dp).clip(CircleShape)
                .pressable({ open = true }, pressedScale = 0.96f)
                .background(bg)
                .border(1.dp, edge, CircleShape)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) c.accent else c.textSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(272.dp).heightIn(max = 420.dp),
        ) { items { open = false } }
    }
}

/** From how many choices a menu gets a search box. */
private const val SEARCH_FROM = 8

/**
 * One choice in a [MenuChip]'s menu: its icon, its name (cut short when long), a small [detail] like how many
 * entries it has, and a tick on the one in use.
 */
@Composable
private fun MenuItem(
    text: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = c.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = c.textTertiary, modifier = Modifier.padding(start = 8.dp))
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            // Always the same room, so the counts line up whether or not this one is ticked.
            Box(Modifier.size(18.dp)) {
                if (selected) Icon(Lucide.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(18.dp))
            }
        },
        onClick = onClick,
    )
}

/** A search box at the top of a long menu; it takes the keyboard as soon as the menu opens. */
@Composable
private fun MenuSearch(query: String, onChange: (String) -> Unit) {
    val c = LocalAppColors.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.glass).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search categories", style = MaterialTheme.typography.bodyMedium, color = c.textTertiary)
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Icon(Lucide.X, contentDescription = "Clear the search", tint = c.textTertiary, modifier = Modifier.size(16.dp).clip(CircleShape).pressable({ onChange("") }))
        }
    }
}

/** What's worth knowing: the trends spotted, then asking the AI about it and the phone's own AI summing up a month. */
@Composable
private fun NoticedCard(state: StatsUiState, aiConnected: Boolean, onAskAi: (String) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val isExpense = state.type == TxType.EXPENSE
    val label = state.period.label()
    var ready by remember { mutableStateOf(false) }
    if (com.leftovers.app.ai.OnDevice.inBuild && isExpense && state.isMonth) {
        LaunchedEffect(Unit) { ready = com.leftovers.app.ai.OnDevice.status() == com.leftovers.app.ai.LocalStatus.READY }
    }
    val summarise = ready && isExpense && state.isMonth && state.count > 0
    val ask = aiConnected && state.count > 0
    if (!ask && !summarise && state.trends.isEmpty()) return
    Glass(Modifier.fillMaxWidth().appear(2)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (state.trends.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.trends.forEach { t ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                if (t.down) Lucide.TrendingDown else Lucide.TrendingUp,
                                contentDescription = null,
                                tint = when (t.good) {
                                    true -> c.positive
                                    false -> c.warning
                                    null -> c.accent
                                },
                                modifier = Modifier.padding(top = 2.dp).size(16.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(t.text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
                if (ask || summarise) RowDivider(inset = 18.dp)
            }
            if (ask) {
                val question = when {
                    state.period.kind == PeriodKind.MONTH && isExpense -> {
                        val previous = state.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        "How did my spending go in $label? Compare it with $previous and my budget, say what changed most, and suggest one or two things I could do differently."
                    }
                    isExpense -> "How did my spending go over $label (${state.start} to ${minOf(state.period.end(), LocalDate.now())})" +
                        (state.accountName?.let { " in my $it account" } ?: "") + "? What changed most, and what could I do differently?"
                    else -> "How did my income go over $label (${state.start} to ${minOf(state.period.end(), LocalDate.now())})? Anything worth noticing?"
                }
                com.leftovers.app.ui.components.ListRow(
                    "Ask AI about $label",
                    subtitle = if (isExpense) "What changed, and what to do about it" else "How your income compares",
                    leading = { com.leftovers.app.ui.components.IconTile(Lucide.BotMessageSquare, c.accent, size = 38.dp) },
                    trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary) },
                    onClick = { onAskAi(question) },
                )
            }
            // On a tap, not on its own, as Android limits how often an app can use the phone's AI.
            if (summarise) {
                if (ask) RowDivider()
                var summary by remember(state.period) { mutableStateOf<String?>(null) }
                var writing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val facts = buildString {
                    append("Month: $label\n")
                    append("Spent: ${money.format(state.total)} in ${state.count} entries\n")
                    state.previousTotal?.let { append("Spent the month before: ${money.format(it)}\n") }
                    if (state.budget > 0) append("Budget: ${money.format(state.budget)}\n")
                    append("Biggest categories: " + state.categories.take(3).joinToString { "${it.name} ${money.format(it.totalMinor)}" } + "\n")
                    state.max?.let { append("Biggest single spend: ${money.format(it.amountMinor)} on ${it.categoryName}\n") }
                }
                com.leftovers.app.ui.components.ListRow(
                    "Sum up ${state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    subtitle = summary ?: if (writing) "Writing it on this phone…" else "In a sentence or two, written on this phone",
                    leading = { com.leftovers.app.ui.components.IconTile(Lucide.Sparkles, c.accent, size = 38.dp) },
                    onClick = {
                        if (!writing) {
                            writing = true
                            scope.launch {
                                summary = com.leftovers.app.ai.OnDevice.summarise(facts) ?: "This phone's AI couldn't write it just now."
                                writing = false
                            }
                        }
                    },
                )
            }
        }
    }
}

/** The month as a calendar, shaded by how heavy each day was; folded to a row until it's opened. */
@Composable
private fun CalendarCard(state: StatsUiState, peek: TransactionPeek) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "calendarChevron")
    val heaviest = state.daily.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
    Glass(Modifier.fillMaxWidth().appear(3)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable({ open = !open }, pressedScale = 0.98f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Calendar", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    heaviest?.let {
                        Text(
                            (if (state.type == TxType.EXPENSE) "Heaviest day " else "Best day ") + "${state.month.atDay(it.index + 1).shortLabel()}, ${money.formatWhole(it.value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
                Icon(Lucide.ChevronRight, contentDescription = if (open) "Hide the calendar" else "Show the calendar", tint = c.textTertiary, modifier = Modifier.size(18.dp).rotate(turn))
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 14.dp)) {
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
                                RowDivider(inset = 0.dp)
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
    }
}

/**
 * The money as a whole over the period: the balance (#72), or what came in and went out. One card with a switch,
 * so the page doesn't grow by two charts; each view is a big number, a line saying what it means, and its chart.
 */
@Composable
private fun MoneyCard(state: StatsUiState) {
    val c = LocalAppColors.current
    val hasBalance = state.balance.size >= 2
    val hasFlow = state.inOut.any { it.inMinor > 0 || it.outMinor > 0 }
    if (!hasBalance && !hasFlow) return
    var tab by rememberSaveable { mutableStateOf(0) }
    val shown = when {
        !hasBalance -> 1
        !hasFlow -> 0
        else -> tab
    }
    Glass(Modifier.fillMaxWidth().appear(0)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
            CardTitle("Your money", trailing = if (hasBalance && hasFlow) {
                { PillTabs(listOf("Balance", "In & out"), shown) { tab = it } }
            } else null)
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                shown,
                transitionSpec = {
                    (fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60))).togetherWith(fadeOut(androidx.compose.animation.core.tween(120)))
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "moneyView",
            ) { view -> if (view == 0) BalanceView(state) else FlowView(state) }
        }
    }
}

/**
 * Financial health (#73): a score out of 100 in a ring, what it means, and, on a tap, how it's worked out: each
 * measure with a bar and a plain sentence. Measures without enough to go on say so and aren't counted.
 */
@Composable
private fun HealthCard(h: com.leftovers.app.data.FinancialHealth.Health) {
    val c = LocalAppColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    fun tone(score: Float) = when {
        score >= 0.8f -> c.positive
        score >= 0.6f -> c.accent
        score >= 0.4f -> c.warning
        else -> c.negative
    }
    val color = tone(h.score / 100f)
    val sweep by animateFloatAsState(h.score / 100f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "healthRing")
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "healthChevron")
    Glass(Modifier.fillMaxWidth().appear(0)) {
        Column(Modifier.padding(20.dp)) {
            CardTitle("Financial health")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                    val track = c.glassStrong
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        // A soft glow of the score's colour behind the ring, so the card takes on its mood.
                        drawCircle(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(color.copy(alpha = 0.22f), Color.Transparent),
                                center = center, radius = size.minDimension * 0.85f,
                            ),
                            radius = size.minDimension * 0.85f,
                        )
                        val stroke = 11.dp.toPx()
                        val inset = stroke / 2
                        val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        val style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawArc(track, 135f, 270f, false, tl, arc, style = style)
                        if (sweep > 0.005f) {
                            // The arc deepens toward the score, faint where it starts and full where it ends.
                            rotateDraw(135f) {
                                drawArc(
                                    androidx.compose.ui.graphics.Brush.sweepGradient(
                                        0f to color.copy(alpha = 0.35f),
                                        0.75f * sweep to color,
                                        1f to color.copy(alpha = 0.35f),
                                        center = center,
                                    ),
                                    0f, 270f * sweep, false, tl, arc, style = style,
                                )
                            }
                            // A bright bead where the score sits.
                            val a = Math.toRadians((135f + 270f * sweep).toDouble())
                            val r = (size.minDimension - stroke) / 2
                            val at = androidx.compose.ui.geometry.Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
                            drawCircle(color.copy(alpha = 0.3f), stroke * 0.95f, at)
                            drawCircle(Color.White, stroke * 0.32f, at)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Counts up with the ring as it fills.
                        Text(
                            "${Math.round(sweep * 100)}",
                            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-1).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = c.textPrimary,
                        )
                        Text("of 100", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(7.dp))
                        Text(h.label, style = MaterialTheme.typography.labelLarge, color = color)
                    }
                    Text(h.summary, style = MaterialTheme.typography.bodyMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading), color = c.textPrimary.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(18.dp))
            // The five measures at a glance: each as wide as it counts, filled as far as it's doing.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                h.parts.forEach { part ->
                    Box(Modifier.weight(part.weight.toFloat()).height(6.dp).clip(CircleShape).background(c.glassStrong)) {
                        part.score?.let { f ->
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.04f)).clip(CircleShape).background(tone(f)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable({ open = !open }, pressedScale = 0.98f).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("How it's worked out", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(16.dp).rotate(turn))
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    h.parts.forEach { part ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(part.name, style = MaterialTheme.typography.titleSmall, color = if (part.score == null) c.textTertiary else c.textPrimary, modifier = Modifier.weight(1f))
                                Text(
                                    part.score?.let { "${Math.round(it * part.weight)} of ${part.weight}" } ?: "Not counted",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textTertiary,
                                )
                            }
                            Text(part.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            if (part.score != null) {
                                Spacer(Modifier.height(6.dp))
                                ProgressLine(part.score, Modifier.fillMaxWidth(), color = tone(part.score), height = 5.dp)
                            }
                        }
                    }
                    Text(
                        "Worked out on this phone from your own entries, balances and money owed. It's a guide, not financial advice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }
        }
    }
}

/** Two small choices side by side, the picked one raised. */
@Composable
private fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    val view = androidx.compose.ui.platform.LocalView.current
    Row(Modifier.clip(CircleShape).background(c.glass).padding(3.dp)) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) c.glassStrong else Color.Transparent, label = "pill")
            Box(
                Modifier.clip(CircleShape)
                    .pressable({ if (!on) { com.leftovers.app.ui.components.Haptics.select(view); onSelect(i) } }, pressedScale = 0.95f)
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) c.textPrimary else c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BalanceView(state: StatsUiState) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val points = state.balance
    val first = points.first()
    val last = points.last()
    val change = last.balanceMinor - first.balanceMinor
    val color = state.accountId?.let { id -> state.accounts.find { it.id == id }?.color }?.let { Color(it) } ?: c.accent
    val spansYears = first.day.year != last.day.year
    val day = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM") }
    val dayYear = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy") }
    val since = first.day.format(if (spansYears) dayYear else day)
    Column {
        RollingText(money.format(last.balanceMinor), MaterialTheme.typography.headlineMedium, if (last.balanceMinor < 0) c.negative else c.textPrimary)
        Text(
            (state.accountName ?: "All accounts") + " · " + when {
                change == 0L -> "no change since $since"
                change > 0 -> "up ${money.format(change)} since $since"
                else -> "down ${money.format(-change)} since $since"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (change == 0L) c.textSecondary else if (change > 0) c.positive else c.negative,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        // Four dates along the bottom, evenly apart.
        val marks = (0..3).map { (it * points.lastIndex / 3f).roundToInt() }.toSet()
        TrendLine(
            values = points.map { it.balanceMinor },
            color = color,
            modifier = Modifier.fillMaxWidth().height(170.dp),
            axis = { i -> if (i in marks) points[i].day.format(if (spansYears) java.time.format.DateTimeFormatter.ofPattern("MMM yy") else day) else null },
            title = { i -> points[i].day.format(if (spansYears) dayYear else day) },
            value = { i -> money.format(points[i].balanceMinor) },
            scale = money::compact,
        )
    }
}

@Composable
private fun FlowView(state: StatsUiState) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val months = state.inOut
    val saved = months.sumOf { it.savedMinor }
    val over = if (months.size == 1) months.first().month.label() else "${months.first().month.label()} – ${months.last().month.label()}"
    // Out is kept neutral, so it doesn't read as a second kind of good.
    val outColor = c.textSecondary
    Column {
        RollingText(money.formatWhole(kotlin.math.abs(saved)), MaterialTheme.typography.headlineMedium, c.textPrimary)
        Text(
            (if (saved >= 0) "Saved" else "More out than in") + " · $over",
            style = MaterialTheme.typography.labelMedium,
            color = if (saved >= 0) c.positive else c.negative,
            maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))
        if (months.size == 1) {
            // One month: two bars across, which say more than two lone columns.
            val m = months.first()
            val max = maxOf(m.inMinor, m.outMinor).coerceAtLeast(1L).toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowBar("In", m.inMinor, m.inMinor / max, c.positive)
                FlowBar("Out", m.outMinor, m.outMinor / max, outColor)
            }
            Spacer(Modifier.height(4.dp))
        } else {
            val short = remember { java.time.format.DateTimeFormatter.ofPattern("MMM") }
            PairBars(
                pairs = months.map { it.inMinor to it.outMinor },
                first = c.positive,
                second = outColor,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                axis = { i -> months[i].month.format(short) },
                title = { i -> months[i].month.format(short) },
                value = { i -> "in ${money.formatWhole(months[i].inMinor)} · out ${money.formatWhole(months[i].outMinor)}" },
                scale = money::compact,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.positive, "In")
                Legend(outColor, "Out")
            }
        }
    }
}

/** One row of the single-month view: a label, a bar as long as its share, and the amount. */
@Composable
private fun FlowBar(label: String, amount: Long, fraction: Float, color: Color) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val grow by animateFloatAsState(fraction, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "flowBar")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.width(40.dp))
        Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(c.glass)) {
            Box(Modifier.fillMaxWidth(grow.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(12.dp))
        Text(money.formatWhole(amount), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
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
    val before = state.previousTotal
    if (before == null || before <= 0) {
        if (state.period.steps) Text(if (state.isMonth) "No entries last month" else "Nothing before to compare with", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        return
    }
    val change = (state.total - before).toFloat() / before * 100
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
        Text("$pct% ${state.period.versus()}", style = MaterialTheme.typography.labelMedium, color = tint)
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
