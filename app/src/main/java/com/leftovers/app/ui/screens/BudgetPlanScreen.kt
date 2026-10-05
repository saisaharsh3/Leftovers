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
import com.leftovers.app.data.BudgetMode
import com.leftovers.app.data.BudgetPlan
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
) : ViewModel() {
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
            loaded = true
        }
    }

    fun plan(): BudgetPlan = BudgetPlan(
        mode = mode,
        monthlyMinor = AmountInput.toMinor(monthlyText) ?: 0,
        yearlyMinor = AmountInput.toMinor(yearlyText) ?: 0,
        split = split,
        custom = customText.mapNotNull { (m, t) -> AmountInput.toMinor(t)?.let { m to it } }.toMap(),
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
                    .frosted()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
            ) {
                PrimaryButton("Save plan", { saveAndClose() }, Modifier.fillMaxWidth(), enabled = plan.isSet)
                if (viewModel.wasSet) {
                    Text(
                        "Remove budget",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.negative,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 14.dp)
                            .pressable({ viewModel.clear(onBack) }),
                    )
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
                    { if (it == BudgetMode.MONTHLY) "Monthly" else "Yearly" },
                    { viewModel.mode = it }, Modifier.fillMaxWidth(),
                )
            }
            item {
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
