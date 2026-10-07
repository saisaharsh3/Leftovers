package com.leftovers.app.ui.screens

import com.leftovers.app.data.AccountWithBalance
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.components.pressable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.DailyBudget
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.forecastMonthEnd
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.SmsRepository
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.data.dailyBudget
import com.leftovers.app.data.pendingExpenses
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.DockClearance
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.components.LocalHazeState
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.ProgressLine
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.StatTile
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.sharedContainer
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.app.util.label
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs

data class HomeUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val budgetSet: Boolean = false,
    val today: DailyBudget? = null,
    val spentToday: Long = 0,
    val daysLeft: Int = 1,
    val monthSpent: Long = 0,
    val monthIncome: Long = 0,
    val upcoming: List<RecurringItem> = emptyList(),
    val goals: List<GoalWithSaved> = emptyList(),
    val recent: List<TransactionItem> = emptyList(),
    val smsSuggestions: List<SmsSuggestion> = emptyList(),
    /** Last month, when its recap hasn't been seen yet. */
    val recapMonth: YearMonth? = null,
    /** Last month's leftover, while the user hasn't decided whether to carry it over. */
    val carryPrompt: Long? = null,
    /** Sum of every account's balance; null until accounts load. */
    val balance: Long? = null,
    val accountCount: Int = 0,
    /** Where [balance] is heading by the end of the month; null early in the month. */
    val forecast: Long? = null,
    /** All accounts, for the picker on the balance card. */
    val accounts: List<AccountWithBalance> = emptyList(),
    /** The account Home is showing, or null for all of them. */
    val selectedAccount: AccountWithBalance? = null,
    val aiEnabled: Boolean = true,
)

class HomeViewModel(
    repository: TransactionRepository,
    private val settings: SettingsRepository,
    planning: PlanningRepository,
    private val sms: SmsRepository,
    accounts: AccountRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        combine(repository.allTransactions, accounts.accounts, ::Pair),
        settings.settings,
        planning.recurring,
        planning.goals,
        sms.suggestions,
    ) { (all, accountList), s, recurring, goals, suggestions ->
        val month = YearMonth.now()
        val todayDate = LocalDate.now()
        val items = all.filter { YearMonth.from(it.date) == month }
        val budget = s.plan.budgetFor(month, all)
        // Like switching wallets: the balance, spent, income and recent entries follow the chosen account.
        // The budget and safe-to-spend stay for all money together.
        val selected = accountList.firstOrNull { it.id == s.homeAccountId }
        val shownAll = if (selected == null) all else all.filter { it.accountId == selected.id }
        val shownMonth = if (selected == null) items else items.filter { it.accountId == selected.id }
        HomeUiState(
            loaded = true,
            month = month,
            budgetSet = s.plan.isSet,
            today = dailyBudget(budget, items, recurring.pendingExpenses(month)),
            spentToday = items.filter { it.type == TxType.EXPENSE && it.epochDay == todayDate.toEpochDay() }.sumOf { it.amountMinor },
            daysLeft = month.lengthOfMonth() - todayDate.dayOfMonth + 1,
            monthSpent = shownMonth.totalOf(TxType.EXPENSE),
            monthIncome = shownMonth.totalOf(TxType.INCOME),
            upcoming = recurring.filter { it.active }
                .sortedBy { it.toRecurring().nextChargeDate() }
                .filter { it.toRecurring().nextChargeDate() <= todayDate.plusDays(30) }
                .take(6),
            goals = goals.filter { !it.reached }.take(5),
            recent = shownAll.take(6),
            smsSuggestions = suggestions,
            recapMonth = month.minusMonths(1).takeIf { prev ->
                todayDate.dayOfMonth <= 10 && s.recapSeen != prev.toString() && all.any { YearMonth.from(it.date) == prev }
            },
            carryPrompt = s.plan.pendingCarry(month, all),
            balance = selected?.balanceMinor ?: accountList.sumOf { it.balanceMinor },
            accountCount = accountList.size,
            // Bills and salary aren't tied to an account, so the forecast is only for all money together.
            forecast = if (selected == null) forecastMonthEnd(accountList.sumOf { it.balanceMinor }, all, recurring, todayDate) else null,
            accounts = accountList,
            selectedAccount = selected,
            aiEnabled = s.aiEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun answerCarry(carry: Boolean) {
        viewModelScope.launch { settings.setCarryChoice(YearMonth.now().minusMonths(1), carry) }
    }

    fun dismissSms(id: Long) {
        viewModelScope.launch { sms.dismiss(id) }
    }

    fun showAccount(id: Long?) {
        viewModelScope.launch { settings.setHomeAccount(id) }
    }
}

@Composable
fun HomeScreen(
    onOpenBudget: () -> Unit,
    /** Opens Activity on spending, income, or everything (null). */
    /** Opens Activity on spending, income or everything (null), for the account Home is showing. */
    onOpenActivity: (type: TxType?, accountId: Long?) -> Unit,
    /** Opens Subscriptions on payments, or on recurring income when true. */
    onOpenSubscriptions: (income: Boolean) -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onReviewSms: (SmsSuggestion) -> Unit,
    onOpenRecap: (YearMonth) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAssistant: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    if (!state.loaded) return

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .hazeSource(LocalHazeState.current, zIndex = 1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockClearance),
        ) {
            item {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(top = 12.dp, bottom = 18.dp, start = 4.dp)
                        .appear(0),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        Text(state.month.label(), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
                    }
                    if (state.aiEnabled) {
                        RoundButton(Lucide.MessageCircle, "AI assistant", onOpenAssistant)
                        Spacer(Modifier.width(8.dp))
                    }
                    RoundButton(Lucide.Settings, "Settings", onOpenSettings)
                }
            }

            state.recapMonth?.let { month ->
                item {
                    Glass(Modifier.fillMaxWidth().padding(bottom = 12.dp).appear(1), strong = true, onClick = { onOpenRecap(month) }) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Lucide.Sparkles, c.accent, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Your ${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} recap", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                Text("See how the month went", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                            Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            state.carryPrompt?.let { left ->
                item {
                    val prev = state.month.minusMonths(1).month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                    val next = state.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                    Glass(Modifier.fillMaxWidth().padding(bottom = 12.dp).appear(1), strong = true) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Lucide.PiggyBank, c.positive, size = 44.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("$prev ended with ${money.format(left)} left", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                    Text("Add it to $next's budget?", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("No thanks", { viewModel.answerCarry(false) }, Modifier.weight(1f))
                                PrimaryButton("Carry over", { viewModel.answerCarry(true) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { TodayCard(state, onOpenBudget, Modifier.appear(1)) }

            state.balance?.let { balance ->
                item {
                    Glass(Modifier.fillMaxWidth().padding(top = 12.dp).appear(2), onClick = onOpenAccounts) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Lucide.Wallet, c.textPrimary, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                if (state.accounts.size > 1) {
                                    AccountSwitch(state.accounts, state.selectedAccount, viewModel::showAccount)
                                } else {
                                    Text("Total balance", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                                }
                                RollingText(money.formatWhole(balance), MaterialTheme.typography.headlineSmall, if (balance < 0) c.negative else c.textPrimary)
                                state.forecast?.let { f ->
                                    val end = state.month.atEndOfMonth().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                                    Text(
                                        "About ${money.formatWhole(f)} by $end",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (f < 0) c.negative else c.textSecondary,
                                    )
                                }
                            }
                            Text(
                                if (state.accountCount == 1) "1 account" else "${state.accountCount} accounts",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (state.smsSuggestions.isNotEmpty()) {
                item { SectionHeader("Detected payments", Modifier.appear(2)) }
                item {
                    Glass(Modifier.fillMaxWidth().appear(2)) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            state.smsSuggestions.take(5).forEachIndexed { i, s ->
                                if (i > 0) RowDivider()
                                ListRow(
                                    title = s.merchant.ifBlank { "Bank payment" },
                                    subtitle = java.time.LocalDate.ofEpochDay(s.epochDay).friendlyLabel() + " · " +
                                        s.sender.ifBlank { if (s.source == SmsSuggestion.SOURCE_EMAIL) "email" else "bank SMS" },
                                    leading = {
                                        IconTile(if (s.source == SmsSuggestion.SOURCE_EMAIL) Lucide.Mail else Lucide.Smartphone, c.textPrimary, size = 42.dp)
                                    },
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(money.format(s.amountMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                            Spacer(Modifier.width(10.dp))
                                            RoundButton(Lucide.X, "Dismiss", { viewModel.dismissSms(s.id) }, size = 34.dp, tint = c.textTertiary)
                                        }
                                    },
                                    onClick = { onReviewSms(s) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .appear(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = "Spent this month",
                        value = money.formatWhole(state.monthSpent),
                        icon = Lucide.ArrowUpRight,
                        iconTint = c.negative,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenActivity(TxType.EXPENSE, state.selectedAccount?.id) },
                    )
                    StatTile(
                        label = "Income",
                        value = money.formatWhole(state.monthIncome),
                        icon = Lucide.ArrowDownLeft,
                        iconTint = c.positive,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenActivity(TxType.INCOME, state.selectedAccount?.id) },
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item { SectionHeader("Upcoming", Modifier.appear(3), action = "Manage", onAction = { onOpenSubscriptions(false) }) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.appear(3)) {
                        items(state.upcoming, key = { it.id }) { r -> UpcomingCard(r) { onOpenSubscriptions(r.type == TxType.INCOME) } }
                    }
                }
            }

            if (state.goals.isNotEmpty()) {
                item { SectionHeader("Saving for", Modifier.appear(4), action = "All goals", onAction = onOpenGoals) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.appear(4)) {
                        items(state.goals, key = { it.id }) { g -> GoalMiniCard(g) { onOpenGoal(g.id) } }
                    }
                }
            }

            item { SectionHeader("Recent", Modifier.appear(5), action = if (state.recent.isNotEmpty()) "See all" else null, onAction = { onOpenActivity(null, state.selectedAccount?.id) }) }
            item {
                Glass(Modifier.fillMaxWidth().appear(5)) {
                    if (state.recent.isEmpty()) {
                        EmptyState(Lucide.Wallet, "Nothing logged yet", "Tap the + button below to add your first expense.")
                    } else {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            state.recent.forEachIndexed { i, item ->
                                if (i > 0) RowDivider()
                                TransactionRow(item, onClick = { onOpenTransaction(item.id) }, subtitle = item.date.friendlyLabel())
                            }
                        }
                    }
                }
            }
        }
        // Soft fade under the status bar so scrolling content doesn't collide with the clock.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(c.background.copy(alpha = 0.85f), Color.Transparent))),
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
private fun TodayCard(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val budget = state.today
    Glass(modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(32.dp), onClick = onClick) {
        Column(Modifier.padding(24.dp)) {
            val over = budget != null && budget.leftToday < 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        budget == null -> "Spent today"
                        over -> "Over today's budget"
                        else -> "Safe to spend today"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            RollingText(
                text = money.formatWhole(if (budget == null) state.spentToday else abs(budget.leftToday)),
                style = MaterialTheme.typography.displayMedium,
                color = if (over) c.negative else c.textPrimary,
            )
            Spacer(Modifier.height(18.dp))
            if (budget != null) {
                ProgressLine(budget.usedFraction)
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        "${money.formatWhole(state.spentToday)} of ${money.formatWhole(budget.dailyLimit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${state.daysLeft} days left", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            } else {
                Text(
                    "Set a budget to see a daily allowance",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun UpcomingCard(r: RecurringItem, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Glass(Modifier.width(156.dp), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            CategoryIcon(r.categoryEmoji, r.categoryColor, size = 36.dp)
            Spacer(Modifier.height(12.dp))
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.toRecurring().nextChargeDate().friendlyLabel(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(
                (if (r.type == TxType.INCOME) "+" else "") + money.format(r.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = if (r.type == TxType.INCOME) c.positive else c.textPrimary,
            )
        }
    }
}

@Composable
private fun GoalMiniCard(g: GoalWithSaved, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Glass(Modifier.sharedContainer("goal-${g.id}").width(200.dp), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(g.emoji, g.color, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(g.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Text(money.format(g.savedMinor), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text("of ${money.format(g.targetMinor)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            ProgressLine(g.fraction, color = Color(g.color), height = 5.dp)
        }
    }
}

/** "Total balance ⌄" or the chosen account: tap to show one account's money, like switching wallets. */
@Composable
private fun AccountSwitch(accounts: List<AccountWithBalance>, selected: AccountWithBalance?, onSelect: (Long?) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(CircleShape).pressable({ open = true }, pressedScale = 0.95f).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected?.name ?: "Total balance", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, contentDescription = "Choose account", tint = c.textSecondary, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("All accounts", color = c.textPrimary)
                        Text(money.formatWhole(accounts.sumOf { it.balanceMinor }), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                },
                leadingIcon = { Icon(Lucide.Wallet, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (selected == null) {
                    { Icon(Lucide.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(a.name, color = c.textPrimary)
                            Text(money.formatWhole(a.balanceMinor), style = MaterialTheme.typography.bodySmall, color = if (a.balanceMinor < 0) c.negative else c.textSecondary)
                        }
                    },
                    leadingIcon = { Icon(CategoryIcons[a.icon], contentDescription = null, tint = Color(a.color), modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (a.id == selected?.id) {
                        { Icon(Lucide.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(a.id)
                        open = false
                    },
                )
            }
        }
    }
}
