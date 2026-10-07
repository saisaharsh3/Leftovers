package com.leftovers.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.Category
import com.leftovers.app.data.DailyBudget
import com.leftovers.app.data.MonthBudget
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.data.dailyBudget
import com.leftovers.app.data.pendingExpenses
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Gauge
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.ProgressLine
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.StatTile
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.label
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

data class CategoryBudgetRow(val category: Category, val spent: Long)

data class BudgetsUiState(
    val month: YearMonth = YearMonth.now(),
    val plan: BudgetPlan = BudgetPlan(),
    val overallBudget: Long = 0,
    val breakdown: MonthBudget = MonthBudget(0, 0, 0),
    val totalSpent: Long = 0,
    val daysLeft: Int = 1,
    val today: DailyBudget? = null,
    val pendingSubscriptions: Long = 0,
    val goalsMonthly: Long = 0,
    val rows: List<CategoryBudgetRow> = emptyList(),
)

class BudgetsViewModel(
    private val repository: TransactionRepository,
    settings: SettingsRepository,
    planning: PlanningRepository,
) : ViewModel() {
    private val month = YearMonth.now()

    val state: StateFlow<BudgetsUiState> = combine(
        settings.settings,
        repository.categories,
        repository.allTransactions,
        planning.recurring,
        planning.goals,
    ) { s, categories, all, recurring, goals ->
        val items = all.filter { YearMonth.from(it.date) == month }
        val breakdown = s.plan.breakdownFor(month, all)
        val budget = breakdown.total
        val pending = recurring.pendingExpenses(month)
        val spentByCategory = items.filter { it.type == TxType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        BudgetsUiState(
            month = month,
            plan = s.plan,
            overallBudget = budget,
            breakdown = breakdown,
            totalSpent = items.totalOf(TxType.EXPENSE),
            daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1,
            today = dailyBudget(budget, items, pending),
            pendingSubscriptions = pending,
            goalsMonthly = goals.filter { !it.reached }.sumOf { it.monthlyNeeded() },
            rows = categories.filter { it.type == TxType.EXPENSE }
                .map { CategoryBudgetRow(it, spentByCategory[it.id] ?: 0L) }
                // Categories with a limit first, then the ones you spend most on.
                .sortedWith(compareByDescending<CategoryBudgetRow> { it.category.budgetMinor != null }.thenByDescending { it.spent }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun setCategoryBudget(categoryId: Long, minor: Long?) {
        viewModelScope.launch { repository.setCategoryBudget(categoryId, minor) }
    }
}

@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenGoals: () -> Unit,
    viewModel: BudgetsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Category?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    GlassScreen(
        title = "Budget",
        subtitle = state.month.label(),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 40.dp),
        ) {
            if (!state.plan.isSet) {
                item {
                    Glass(Modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(32.dp)) {
                        Column(Modifier.padding(24.dp)) {
                            IconTile(Lucide.Target, c.accent, size = 48.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("No budget yet", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text("Category limits still work on their own, but a budget gives you a daily allowance.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                            Spacer(Modifier.height(20.dp))
                            PrimaryButton("Create budget", onEditPlan, Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                item {
                    val left = state.overallBudget - state.totalSpent
                    val used = if (state.overallBudget > 0) state.totalSpent.toFloat() / state.overallBudget else 1f
                    // Tapping the gauge opens the budget editor.
                    Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp), onClick = onEditPlan) {
                        // A small pencil says the card opens the budget editor.
                        Icon(
                            Lucide.Pencil,
                            contentDescription = "Edit budget",
                            tint = c.textSecondary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).size(18.dp),
                        )
                        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Gauge(used, Modifier.size(240.dp), thickness = 16.dp) {
                                Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (left >= 0) "Left" else "Over by", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                                    RollingText(money.format(abs(left)), MaterialTheme.typography.displaySmall, if (left >= 0) c.textPrimary else c.negative)
                                    Text("Spent ${money.format(state.totalSpent)} of ${money.format(state.overallBudget)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
                                }
                            }
                            val b = state.breakdown
                            if (b.income > 0 || b.carried > 0) {
                                Text(
                                    buildString {
                                        append("${money.format(b.planned)} plan")
                                        if (b.income > 0) append(" + ${money.format(b.income)} extra income")
                                        if (b.carried > 0) append(" + ${money.format(b.carried)} carried over")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(top = 12.dp).appear(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val today = state.today
                        StatTile("Daily", money.formatWhole(today?.dailyLimit ?: 0), Modifier.weight(1f))
                        StatTile(
                            if ((today?.leftToday ?: 0) < 0) "Over today" else "Left today",
                            money.formatWhole(abs(today?.leftToday ?: 0)),
                            Modifier.weight(1f),
                        )
                        StatTile("Days left", state.daysLeft.toString(), Modifier.weight(1f))
                    }
                }
            }

            if (state.pendingSubscriptions > 0 || state.goalsMonthly > 0) {
                item { SectionHeader("Set aside", Modifier.appear(2)) }
                item {
                    Glass(Modifier.fillMaxWidth().appear(2)) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            if (state.pendingSubscriptions > 0) {
                                ListRow(
                                    "Bills still due this month",
                                    subtitle = "Already removed from your daily allowance",
                                    leading = { IconTile(Lucide.Repeat, c.textPrimary, size = 42.dp) },
                                    trailing = { Text(money.format(state.pendingSubscriptions), style = MaterialTheme.typography.titleSmall, color = c.textPrimary) },
                                    onClick = onOpenSubscriptions,
                                )
                            }
                            if (state.pendingSubscriptions > 0 && state.goalsMonthly > 0) RowDivider()
                            if (state.goalsMonthly > 0) {
                                ListRow(
                                    "Savings goals",
                                    subtitle = "Leave this unspent to stay on track",
                                    leading = { IconTile(Lucide.PiggyBank, c.textPrimary, size = 42.dp) },
                                    trailing = { Text("${money.format(state.goalsMonthly)}/mo", style = MaterialTheme.typography.titleSmall, color = c.textPrimary) },
                                    onClick = onOpenGoals,
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Category limits", Modifier.appear(3)) }
            item {
                Glass(Modifier.fillMaxWidth().appear(3)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        state.rows.forEachIndexed { i, row ->
                            if (i > 0) RowDivider()
                            CategoryLimitRow(row) { editing = row.category }
                        }
                    }
                }
            }
        }
    }

    editing?.let { category ->
        AmountDialog(
            title = category.name,
            initialMinor = category.budgetMinor,
            onDismiss = { editing = null },
            onSave = {
                viewModel.setCategoryBudget(category.id, it)
                if (it != null) askForNotificationsIfNeeded()
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryLimitRow(row: CategoryBudgetRow, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val budget = row.category.budgetMinor
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(row.category.emoji, row.category.color, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.category.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
                Text(
                    if (budget != null) "${money.format(row.spent)} of ${money.format(budget)}" else "No limit · tap to set",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            Text(money.format(row.spent), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        if (budget != null && budget > 0) {
            Spacer(Modifier.height(10.dp))
            ProgressLine(row.spent.toFloat() / budget, Modifier.padding(start = 56.dp), height = 4.dp)
        }
    }
}
