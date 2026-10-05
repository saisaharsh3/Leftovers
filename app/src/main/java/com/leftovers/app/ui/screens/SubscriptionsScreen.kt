package com.leftovers.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.Category
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.app.util.label
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class SubscriptionsViewModel(
    private val planning: PlanningRepository,
    repository: TransactionRepository,
    private val sync: suspend () -> Unit,
) : ViewModel() {
    val items: StateFlow<List<RecurringItem>> =
        planning.recurring.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(recurring: Recurring) {
        viewModelScope.launch {
            planning.saveRecurring(recurring)
            sync() // logs this month's charge straight away if it's already due
        }
    }

    fun setActive(item: RecurringItem, active: Boolean) {
        viewModelScope.launch {
            // Resuming shouldn't back-charge the months it was paused for.
            val updated = if (active && !item.active) {
                item.toRecurring().copy(active = true, lastPostedMonth = YearMonth.now().minusMonths(1).toString())
            } else {
                item.toRecurring().copy(active = active)
            }
            planning.saveRecurring(updated)
            sync()
        }
    }

    fun delete(item: RecurringItem) {
        viewModelScope.launch { planning.deleteRecurring(item.toRecurring()) }
    }
}

private data class Template(val name: String, val icon: String, val categoryName: String, val type: TxType = TxType.EXPENSE)

private val templates = listOf(
    Template("Netflix", "tv", "Entertainment"),
    Template("Spotify", "music", "Entertainment"),
    Template("Rent", "house", "Rent"),
    Template("Electricity", "zap", "Bills & Utilities"),
    Template("Mobile plan", "smartphone", "Bills & Utilities"),
    Template("Internet", "wifi", "Bills & Utilities"),
    Template("Gym", "dumbbell", "Health"),
    Template("Salary", "briefcase", "Salary", TxType.INCOME),
)

@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var filter by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Recurring?>(null) }

    val active = items.filter { it.active }
    val monthlyOut = active.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val monthlyIn = active.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }

    fun startNew() {
        categories.firstOrNull { it.type == filter }?.let { first ->
            editing = Recurring(
                name = "",
                amountMinor = 0,
                type = filter,
                categoryId = first.id,
                dayOfMonth = LocalDate.now().dayOfMonth,
                startMonth = YearMonth.now().toString(),
            )
        }
    }

    GlassScreen(
        title = "Subscriptions",
        subtitle = "Logged automatically on their day",
        onBack = onBack,
        bottomBar = {
            Box(
                Modifier
                    .frosted()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                PrimaryButton(
                    if (filter == TxType.EXPENSE) "Add subscription" else "Add recurring income",
                    ::startNew,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Plus,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Going out every month", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        RollingText(money.format(monthlyOut), MaterialTheme.typography.displaySmall, c.textPrimary)
                        if (monthlyIn > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("+${money.format(monthlyIn)} coming in", style = MaterialTheme.typography.bodySmall, color = c.positive)
                        }
                    }
                }
            }

            if (active.isNotEmpty()) {
                item { SectionHeader("${YearMonth.now().label()} schedule", Modifier.appear(1)) }
                item { DeductionCalendar(active, Modifier.appear(1)) }
            }

            item {
                Spacer(Modifier.height(18.dp))
                SegmentedToggle(
                    TxType.entries, filter,
                    { if (it == TxType.EXPENSE) "Payments" else "Income" },
                    { filter = it }, Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            val shown = items.filter { it.type == filter }
            item {
                Glass(Modifier.fillMaxWidth().appear(2)) {
                    if (shown.isEmpty()) {
                        EmptyState(
                            if (filter == TxType.EXPENSE) Lucide.Repeat else Lucide.Briefcase,
                            if (filter == TxType.EXPENSE) "No subscriptions yet" else "No recurring income yet",
                            if (filter == TxType.EXPENSE) {
                                "Add rent, streaming or phone bills. They're logged on their billing day and kept out of your daily allowance."
                            } else {
                                "Add your salary and it will be logged automatically every month."
                            },
                        )
                    } else {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            shown.forEachIndexed { i, item ->
                                if (i > 0) RowDivider()
                                RecurringRow(item, onClick = { editing = item.toRecurring() }, onToggle = { viewModel.setActive(item, it) })
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { recurring ->
        RecurringEditor(
            initial = recurring,
            categories = categories,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = items.firstOrNull { it.id == recurring.id }?.let { item ->
                {
                    viewModel.delete(item)
                    editing = null
                }
            },
        )
    }
}

/** This month's calendar with each billing day marked, so you can see when money leaves. */
@Composable
private fun DeductionCalendar(items: List<RecurringItem>, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val month = YearMonth.now()
    val today = LocalDate.now()
    val byDay = items.groupBy { it.toRecurring().chargeDate(month).dayOfMonth }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<Int?> = List(leading) { null } + (1..month.lengthOfMonth()).toList()
    var selected by rememberSaveable { mutableStateOf(byDay.keys.filter { it >= today.dayOfMonth }.minOrNull() ?: byDay.keys.minOrNull() ?: 1) }

    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (day == null) return@Box
                            val charges = byDay[day].orEmpty()
                            val isSelected = day == selected && charges.isNotEmpty()
                            val past = day < today.dayOfMonth
                            val bg by animateColorAsState(
                                when {
                                    isSelected -> c.accent
                                    charges.isNotEmpty() -> c.glassStrong
                                    else -> Color.Transparent
                                },
                                label = "deductBg",
                            )
                            Column(
                                Modifier
                                    .matchParentSize()
                                    .pressable({ if (charges.isNotEmpty()) selected = day }, pressedScale = 0.9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .then(if (day == today.dayOfMonth && !isSelected) Modifier.border(1.dp, c.textSecondary, RoundedCornerShape(12.dp)) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        isSelected -> c.onAccent
                                        charges.isNotEmpty() -> c.textPrimary
                                        else -> c.textTertiary
                                    },
                                )
                                if (charges.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 3.dp)) {
                                        charges.take(3).forEach { r ->
                                            Box(
                                                Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected -> c.onAccent
                                                            r.type == TxType.INCOME -> c.positive
                                                            past -> c.textTertiary
                                                            else -> Color(r.categoryColor)
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            byDay[selected]?.let { charges ->
                Spacer(Modifier.height(6.dp))
                RowDivider(inset = 0.dp)
                val date = month.atDay(selected)
                Text(
                    if (date < today) "${date.friendlyLabel()} · already logged" else date.friendlyLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                charges.forEach { r ->
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(r.categoryEmoji, r.categoryColor, size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(r.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            (if (r.type == TxType.INCOME) "+" else "−") + money.format(r.amountMinor),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (r.type == TxType.INCOME) c.positive else c.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(item: RecurringItem, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (item.active) 1f else 0.4f)) { CategoryIcon(item.categoryEmoji, item.categoryColor, size = 42.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (item.active) 1f else 0.5f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
            Text(
                if (item.active) "Every ${ordinal(item.dayOfMonth)} · next ${item.toRecurring().nextChargeDate().friendlyLabel()}" else "Paused",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
        Text(
            (if (item.type == TxType.INCOME) "+" else "") + money.format(item.amountMinor),
            style = MaterialTheme.typography.titleSmall,
            color = if (item.type == TxType.INCOME) c.positive else c.textPrimary,
            modifier = Modifier.alpha(if (item.active) 1f else 0.5f),
        )
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = item.active,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.glass,
                uncheckedBorderColor = c.borderTop,
                uncheckedThumbColor = c.textSecondary,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditor(
    initial: Recurring,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = LocalAppColors.current
    val isNew = initial.id == 0L
    val today = LocalDate.now()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var amountText by rememberSaveable { mutableStateOf(if (initial.amountMinor > 0) AmountInput.fromMinor(initial.amountMinor) else "") }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var categoryId by rememberSaveable { mutableStateOf(initial.categoryId) }
    var day by rememberSaveable { mutableStateOf(initial.dayOfMonth) }
    var chargeThisMonth by rememberSaveable { mutableStateOf(true) }
    val amount = AmountInput.toMinor(amountText) ?: 0L
    val dayPassed = day <= today.dayOfMonth

    GlassSheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (isNew) "New recurring entry" else initial.name, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)

            if (isNew) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { t ->
                        Chip(t.name, icon = CategoryIcons[t.icon], iconTint = c.textSecondary, onClick = {
                            name = t.name
                            type = t.type
                            categories.firstOrNull { it.type == t.type && it.name == t.categoryName }?.let { categoryId = it.id }
                        })
                    }
                }
            }

            SegmentedToggle(
                TxType.entries, type,
                { if (it == TxType.EXPENSE) "Payment" else "Income" },
                {
                    if (it != type) {
                        type = it
                        categories.firstOrNull { cat -> cat.type == it }?.let { cat -> categoryId = cat.id }
                    }
                },
                Modifier.fillMaxWidth(),
            )
            GlassTextField(name, { name = it.take(30) }, placeholder = "e.g. Netflix", label = "Name")
            MoneyField(amountText, { amountText = it }, label = "Amount every month")

            Text("Category", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.type == type }.forEach { cat ->
                    Chip(cat.name, icon = CategoryIcons[cat.emoji], iconTint = Color(cat.color), selected = cat.id == categoryId, onClick = { categoryId = cat.id })
                }
            }

            Text(
                "Deducted on the ${if (day == 31) "last day" else ordinal(day)} of every month",
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
            DayOfMonthGrid(selected = day, onSelect = { day = it })

            if (isNew && dayPassed) {
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Log this month's ${if (type == TxType.EXPENSE) "payment" else "income"} now", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(
                                if (day == today.dayOfMonth) "The ${ordinal(day)} is today" else "The ${ordinal(day)} has already passed this month",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Switch(
                            checked = chargeThisMonth,
                            onCheckedChange = { chargeThisMonth = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                        )
                    }
                }
            }

            PrimaryButton(
                "Save",
                {
                    val start = when {
                        !isNew -> initial.startMonth
                        dayPassed && !chargeThisMonth -> YearMonth.now().plusMonths(1).toString()
                        else -> YearMonth.now().toString()
                    }
                    onSave(initial.copy(name = name.trim(), amountMinor = amount, type = type, categoryId = categoryId, dayOfMonth = day, startMonth = start))
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && amount > 0 && categories.any { it.id == categoryId && it.type == type },
            )
            if (onDelete != null) {
                Text(
                    "Delete · past entries stay in your history",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.negative,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .pressable(onDelete),
                )
            }
        }
    }
}

/** Calendar-style grid of days 1–31 for choosing a billing day. */
@Composable
private fun DayOfMonthGrid(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..31).chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1.15f)) {
                            if (day == null) return@Box
                            val isSelected = day == selected
                            val bg by animateColorAsState(if (isSelected) c.accent else Color.Transparent, label = "dom")
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .pressable({ onSelect(day) }, pressedScale = 0.88f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(day.toString(), style = MaterialTheme.typography.labelLarge, color = if (isSelected) c.onAccent else c.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
