package com.leftovers.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.Palette
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.ceilDiv
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.StatTile
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.components.sharedContainer
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy")
private fun String.asMonthLabel(): String = YearMonth.parse(this).format(monthYear)

class GoalsViewModel(private val planning: PlanningRepository) : ViewModel() {
    val goals: StateFlow<List<GoalWithSaved>?> =
        planning.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the goal; for a new goal, [alreadySaved] becomes its first deposit. */
    fun save(goal: Goal, alreadySaved: Long) {
        viewModelScope.launch {
            val newId = planning.saveGoal(goal)
            if (goal.id == 0L && alreadySaved > 0) {
                planning.saveDeposit(GoalDeposit(goalId = newId, amountMinor = alreadySaved, epochDay = LocalDate.now().toEpochDay()))
            }
        }
    }
}

class GoalDetailViewModel(savedState: SavedStateHandle, private val planning: PlanningRepository) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: -1L

    val goal: StateFlow<GoalWithSaved?> =
        planning.goals.map { list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val deposits: StateFlow<List<GoalDeposit>> =
        planning.deposits(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addDeposit(amountMinor: Long) {
        viewModelScope.launch {
            planning.saveDeposit(GoalDeposit(goalId = id, amountMinor = amountMinor, epochDay = LocalDate.now().toEpochDay()))
        }
    }

    fun deleteDeposit(deposit: GoalDeposit) {
        viewModelScope.launch { planning.deleteDeposit(deposit) }
    }

    fun save(goal: Goal) {
        viewModelScope.launch { planning.saveGoal(goal) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            goal.value?.let { planning.deleteGoal(it.toGoal()) }
            onDone()
        }
    }
}

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    viewModel: GoalsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var creating by rememberSaveable { mutableStateOf(false) }

    GlassScreen(
        title = "Savings goals",
        onBack = onBack,
        bottomBar = {
            Box(Modifier.frosted().navigationBarsPadding().padding(16.dp)) {
                PrimaryButton("New goal", { creating = true }, Modifier.fillMaxWidth(), icon = Lucide.Plus)
            }
        },
    ) { padding ->
        val list = goals ?: return@GlassScreen
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Lucide.PiggyBank,
                        "Save for something you want",
                        "A new laptop, a trip, a bike. Set the price and when you want it, and you'll see how much to put aside each month.",
                    )
                }
            } else {
                item {
                    val active = list.filter { !it.reached }
                    Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                        Column(Modifier.padding(22.dp)) {
                            Text("Saved so far", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            RollingText(money.format(list.sumOf { it.savedMinor }), MaterialTheme.typography.displaySmall, c.textPrimary)
                            if (active.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Put aside ${money.format(active.sumOf { it.monthlyNeeded() })} this month to stay on track",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                            }
                        }
                    }
                }
                list.forEachIndexed { i, goal ->
                    item(key = goal.id) { GoalCard(goal, Modifier.appear(i + 1)) { onOpenGoal(goal.id) } }
                }
            }
        }
    }

    if (creating) {
        GoalEditor(
            initial = null,
            onDismiss = { creating = false },
            onSave = { goal, saved ->
                viewModel.save(goal, saved)
                creating = false
            },
        )
    }
}

@Composable
private fun GoalCard(goal: GoalWithSaved, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Glass(modifier.sharedContainer("goal-${goal.id}").fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(goal.fraction, Color(goal.color), size = 64.dp) {
                Icon(CategoryIcons[goal.emoji], contentDescription = null, tint = Color(goal.color), modifier = Modifier.size(24.dp).align(Alignment.Center))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
                Text("${money.format(goal.savedMinor)} of ${money.format(goal.targetMinor)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                StatusPill(goal)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatusPill(goal: GoalWithSaved) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val behind = goal.expectedByNow() - goal.savedMinor
    val (tone, text) = when {
        goal.reached -> c.positive to "Goal reached"
        behind > 0 -> c.warning to "${money.format(goal.monthlyNeeded())}/mo · behind"
        else -> c.positive to "${money.format(goal.monthlyNeeded())}/mo · on track"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = tone,
        modifier = Modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ProgressRing(fraction: Float, color: Color, size: Dp, thickness: Dp = 5.dp, content: @Composable BoxScope.() -> Unit) {
    val c = LocalAppColors.current
    val animated by animateFloatAsState(fraction, spring(dampingRatio = 0.9f, stiffness = 40f), label = "goalRing")
    Box(Modifier.size(size)) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val d = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(c.glassStrong, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            drawArc(color, -90f, 360f * animated, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    viewModel: GoalDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val deposits by viewModel.deposits.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var moneyDialog by rememberSaveable { mutableStateOf<Int?>(null) } // +1 add, -1 withdraw

    val g = goal
    GlassScreen(
        title = g?.name.orEmpty(),
        onBack = onBack,
        actions = {
            RoundButton(Lucide.Pencil, "Edit goal", { editing = true })
            RoundButton(Lucide.Trash2, "Delete goal", { confirmDelete = true }, tint = c.negative)
        },
    ) { padding ->
        if (g == null) return@GlassScreen
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Glass(Modifier.sharedContainer("goal-${g.id}").fillMaxWidth(), strong = true, shape = RoundedCornerShape(32.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(g.fraction, Color(g.color), size = 190.dp, thickness = 12.dp) {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(CategoryIcons[g.emoji], contentDescription = null, tint = Color(g.color), modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(6.dp))
                                RollingText(money.format(g.savedMinor), MaterialTheme.typography.headlineMedium, c.textPrimary)
                                Text("of ${money.format(g.targetMinor)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("Add money", { moneyDialog = 1 }, Modifier.weight(1f), icon = Lucide.Plus)
                            SecondaryButton("Withdraw", { moneyDialog = -1 }, Modifier.weight(1f), icon = Lucide.Minus)
                        }
                    }
                }
            }
            item {
                Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        if (g.reached) "Status" else "Save each month",
                        if (g.reached) "Reached" else money.format(g.monthlyNeeded()),
                        Modifier.weight(1f),
                        footnote = if (g.reached) null else "≈ ${money.format(ceilDiv(g.monthlyNeeded(), 30))} a day",
                    )
                    StatTile("Time left", "${g.monthsLeft()} months", Modifier.weight(1f), footnote = "Target ${g.targetMonth.asMonthLabel()}")
                }
            }
            if (!g.reached) {
                item {
                    val behind = g.expectedByNow() - g.savedMinor
                    Glass(Modifier.fillMaxWidth().appear(2)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (behind > 0) Lucide.Info else Lucide.CircleCheck,
                                contentDescription = null,
                                tint = if (behind > 0) c.warning else c.positive,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (behind > 0) {
                                    "${money.format(behind)} behind plan. Adding a little extra this month gets you back on track."
                                } else {
                                    "On track. Keep saving ${money.format(g.monthlyNeeded())} a month to reach it by ${g.targetMonth.asMonthLabel()}."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary,
                            )
                        }
                    }
                }
            }
            if (deposits.isNotEmpty()) {
                item { SectionHeader("History", Modifier.appear(3)) }
                item {
                    Glass(Modifier.fillMaxWidth().appear(3)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            deposits.forEachIndexed { i, d ->
                                if (i > 0) RowDivider(inset = 16.dp)
                                Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (d.amountMinor >= 0) "Added" else "Withdrawn", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                        Text(LocalDate.ofEpochDay(d.epochDay).friendlyLabel(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                    }
                                    Text(
                                        (if (d.amountMinor >= 0) "+" else "−") + money.format(abs(d.amountMinor)),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (d.amountMinor >= 0) c.positive else c.negative,
                                    )
                                    RoundButton(Lucide.X, "Remove entry", { viewModel.deleteDeposit(d) }, size = 36.dp, tint = c.textTertiary, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    moneyDialog?.let { sign ->
        AmountDialog(
            title = if (sign > 0) "Add money" else "Withdraw",
            initialMinor = null,
            label = "Amount",
            onDismiss = { moneyDialog = null },
            onSave = { amount ->
                if (amount != null) viewModel.addDeposit(amount * sign)
                moneyDialog = null
            },
        )
    }
    if (editing && g != null) {
        GoalEditor(
            initial = g,
            onDismiss = { editing = false },
            onSave = { goal, _ ->
                viewModel.save(goal)
                editing = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Delete this goal?") },
            text = { Text("Its savings history will be removed too.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Delete", color = c.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditor(
    initial: GoalWithSaved?,
    onDismiss: () -> Unit,
    onSave: (Goal, Long) -> Unit,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val now = YearMonth.now()
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(initial?.emoji ?: CategoryIcons.goalKeys.first()) }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: Palette.IRIS) }
    var targetText by rememberSaveable { mutableStateOf(initial?.targetMinor?.let(AmountInput::fromMinor).orEmpty()) }
    var savedText by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf(initial?.monthsLeft() ?: 12) }
    val target = AmountInput.toMinor(targetText) ?: 0L
    val alreadySaved = initial?.savedMinor ?: (AmountInput.toMinor(savedText) ?: 0L)
    val monthly = ceilDiv((target - alreadySaved).coerceAtLeast(0), months.toLong())
    val targetMonth = now.plusMonths((months - 1).toLong())

    GlassSheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "New savings goal" else "Edit goal", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.goalKeys.forEach { key ->
                    val selected = key == icon
                    val bg by animateColorAsState(if (selected) Color(color).copy(alpha = 0.22f) else c.glass, label = "goalIcon")
                    Box(
                        Modifier
                            .size(46.dp)
                            .pressable({ icon = key }, pressedScale = 0.9f)
                            .clip(RoundedCornerShape(15.dp))
                            .background(bg)
                            .border(1.dp, if (selected) Color(color) else c.borderBottom, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(CategoryIcons[key], contentDescription = key, tint = if (selected) Color(color) else c.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.all.take(8).forEach { col ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .pressable({ color = col }, pressedScale = 0.85f)
                            .clip(CircleShape)
                            .background(Color(col))
                            .then(if (col == color) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier),
                    )
                }
            }
            GlassTextField(name, { name = it.take(30) }, placeholder = "e.g. Gaming laptop", label = "What are you saving for?")
            MoneyField(targetText, { targetText = it }, label = "Target amount")
            if (initial == null) MoneyField(savedText, { savedText = it }, label = "Already saved (optional)")

            Text("I want it in", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Lucide.Minus, "Fewer months", { if (months > 1) months-- })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$months month${if (months == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text("by ${targetMonth.format(monthYear)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                RoundButton(Lucide.Plus, "More months", { if (months < 120) months++ })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 12, 24).forEach { m ->
                    val selected = months == m
                    val bg by animateColorAsState(if (selected) c.accent else c.glass, label = "quickMonths")
                    Text(
                        if (m < 12) "$m months" else "${m / 12} year${if (m > 12) "s" else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) c.onAccent else c.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .pressable({ months = m }, pressedScale = 0.94f)
                            .clip(CircleShape)
                            .background(bg)
                            .padding(vertical = 11.dp),
                    )
                }
            }

            if (target > 0) {
                Glass(Modifier.fillMaxWidth(), strong = true, shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Save every month", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                            RollingText(money.format(monthly), MaterialTheme.typography.headlineMedium, c.textPrimary)
                        }
                        Text("≈ ${money.format(ceilDiv(monthly, 30))}\na day", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, textAlign = TextAlign.End)
                    }
                }
            }

            PrimaryButton(
                if (initial == null) "Create goal" else "Save",
                {
                    val base = initial?.toGoal() ?: Goal(name = "", emoji = icon, color = color, targetMinor = 0, targetMonth = "")
                    onSave(
                        base.copy(name = name.trim(), emoji = icon, color = color, targetMinor = target, targetMonth = targetMonth.toString()),
                        AmountInput.toMinor(savedText) ?: 0L,
                    )
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && target > 0,
            )
        }
    }
}
