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
import com.leftovers.app.ui.components.PairBars
import com.leftovers.app.ui.components.TrendLine
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
) {
    val month: YearMonth get() = period.anchor
    val isMonth: Boolean get() = period.kind == PeriodKind.MONTH
    val start: LocalDate get() = period.start(oldest)
    val accountName: String? get() = accountId?.let { id -> accounts.find { it.id == id }?.name }
}

/** What Insights is showing: the period, the account, and spending or income. */
private data class StatsFilter(val period: InsightsPeriod = InsightsPeriod(), val accountId: Long? = null, val type: TxType = TxType.EXPENSE)

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
            val all = if (accountId == null) everything else everything.filter { it.accountId == accountId }
            val oldest = everything.minOfOrNull { it.epochDay }?.let(LocalDate::ofEpochDay)
            val start = p.start(oldest)
            val end = p.end()
            val current = all.filter { it.date >= start && it.date <= end }
            val ofType = current.filter { it.type == f.type }
            val byMonth = p.byMonth(oldest)
            val bars = if (byMonth) {
                val first = YearMonth.from(start)
                val months = (ChronoUnit.MONTHS.between(first, YearMonth.from(end)) + 1).toInt().coerceAtLeast(1)
                LongArray(months).also { a -> ofType.forEach { a[ChronoUnit.MONTHS.between(first, YearMonth.from(it.date)).toInt()] += it.amountMinor } }
            } else {
                LongArray((ChronoUnit.DAYS.between(start, end) + 1).toInt()).also { a -> ofType.forEach { a[ChronoUnit.DAYS.between(start, it.date).toInt()] += it.amountMinor } }
            }.toList()
            val before = p.previous()?.let { prev -> all.filter { prev.contains(it.date) }.takeIf { it.isNotEmpty() }?.totalOf(f.type) }
            // In and out by month: the period's months, or the six up to it when it's shorter.
            val lastMonth = minOf(YearMonth.from(end), YearMonth.from(today))
            // Never from before the first entry, so there are no empty months at the start.
            val firstMonth = maxOf(if (byMonth) YearMonth.from(start) else lastMonth.minusMonths(5), oldest?.let(YearMonth::from) ?: lastMonth)
            val counted = accountList.filter { accountId != null || !it.outOfTotal }.associate { it.id to it.balanceMinor }
            val m = p.anchor
            StatsUiState(
                loaded = true,
                period = p,
                accountId = accountId,
                accounts = accountList,
                oldest = oldest,
                type = f.type,
                total = current.totalOf(f.type),
                previousTotal = before,
                budget = if (p.kind == PeriodKind.MONTH && accountId == null && s.plan.isSet && s.plan.cycle(m).start == m.atDay(1)) s.plan.budgetFor(m, everything) else 0L,
                categories = current.categoryTotals(f.type),
                daily = if (p.kind == PeriodKind.MONTH) bars else emptyList(),
                bars = bars,
                barsByMonth = byMonth,
                count = ofType.size,
                days = p.daysSoFar(today, oldest),
                max = ofType.maxByOrNull { it.amountMinor },
                items = ofType,
                trends = if (p.kind == PeriodKind.MONTH) spendingTrends(all, m, f.type, Money(s.currencyCode)::formatWhole) else emptyList(),
                balance = balanceHistory(counted, everything, transfers, debts, maxOf(start, oldest ?: start), end, accountId, today),
                inOut = monthlyInOut(all, firstMonth, lastMonth),
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(value: InsightsPeriod) = filter.update { it.copy(period = value) }
    fun setAccount(id: Long?) = filter.update { it.copy(accountId = id) }
    fun setType(value: TxType) = filter.update { it.copy(type = value) }

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
    var showFilter by rememberSaveable { mutableStateOf(false) }
    if (showFilter) {
        ShowSheet(state, viewModel::setPeriod, viewModel::setAccount) { showFilter = false }
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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PeriodBar(state, viewModel::setPeriod) { showFilter = true } }
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
                            (if (isExpense) "Spent" else "Earned") + if (state.period.kind == PeriodKind.ALL) " in all" else if (state.isMonth) " in $monthName" else "",
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
                        val start = state.start
                        val monthFmt = remember { java.time.format.DateTimeFormatter.ofPattern("MMM") }
                        DailyBars(
                            values = state.bars,
                            highlight = when {
                                state.barsByMonth -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(today)).toInt().takeIf { it in state.bars.indices }
                                else -> ChronoUnit.DAYS.between(start, today).toInt().takeIf { it in state.bars.indices }
                            },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            tooltip = { i ->
                                val v = money.format(state.bars.getOrElse(i) { 0L })
                                if (state.barsByMonth) "${YearMonth.from(start).plusMonths(i.toLong()).label()} · $v" else "${start.plusDays(i.toLong()).shortLabel()} · $v"
                            },
                            axis = when {
                                state.isMonth -> null
                                state.barsByMonth -> { i ->
                                    val m = YearMonth.from(start).plusMonths(i.toLong())
                                    val every = ((state.bars.size + 5) / 6).coerceAtLeast(1)
                                    if (i % every == 0) (if (m.monthValue == 1 || i == 0) "${m.format(monthFmt)} ${m.year % 100}" else m.format(monthFmt)) else null
                                }
                                else -> { i ->
                                    val d = start.plusDays(i.toLong())
                                    if (i == 0 || d.dayOfMonth == 1) d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) else if (d.dayOfMonth % 5 == 0 && d.dayOfMonth < 30) d.dayOfMonth.toString() else null
                                }
                            },
                        )
                        Text(
                            if (state.barsByMonth) "Touch the bars to see each month" else "Touch the bars to see each day",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            if (state.trends.isNotEmpty()) {
                item {
                    Glass(Modifier.fillMaxWidth().appear(1)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.trends.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (t.down) Lucide.TrendingDown else Lucide.TrendingUp,
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

            // The AI, when there is one: asking about the period, and the phone's own short summary of a month.
            if (state.count > 0) {
                item { AiCard(state, aiConnected, onAskAi) }
            }

            if (state.total == 0L) {
                item {
                    EmptyState(
                        Lucide.ChartPie,
                        if (isExpense) "No spending in $label" else "No income in $label",
                        "Your charts fill in as you add entries.",
                    )
                }
            } else {
                item {
                    Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Daily average", money.formatWhole(state.total / state.days), Modifier.weight(1f))
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

                if (state.isMonth) {
                    item { SectionHeader("Calendar", Modifier.appear(3)) }
                    item { CalendarCard(state, peek) }
                }
            }

            // How the money itself moved: the balance over the period, and what came in and went out each month.
            if (state.balance.size >= 2) {
                item { SectionHeader("Balance", Modifier.appear(3)) }
                item { BalanceCard(state) }
            }
            if (state.inOut.any { it.inMinor > 0 || it.outMinor > 0 }) {
                item { SectionHeader("Money in and out", Modifier.appear(4)) }
                item { InOutCard(state) }
            }

            // The month and the year as a story.
            item {
                val year = state.month.year
                Glass(Modifier.fillMaxWidth().padding(top = 12.dp).appear(4)) {
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

/** The period with arrows either side; tapping it opens [ShowSheet] to pick another period or one account. */
@Composable
private fun PeriodBar(state: StatsUiState, onChange: (InsightsPeriod) -> Unit, onOpen: () -> Unit) {
    val c = LocalAppColors.current
    val p = state.period
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Lucide.ChevronLeft, "Earlier", { onChange(p.shift(-1)) }, size = 40.dp, enabled = p.steps, tint = if (p.steps) c.textPrimary else c.textTertiary)
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).pressable(onOpen, pressedScale = 0.97f).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.accountName ?: "All accounts", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Lucide.ChevronDown, contentDescription = "Change what's shown", tint = c.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
        val next = p.hasNext()
        RoundButton(Lucide.ChevronRight, "Later", { onChange(p.shift(1)) }, size = 40.dp, enabled = next, tint = if (next) c.textPrimary else c.textTertiary)
    }
}

/** Everything Insights can be narrowed to, in one place: how long, and which account. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShowSheet(state: StatsUiState, onPeriod: (InsightsPeriod) -> Unit, onAccount: (Long?) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var pickDates by rememberSaveable { mutableStateOf(false) }
    val p = state.period
    if (pickDates) {
        val today = LocalDate.now()
        RangeCalendarSheet(
            from = state.start,
            to = minOf(p.end(), today),
            title = "Pick dates",
            onDone = { a, b -> pickDates = false; onPeriod(InsightsPeriod(PeriodKind.DATES, YearMonth.from(b), a, b)) },
            onDismiss = { pickDates = false },
        )
    }
    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Show", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text("Period", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodKind.entries.forEach { kind ->
                    com.leftovers.app.ui.components.Chip(
                        kind.title,
                        {
                            if (kind == PeriodKind.DATES) pickDates = true
                            else {
                                // Keeps to the month or year being looked at, never past this one.
                                val anchor = minOf(p.anchor, YearMonth.now())
                                onPeriod(InsightsPeriod(kind, if (kind == PeriodKind.ALL) YearMonth.now() else anchor))
                            }
                        },
                        selected = p.kind == kind,
                        icon = if (kind == PeriodKind.DATES) Lucide.Calendar else null,
                    )
                }
            }
            if (state.accounts.size > 1) {
                Text("Account", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.leftovers.app.ui.components.Chip("All accounts", { onAccount(null) }, selected = state.accountId == null)
                    state.accounts.forEach { a ->
                        com.leftovers.app.ui.components.Chip(a.name, { onAccount(a.id) }, selected = state.accountId == a.id)
                    }
                }
            }
            com.leftovers.app.ui.components.PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Asking the AI about the period, and the phone's own AI summing up a month: one card for both. */
@Composable
private fun AiCard(state: StatsUiState, aiConnected: Boolean, onAskAi: (String) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val isExpense = state.type == TxType.EXPENSE
    val label = state.period.label()
    var ready by remember { mutableStateOf(false) }
    if (com.leftovers.app.ai.OnDevice.inBuild && isExpense && state.isMonth) {
        LaunchedEffect(Unit) { ready = com.leftovers.app.ai.OnDevice.status() == com.leftovers.app.ai.LocalStatus.READY }
    }
    val summarise = ready && isExpense && state.isMonth
    if (!aiConnected && !summarise) return
    Glass(Modifier.fillMaxWidth().appear(1)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (aiConnected) {
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
                if (aiConnected) RowDivider()
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

/** The month as a calendar, shaded by how heavy each day was; tapping a day lists it. */
@Composable
private fun CalendarCard(state: StatsUiState, peek: TransactionPeek) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
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

/** The balance over the period (#72), of every account counted in the total or the one picked. */
@Composable
private fun BalanceCard(state: StatsUiState) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val points = state.balance
    val first = points.first()
    val last = points.last()
    val change = last.balanceMinor - first.balanceMinor
    val color = state.accountId?.let { id -> state.accounts.find { it.id == id }?.color }?.let { Color(it) } ?: c.accent
    val day = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM") }
    val dayYear = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy") }
    val spansYears = first.day.year != last.day.year
    Glass(Modifier.fillMaxWidth().appear(3)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                (state.accountName ?: "All accounts") + if (last.day == LocalDate.now()) " · today" else " · ${last.day.format(day)}",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(2.dp))
            RollingText(money.format(last.balanceMinor), MaterialTheme.typography.headlineMedium, if (last.balanceMinor < 0) c.negative else c.textPrimary)
            Text(
                when {
                    change == 0L -> "No change since ${first.day.format(if (spansYears) dayYear else day)}"
                    change > 0 -> "Up ${money.format(change)} since ${first.day.format(if (spansYears) dayYear else day)}"
                    else -> "Down ${money.format(-change)} since ${first.day.format(if (spansYears) dayYear else day)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (change == 0L) c.textSecondary else if (change > 0) c.positive else c.negative,
            )
            Spacer(Modifier.height(14.dp))
            val marks = setOf(0, points.size / 3, points.size * 2 / 3, points.lastIndex)
            TrendLine(
                values = points.map { it.balanceMinor },
                color = color,
                modifier = Modifier.fillMaxWidth().height(170.dp),
                axis = { i -> if (i in marks) points[i].day.format(if (spansYears) java.time.format.DateTimeFormatter.ofPattern("MMM yy") else day) else null },
                tooltip = { i -> "${points[i].day.format(if (spansYears) dayYear else day)} · ${money.format(points[i].balanceMinor)}" },
            )
        }
    }
}

/** What came in and what went out, a pair of bars a month, with what was saved over them. */
@Composable
private fun InOutCard(state: StatsUiState) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val months = state.inOut
    val saved = months.sumOf { it.savedMinor }
    val short = remember { java.time.format.DateTimeFormatter.ofPattern("MMM") }
    Glass(Modifier.fillMaxWidth().appear(4)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (months.size == 1) months.first().month.label() else "${months.first().month.label()} – ${months.last().month.label()}",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (saved >= 0) "Saved ${money.formatWhole(saved)}" else "${money.formatWhole(-saved)} more out than in",
                style = MaterialTheme.typography.titleLarge,
                color = if (saved >= 0) c.positive else c.negative,
            )
            Spacer(Modifier.height(14.dp))
            PairBars(
                pairs = months.map { it.inMinor to it.outMinor },
                first = c.positive,
                second = c.accent,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                axis = { i -> months[i].month.format(short) },
                tooltip = { i -> "${months[i].month.format(short)} · in ${money.formatWhole(months[i].inMinor)} · out ${money.formatWhole(months[i].outMinor)}" },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.positive, "In")
                Legend(c.accent, "Out")
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
