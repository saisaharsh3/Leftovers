package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.BudgetMode
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.data.dailyBudget
import com.leftovers.app.data.pendingExpenses
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.DockClearance
import com.leftovers.app.ui.components.Gauge
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.ProgressLine
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import kotlin.math.abs

data class PlanUiState(
    val loaded: Boolean = false,
    val plan: BudgetPlan = BudgetPlan(),
    val budget: Long = 0,
    val spent: Long = 0,
    val dailyLimit: Long = 0,
    val recurring: List<RecurringItem> = emptyList(),
    val goals: List<GoalWithSaved> = emptyList(),
)

class PlanViewModel(
    repository: TransactionRepository,
    settings: SettingsRepository,
    planning: PlanningRepository,
) : ViewModel() {
    val state: StateFlow<PlanUiState> = combine(
        repository.allTransactions,
        settings.settings,
        planning.recurring,
        planning.goals,
    ) { all, s, recurring, goals ->
        val month = YearMonth.now()
        val items = all.filter { YearMonth.from(it.date) == month }
        val budget = s.plan.budgetFor(month, all)
        PlanUiState(
            loaded = true,
            plan = s.plan,
            budget = budget,
            spent = items.totalOf(TxType.EXPENSE),
            dailyLimit = dailyBudget(budget, items, recurring.pendingExpenses(month))?.dailyLimit ?: 0,
            recurring = recurring,
            goals = goals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())
}

@Composable
fun PlanScreen(
    onOpenBudget: () -> Unit,
    onEditPlan: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: PlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current

    GlassScreen(title = "Plan", onBack = null) { padding ->
        if (!state.loaded) return@GlassScreen
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = DockClearance),
        ) {
            item {
                if (state.plan.isSet) {
                    BudgetHero(state, onOpenBudget, onEditPlan, Modifier.appear(0))
                } else {
                    Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                        Column(Modifier.padding(24.dp)) {
                            IconTile(Lucide.Target, c.accent, size = 48.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Set a budget", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Choose a monthly amount, or a yearly one split the way you like. You'll get a daily allowance and alerts at 80% and 100%.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textSecondary,
                            )
                            Spacer(Modifier.height(20.dp))
                            PrimaryButton("Create budget", onEditPlan, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item { SectionHeader("Subscriptions", Modifier.appear(1), action = "Manage", onAction = onOpenSubscriptions) }
            item {
                val active = state.recurring.filter { it.active && it.type == TxType.EXPENSE }
                Glass(Modifier.fillMaxWidth().appear(1), onClick = onOpenSubscriptions) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ListRow(
                            title = if (active.isEmpty()) "No subscriptions yet" else "${money.format(active.sumOf { it.amountMinor })} every month",
                            subtitle = if (active.isEmpty()) "Rent, phone, streaming — logged automatically" else "${active.size} active · deducted on their billing day",
                            leading = { IconTile(Lucide.Repeat, c.textPrimary, size = 42.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
                        )
                        active.sortedBy { it.toRecurring().nextChargeDate() }.take(3).forEach { r ->
                            RowDivider()
                            ListRow(
                                title = r.name,
                                subtitle = "Next ${r.toRecurring().nextChargeDate().friendlyLabel()}",
                                leading = { CategoryIcon(r.categoryEmoji, r.categoryColor, size = 42.dp) },
                                trailing = { Text(money.format(r.amountMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary) },
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Savings goals", Modifier.appear(2), action = if (state.goals.isEmpty()) null else "All", onAction = onOpenGoals) }
            if (state.goals.isEmpty()) {
                item {
                    Glass(Modifier.fillMaxWidth().appear(2), onClick = onOpenGoals) {
                        ListRow(
                            title = "Start saving for something",
                            subtitle = "Set a price and a date — we'll tell you the monthly amount",
                            leading = { IconTile(Lucide.PiggyBank, c.textPrimary, size = 42.dp) },
                            trailing = { Icon(Lucide.Plus, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp)) },
                        )
                    }
                }
            } else {
                item {
                    Glass(Modifier.fillMaxWidth().appear(2)) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            state.goals.forEachIndexed { i, g ->
                                if (i > 0) RowDivider()
                                GoalRow(g) { onOpenGoal(g.id) }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Setup", Modifier.appear(3)) }
            item {
                Glass(Modifier.fillMaxWidth().appear(3)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ListRow(
                            "Budget plan",
                            subtitle = planSummary(state.plan),
                            leading = { IconTile(Lucide.SlidersHorizontal, c.textPrimary, size = 42.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
                            onClick = onEditPlan,
                        )
                        RowDivider()
                        ListRow(
                            "Accounts",
                            subtitle = "Cash, bank, cards and transfers",
                            leading = { IconTile(Lucide.Wallet, c.textPrimary, size = 42.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
                            onClick = onOpenAccounts,
                        )
                        RowDivider()
                        ListRow(
                            "Categories",
                            subtitle = "Names, icons and colours",
                            leading = { IconTile(Lucide.Tag, c.textPrimary, size = 42.dp) },
                            trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
                            onClick = onOpenCategories,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun planSummary(plan: BudgetPlan): String {
    val money = LocalMoney.current
    return when {
        !plan.isSet -> "Not set"
        plan.mode == BudgetMode.MONTHLY -> "${money.format(plan.monthlyMinor)} a month"
        else -> "${money.format(plan.yearlyMinor)} a year · ${plan.split.title.lowercase()}"
    }
}

@Composable
private fun BudgetHero(state: PlanUiState, onOpen: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val left = state.budget - state.spent
    val used = if (state.budget > 0) state.spent.toFloat() / state.budget else 1f
    Glass(modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(32.dp), onClick = onOpen) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Gauge(used, Modifier.size(220.dp)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (left >= 0) "Left this month" else "Over budget", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    RollingText(money.format(abs(left)), MaterialTheme.typography.headlineLarge, if (left >= 0) c.textPrimary else c.negative)
                    Text("of ${money.format(state.budget)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Daily allowance", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text(money.formatWhole(state.dailyLimit), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Used", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text("${(used * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
fun GoalRow(g: GoalWithSaved, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(g.emoji, g.color, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
                Text(
                    if (g.reached) "Reached" else "${money.format(g.monthlyNeeded())}/month to stay on track",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money.format(g.savedMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text("${(g.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressLine(g.fraction, Modifier.padding(start = 56.dp), color = Color(g.color), height = 4.dp)
    }
}