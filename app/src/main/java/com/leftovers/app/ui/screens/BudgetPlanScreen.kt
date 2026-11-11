package com.leftovers.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.periodName
import com.leftovers.app.data.BudgetMode
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.CarryMode
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.YearSplit
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class BudgetPlanViewModel(
    private val settings: SettingsRepository,
    repository: TransactionRepository,
    accounts: com.leftovers.app.data.AccountRepository,
) : ViewModel() {
    val accountList: StateFlow<List<com.leftovers.app.data.AccountWithBalance>> =
        accounts.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** An envelope (#42): only entries with this tag and/or from this account count. */
    var envelope by mutableStateOf(false)
    var rangeTagText by mutableStateOf("")
    var rangeAccount by mutableStateOf<Long?>(null)
    var loaded by mutableStateOf(false)
        private set
    var wasSet by mutableStateOf(false)
        private set
    var mode by mutableStateOf(BudgetMode.MONTHLY)
    var monthlyText by mutableStateOf("")
    var yearlyText by mutableStateOf("")
    var split by mutableStateOf(YearSplit.EVEN)
    /** Custom per-month amounts as typed, keyed by month number. */
    val customText = mutableStateMapOf<Int, String>()
    var carry by mutableStateOf(CarryMode.ASK)
    var extraIncome by mutableStateOf(false)
    var startDay by mutableStateOf(1)
    var rangeText by mutableStateOf("")
    var buckets by mutableStateOf(false)
    var bucketIncomeText by mutableStateOf("")
    var bucketPicker by mutableStateOf(false)
    var periodText by mutableStateOf("")
    var periodDays by mutableStateOf(14)
    var periodFrom by mutableStateOf<java.time.LocalDate>(java.time.LocalDate.now())
    var rangeFrom by mutableStateOf<java.time.LocalDate>(java.time.LocalDate.now())
    var rangeTo by mutableStateOf<java.time.LocalDate>(java.time.LocalDate.now().plusDays(13))
    private var carryFrom: YearMonth? = null
    private var carryChoices: Map<YearMonth, Boolean> = emptyMap()

    val transactions: StateFlow<List<TransactionItem>> =
        repository.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val plan = settings.settings.first().plan
            wasSet = plan.isSet
            mode = plan.mode
            monthlyText = plan.monthlyMinor.takeIf { it > 0 }?.let(AmountInput::fromMinor).orEmpty()
            yearlyText = plan.yearlyMinor.takeIf { it > 0 }?.let(AmountInput::fromMinor).orEmpty()
            split = plan.split
            plan.custom.forEach { (m, v) -> customText[m] = AmountInput.fromMinor(v) }
            carry = plan.carry
            extraIncome = plan.extraIncome
            startDay = plan.startDay
            rangeText = plan.rangeMinor.takeIf { it > 0 }?.let(AmountInput::fromMinor).orEmpty()
            buckets = plan.buckets
            bucketIncomeText = plan.bucketIncome.takeIf { it > 0 }?.let(AmountInput::fromMinor).orEmpty()
            bucketPicker = plan.bucketPicker
            plan.rangeFrom?.let { rangeFrom = it }
            envelope = plan.rangeTag != null || plan.rangeAccount != null
            rangeTagText = plan.rangeTag.orEmpty()
            rangeAccount = plan.rangeAccount
            periodText = plan.periodMinor.takeIf { it > 0 }?.let(AmountInput::fromMinor).orEmpty()
            periodDays = plan.periodDays
            plan.periodFrom?.let { periodFrom = it }
            plan.rangeTo?.let { rangeTo = it }
            carryFrom = plan.carryFrom
            carryChoices = plan.carryChoices
            loaded = true
        }
    }

    fun plan(): BudgetPlan = BudgetPlan(
        mode = mode,
        monthlyMinor = AmountInput.toMinor(monthlyText) ?: 0,
        yearlyMinor = AmountInput.toMinor(yearlyText) ?: 0,
        split = split,
        custom = customText.mapNotNull { (m, t) -> AmountInput.toMinor(t)?.let { m to it } }.toMap(),
        carry = carry,
        carryFrom = carryFrom,
        carryChoices = carryChoices,
        extraIncome = extraIncome,
        startDay = startDay,
        rangeMinor = AmountInput.toMinor(rangeText) ?: 0,
        rangeFrom = rangeFrom,
        rangeTag = rangeTagText.trim().removePrefix("#").lowercase().takeIf { envelope && it.isNotEmpty() },
        rangeAccount = rangeAccount.takeIf { envelope },
        periodMinor = AmountInput.toMinor(periodText) ?: 0,
        periodDays = periodDays,
        periodFrom = periodFrom,
        rangeTo = rangeTo,
        buckets = buckets,
        bucketIncome = AmountInput.toMinor(bucketIncomeText) ?: 0,
        bucketPicker = bucketPicker,
    )

    fun fillEvenly() {
        val yearly = AmountInput.toMinor(yearlyText) ?: return
        (1..12).forEach { customText[it] = AmountInput.fromMinor(yearly / 12) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.savePlan(plan())
            onDone()
        }
    }

    fun clear(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.savePlan(BudgetPlan())
            onDone()
        }
    }
}

@Composable
fun BudgetPlanScreen(
    onBack: () -> Unit,
    viewModel: BudgetPlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onBack() }

    fun saveAndClose() = viewModel.save {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onBack()
        }
    }

    val plan = viewModel.plan()
    val thisMonth = YearMonth.now()

    GlassScreen(
        title = "Budget plan",
        onBack = onBack,
        bottomBar = {
            Column(
                Modifier
                    .frosted(fadeTop = true)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 16.dp),
            ) {
                var askRemove by androidx.compose.runtime.remember { mutableStateOf(false) }
                PrimaryButton("Save plan", { saveAndClose() }, Modifier.fillMaxWidth(), enabled = plan.isSet)
                if (viewModel.wasSet) {
                    Text(
                        "Remove budget",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.negative,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 14.dp)
                            .pressable({ askRemove = true }),
                    )
                    // Asked first: the whole plan goes, with its start day, carry-over and 50/30/20 choices.
                    if (askRemove) {
                        GlassAlertDialog(
                            onDismissRequest = { askRemove = false },
                            title = { Text("Remove your budget?") },
                            text = { Text("Safe to spend and budget alerts stop until you set one again. Your entries stay as they are.", color = c.textSecondary) },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = { askRemove = false; viewModel.clear(onBack) }) { Text("Remove", color = c.negative) }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { askRemove = false }) { Text("Cancel", color = c.textSecondary) }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (!viewModel.loaded) return@GlassScreen
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SegmentedToggle(
                    BudgetMode.entries, viewModel.mode,
                    { when (it) { BudgetMode.MONTHLY -> "Monthly"; BudgetMode.YEARLY -> "Yearly"; BudgetMode.PERIOD -> "Weeks"; BudgetMode.RANGE -> "Dates" } },
                    { viewModel.mode = it }, Modifier.fillMaxWidth(),
                )
            }
            if (viewModel.mode == BudgetMode.RANGE) item {
                RangeCard(viewModel)
            }
            if (viewModel.mode == BudgetMode.PERIOD) item {
                PeriodCard(viewModel, plan)
            }
            if (viewModel.mode == BudgetMode.MONTHLY || viewModel.mode == BudgetMode.YEARLY) item {
                val yearly = viewModel.mode == BudgetMode.YEARLY
                Glass(Modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(30.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        MoneyField(
                            value = if (yearly) viewModel.yearlyText else viewModel.monthlyText,
                            onValueChange = { if (yearly) viewModel.yearlyText = it else viewModel.monthlyText = it },
                            label = if (yearly) "Budget for ${thisMonth.year}" else "Budget every month",
                            large = true,
                        )
                        AnimatedVisibility(plan.isSet, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            val monthBudget = plan.budgetFor(thisMonth, transactions)
                            Row(Modifier.padding(top = 14.dp, start = 4.dp)) {
                                Text(
                                    "${thisMonth.month.full()}: ${money.format(monthBudget)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${money.format(monthBudget / thisMonth.lengthOfMonth())} a day",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            if (viewModel.mode == BudgetMode.MONTHLY || (viewModel.mode == BudgetMode.YEARLY && viewModel.split != YearSplit.SMART)) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Money left at the end of a month", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when (viewModel.carry) {
                                    CarryMode.ASK -> "When a month ends, Home asks whether to add what's left to the next month."
                                    CarryMode.ALWAYS -> "What's left is always added to the next month."
                                    CarryMode.NEVER -> "Every month starts fresh with its planned amount."
                                } + " Overspending is never taken away.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            SegmentedToggle(CarryMode.entries, viewModel.carry, { it.label }, { viewModel.carry = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (viewModel.mode == BudgetMode.MONTHLY || viewModel.mode == BudgetMode.YEARLY) item {
                // When the budget month starts: the 1st, or payday (#34).
                var choosing by androidx.compose.runtime.remember { mutableStateOf(false) }
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp).pressable({ choosing = true }), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Budget month starts on", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            val d = viewModel.startDay
                            Text(
                                if (d == 1) "The 1st: budgets follow calendar months. Pick payday to run from one to the next."
                                else {
                                    // The budget month today falls in: before the start day, it began last month.
                                    val today = java.time.LocalDate.now()
                                    val from = (if (today.dayOfMonth >= d) today else today.minusMonths(1)).withDayOfMonth(d)
                                    val dayMonth = java.time.format.DateTimeFormatter.ofPattern("d MMM")
                                    "The ${ordinal(d)}: the budget you're in runs ${from.format(dayMonth)} to ${from.plusMonths(1).minusDays(1).format(dayMonth)}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Box {
                            Text(ordinal(viewModel.startDay), style = MaterialTheme.typography.titleMedium, color = c.accent)
                            androidx.compose.material3.DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }, modifier = Modifier.height(360.dp)) {
                                (1..28).forEach { day ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(ordinal(day)) }, onClick = { viewModel.startDay = day; choosing = false })
                                }
                            }
                        }
                    }
                }
            }

            item {
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Extra income adds to the budget", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (viewModel.extraIncome) "Gifts, bonuses and other income in categories that add to the budget raise that month's budget. Choose which in Categories."
                                else "Your budget stays the amount you set, whatever comes in. Extra income is left for saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.Switch(
                            checked = viewModel.extraIncome,
                            onCheckedChange = com.leftovers.app.ui.components.toggleHaptic({ viewModel.extraIncome = it }),
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = c.accent,
                                checkedThumbColor = c.onAccent,
                                uncheckedTrackColor = c.glass,
                                uncheckedBorderColor = c.borderTop,
                                uncheckedThumbColor = c.textSecondary,
                            ),
                        )
                    }
                }
            }

            item {
                // 50/30/20 (#39): needs, wants and savings as shares of income.
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("50/30/20", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Half of your income for needs, 30% for wants and 20% for savings. Budgets shows how much of each is used.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.material3.Switch(
                                checked = viewModel.buckets,
                                onCheckedChange = com.leftovers.app.ui.components.toggleHaptic({ viewModel.buckets = it }),
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedTrackColor = c.accent,
                                    checkedThumbColor = c.onAccent,
                                    uncheckedTrackColor = c.glass,
                                    uncheckedBorderColor = c.borderTop,
                                    uncheckedThumbColor = c.textSecondary,
                                ),
                            )
                        }
                        if (viewModel.buckets) {
                            MoneyField(viewModel.bucketIncomeText, { viewModel.bucketIncomeText = it }, label = "Monthly income (empty: what you log, or your budget)")
                            val income = AmountInput.toMinor(viewModel.bucketIncomeText) ?: 0L
                            if (income > 0) {
                                val money = com.leftovers.app.util.LocalMoney.current
                                Text(
                                    "Needs ${money.formatWhole(income / 2)} · Wants ${money.formatWhole(income * 3 / 10)} · Savings ${money.formatWhole(income / 5)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textPrimary,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            Text(
                                "Which categories are needs or wants: Settings → General → Categories.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textTertiary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            // Off: all categories in one row, as always.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Pick Needs, Wants or Savings first", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "When adding an expense, choose the group, then one of its categories.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                androidx.compose.material3.Switch(
                                    checked = viewModel.bucketPicker,
                                    onCheckedChange = com.leftovers.app.ui.components.toggleHaptic({ viewModel.bucketPicker = it }),
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedTrackColor = c.accent,
                                        checkedThumbColor = c.onAccent,
                                        uncheckedTrackColor = c.glass,
                                        uncheckedBorderColor = c.borderTop,
                                        uncheckedThumbColor = c.textSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (viewModel.mode == BudgetMode.YEARLY) {
                item { SectionHeader("Split across months") }
                items(YearSplit.entries) { option ->
                    SplitOption(option, selected = viewModel.split == option) { viewModel.split = option }
                }
                if (viewModel.split == YearSplit.CUSTOM) {
                    item {
                        val diff = plan.yearlyMinor - plan.customTotal
                        val tone = if (diff == 0L) c.positive else c.warning
                        Glass(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Assigned ${money.format(plan.customTotal)} of ${money.format(plan.yearlyMinor)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = c.textPrimary,
                                    )
                                    Text(
                                        when {
                                            diff > 0 -> "${money.format(diff)} left to assign"
                                            diff < 0 -> "${money.format(-diff)} over the yearly budget"
                                            else -> "Balanced"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tone,
                                    )
                                }
                                Text(
                                    "Fill evenly",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textPrimary,
                                    modifier = Modifier.pressable(viewModel::fillEvenly),
                                )
                            }
                        }
                    }
                    items((1..12).toList(), key = { "m$it" }) { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Month.of(m).full(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (m == thisMonth.monthValue) c.accent else c.textPrimary,
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                            )
                            MoneyField(
                                value = viewModel.customText[m].orEmpty(),
                                onValueChange = { viewModel.customText[m] = it },
                                modifier = Modifier.width(190.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Month.full(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
private fun SplitOption(option: YearSplit, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val ring by animateColorAsState(if (selected) c.accent else c.textTertiary, label = "ring")
    Glass(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        strong = selected,
        onClick = onClick,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.accent else Color.Transparent)
                    .border(1.5.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Lucide.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(option.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(option.description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

/**
 * A budget for every so many days (#45): how much, how long a period is (1, 2 or 4 weeks, or a number of days), and a
 * payday it starts on. Each period starts fresh.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PeriodCard(viewModel: BudgetPlanViewModel, plan: BudgetPlan) {
    val c = LocalAppColors.current
    val money = com.leftovers.app.util.LocalMoney.current
    var picking by androidx.compose.runtime.remember { mutableStateOf(false) }
    var customDays by androidx.compose.runtime.remember { mutableStateOf(viewModel.periodDays.takeIf { it !in listOf(7, 14, 28) }?.toString() ?: "") }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")
    val amount = AmountInput.toMinor(viewModel.periodText) ?: 0L
    Glass(Modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoneyField(viewModel.periodText, { viewModel.periodText = it }, label = "Budget " + periodName(viewModel.periodDays), large = true)
            Text("Every", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "Week", 14 to "2 weeks", 28 to "4 weeks").forEach { (d, label) ->
                    com.leftovers.app.ui.components.Chip(label, { viewModel.periodDays = d; customDays = "" }, selected = viewModel.periodDays == d && customDays.isEmpty())
                }
            }
            // Any other length, in days.
            GlassTextField(
                customDays,
                { t -> customDays = t.filter(Char::isDigit).take(3); customDays.toIntOrNull()?.takeIf { it in 1..366 }?.let { viewModel.periodDays = it } },
                placeholder = "Or a number of days",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.glass).pressable({ picking = true }).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Starts on ${viewModel.periodFrom.format(fmt)}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Text("A payday · tap to change", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
            if (plan.isSet) {
                val now = plan.cycleOf(java.time.LocalDate.now())
                Text(
                    "Now: ${now.start.format(fmt)} to ${now.end.format(fmt)} · ${money.format(amount / viewModel.periodDays.coerceAtLeast(1))} a day",
                    style = MaterialTheme.typography.labelLarge, color = c.textPrimary, modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(
                "Safe to spend and the budget follow each period, and each one starts fresh.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    if (picking) {
        val utc = java.time.ZoneOffset.UTC
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = viewModel.periodFrom.atStartOfDay(utc).toInstant().toEpochMilli())
        GlassDatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.periodFrom = java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate() }
                    picking = false
                }) { Text("Done", color = c.textPrimary) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel", color = c.textSecondary) } },
        ) {
            androidx.compose.material3.DatePicker(state, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}

/** A budget for dates of the user's own: from, to, and how much (#34). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RangeCard(viewModel: BudgetPlanViewModel) {
    val c = LocalAppColors.current
    val money = com.leftovers.app.util.LocalMoney.current
    var picking by androidx.compose.runtime.remember { mutableStateOf(false) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val days = (viewModel.rangeTo.toEpochDay() - viewModel.rangeFrom.toEpochDay() + 1).toInt()
    val amount = AmountInput.toMinor(viewModel.rangeText) ?: 0L
    Glass(Modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoneyField(viewModel.rangeText, { viewModel.rangeText = it }, label = "Budget for these dates", large = true)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.glass).pressable({ picking = true }).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${viewModel.rangeFrom.format(fmt)} to ${viewModel.rangeTo.format(fmt)}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Text("$days ${if (days == 1) "day" else "days"} · tap to change", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
            if (amount > 0 && days > 0) {
                Text("${money.format(amount / days)} a day", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            }
            // An envelope: only what's chosen for it counts (#42).
            Text("What counts", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            SegmentedToggle(listOf(false, true), viewModel.envelope, { if (it) "Only chosen" else "All spending" }, { viewModel.envelope = it }, Modifier.fillMaxWidth())
            if (viewModel.envelope) {
                GlassTextField(
                    viewModel.rangeTagText, { viewModel.rangeTagText = it.filter { ch -> !ch.isWhitespace() }.take(24) },
                    placeholder = "Entries tagged, like #trip", modifier = Modifier.fillMaxWidth(),
                )
                val accountList by viewModel.accountList.collectAsStateWithLifecycle()
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.leftovers.app.ui.components.Chip("Any account", { viewModel.rangeAccount = null }, selected = viewModel.rangeAccount == null)
                    accountList.forEach { a ->
                        com.leftovers.app.ui.components.Chip(
                            a.name, { viewModel.rangeAccount = a.id }, icon = com.leftovers.app.ui.icons.CategoryIcons[a.icon],
                            iconTint = androidx.compose.ui.graphics.Color(a.color), selected = viewModel.rangeAccount == a.id,
                        )
                    }
                }
                val tag = viewModel.rangeTagText.trim().removePrefix("#")
                Text(
                    when {
                        tag.isEmpty() && viewModel.rangeAccount == null -> "Choose a tag or an account. Until then, all spending counts."
                        else -> "Only expenses " + listOfNotNull(
                            "tagged #$tag".takeIf { tag.isNotEmpty() },
                            accountList.firstOrNull { it.id == viewModel.rangeAccount }?.let { "from ${it.name}" },
                        ).joinToString(" and ") + " use it up. Rent, bills and the rest leave it be."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(
                "Safe to spend and the budget follow these dates. When they end, set new ones.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    if (picking) {
        val utc = java.time.ZoneOffset.UTC
        val state = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = viewModel.rangeFrom.atStartOfDay(utc).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = viewModel.rangeTo.atStartOfDay(utc).toInstant().toEpochMilli(),
        )
        GlassDatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                val ok = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
                androidx.compose.material3.TextButton(enabled = ok, onClick = {
                    viewModel.rangeFrom = java.time.Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(utc).toLocalDate()
                    viewModel.rangeTo = java.time.Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(utc).toLocalDate()
                    picking = false
                }) { Text("Done", color = if (ok) c.textPrimary else c.textTertiary) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel", color = c.textSecondary) } },
        ) {
            androidx.compose.material3.DateRangePicker(
                state,
                modifier = Modifier.weight(1f),
                title = { Text("Budget dates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, top = 20.dp)) },
                colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}
