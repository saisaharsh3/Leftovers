package com.leftovers.app.ui.screens

import androidx.compose.ui.text.style.TextOverflow
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.AccountWithBalance
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
import androidx.compose.material3.Icon
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
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.SubscriptionSuggestion
import com.leftovers.app.data.findLikelySubscriptions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.leftovers.app.ui.components.RoundButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class SubscriptionsViewModel(
    private val planning: PlanningRepository,
    repository: TransactionRepository,
    accountRepository: AccountRepository,
    private val settings: SettingsRepository,
    private val sync: suspend () -> Unit,
) : ViewModel() {
    /** Null until loaded, so the screen doesn't flash its empty state first. */
    val items: StateFlow<List<RecurringItem>?> =
        planning.recurring.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<AccountWithBalance>> =
        accountRepository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Repeating expenses that look like subscriptions not set up yet. */
    val suggestions: StateFlow<List<SubscriptionSuggestion>> =
        combine(repository.allTransactions, planning.recurring, settings.settings) { all, recurring, s ->
            findLikelySubscriptions(all, recurring, s.dismissedSuggestions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismiss(suggestion: SubscriptionSuggestion) {
        viewModelScope.launch { settings.dismissSuggestion(suggestion.key) }
    }

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
    Template("Freelance", "laptop", "Freelance", TxType.INCOME),
    Template("Rent received", "house", "Other", TxType.INCOME),
    Template("Interest", "landmark", "Investments", TxType.INCOME),
    Template("Dividends", "trending-up", "Investments", TxType.INCOME),
    Template("Pocket money", "gift", "Gifts", TxType.INCOME),
    Template("Pension", "piggy-bank", "Other", TxType.INCOME),
)

@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    /** Open on recurring income instead of payments. */
    startOnIncome: Boolean = false,
    /** A subscription to show straight away, from tapping it on Home; -1 for none. */
    openId: Long = -1L,
    viewModel: SubscriptionsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val loadedItems by viewModel.items.collectAsStateWithLifecycle()
    val items = loadedItems.orEmpty()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var filter by rememberSaveable { mutableStateOf(if (startOnIncome) TxType.INCOME else TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var viewing by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val rowBounds = remember { mutableMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    items.firstOrNull { it.id == viewing }?.let { item ->
        SubscriptionCard(
            item,
            from = viewingFrom,
            accountName = accounts.firstOrNull { it.id == item.accountId }?.name,
            onDismiss = { viewing = null },
            onEdit = {
                viewing = null
                editing = item.toRecurring()
            },
            onSetActive = { viewModel.setActive(item, it) },
        )
    }
    val focus = items.firstOrNull { it.id == openId }
    // Coming from a card on Home: open that one's details once, over its day in the calendar.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focus) {
        if (focus != null && !opened) {
            opened = true
            editing = focus.toRecurring()
        }
    }
    var showAll by rememberSaveable { mutableStateOf(false) }

    val active = items.filter { it.active }
    // Yearly bills count as their monthly share, so the total matches what to set aside each month.
    val monthlyOut = active.filter { it.type == TxType.EXPENSE }.sumOf { it.toRecurring().monthlyShareMinor }
    val monthlyIn = active.filter { it.type == TxType.INCOME }.sumOf { it.toRecurring().monthlyShareMinor }

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
                    .frosted(fadeTop = true)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 16.dp),
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
            if (loadedItems == null) return@LazyColumn
            item {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        // Lead with whichever side the user is looking at; mention the other below.
                        val income = filter == TxType.INCOME
                        Text(if (income) "Coming in every month" else "Going out every month", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        RollingText(money.formatWhole(if (income) monthlyIn else monthlyOut), MaterialTheme.typography.displaySmall, if (income) c.positive else c.textPrimary)
                        val other = if (income) monthlyOut else monthlyIn
                        if (other > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (income) "−${money.formatWhole(other)} going out" else "+${money.formatWhole(other)} coming in",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (income) c.textSecondary else c.positive,
                            )
                        }
                    }
                }
            }

            // One suggestion at a time keeps this quiet.
            suggestions.firstOrNull()?.let { s ->
                item {
                    Spacer(Modifier.height(12.dp))
                    SuggestionCard(
                        s,
                        onAdd = {
                            filter = TxType.EXPENSE
                            editing = Recurring(
                                name = s.name.take(30),
                                amountMinor = s.amountMinor,
                                type = TxType.EXPENSE,
                                categoryId = s.categoryId,
                                dayOfMonth = s.dayOfMonth,
                                startMonth = YearMonth.now().toString(),
                            )
                        },
                        onDismiss = { viewModel.dismiss(s) },
                        modifier = Modifier.appear(1),
                    )
                }
            }

            if (active.isNotEmpty()) {
                item { SectionHeader("Schedule", Modifier.appear(1)) }
                item { DeductionCalendar(active, Modifier.appear(1), focus = focus) }
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

            // Active ones by their next charge, soonest first, then paused ones. Five show; the rest open on request.
            val shown = items.filter { it.type == filter }
                .sortedWith(compareBy<RecurringItem>({ !it.active }, { it.toRecurring().nextChargeDate() }))
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
                            val visible = if (showAll) shown else shown.take(5)
                            visible.forEachIndexed { i, item ->
                                if (i > 0) RowDivider()
                                RecurringRow(item, onClick = { viewingFrom = rowBounds[item.id]; viewing = item.id }, onToggle = { viewModel.setActive(item, it) }, onBounds = { rowBounds[item.id] = it })
                            }
                            if (shown.size > 5) {
                                RowDivider()
                                Text(
                                    if (showAll) "Show less" else "Show all ${shown.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.accent,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().pressable({ showAll = !showAll }).padding(vertical = 14.dp),
                                )
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
            accounts = accounts,
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

/** A calendar with each billing day marked, so you can see when money leaves. Swipe for the months ahead. */
@Composable
private fun DeductionCalendar(items: List<RecurringItem>, modifier: Modifier = Modifier, focus: RecurringItem? = null) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val thisMonth = YearMonth.now()
    // Starts on the month of [focus]'s next charge, when there is one.
    val focusDate = focus?.toRecurring()?.nextChargeDate()
    val startPage = focusDate?.let { (YearMonth.from(it).year - thisMonth.year) * 12 + YearMonth.from(it).monthValue - thisMonth.monthValue }?.coerceIn(0, 11) ?: 0
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = startPage) { 12 }
    val scope = rememberCoroutineScope()
    val shown = thisMonth.plusMonths(pager.currentPage.toLong())
    val goingOut = items.filter { it.type == TxType.EXPENSE && it.toRecurring().isDueIn(shown) }.sumOf { it.amountMinor }

    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(
                    Lucide.ChevronLeft,
                    "Previous month",
                    { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    size = 34.dp,
                    tint = c.textSecondary,
                    enabled = pager.currentPage > 0,
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        shown.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) +
                            if (shown.year != thisMonth.year) " ${shown.year}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                    )
                    Text(
                        if (goingOut > 0) "${money.format(goingOut)} going out" else "Nothing going out",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
                RoundButton(
                    Lucide.ChevronRight,
                    "Next month",
                    { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    size = 34.dp,
                    tint = c.textSecondary,
                    enabled = pager.currentPage < pager.pageCount - 1,
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.pager.HorizontalPager(pager, verticalAlignment = Alignment.Top) { page ->
                val month = thisMonth.plusMonths(page.toLong())
                MonthOfCharges(items, month, Modifier.padding(horizontal = 14.dp), focusDay = focusDate?.takeIf { YearMonth.from(it) == month }?.dayOfMonth)
            }
        }
    }
}

/** One month of [DeductionCalendar]: the days, and what's charged on the selected one. */
@Composable
private fun MonthOfCharges(items: List<RecurringItem>, month: YearMonth, modifier: Modifier = Modifier, focusDay: Int? = null) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val today = LocalDate.now()
    val current = month == YearMonth.from(today)
    val byDay = items.filter { it.toRecurring().isDueIn(month) }.groupBy { it.toRecurring().chargeDate(month).dayOfMonth }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<Int?> = List(leading) { null } + (1..month.lengthOfMonth()).toList()
    var selected by rememberSaveable(month.toString()) {
        mutableStateOf(focusDay ?: (if (current) byDay.keys.filter { it >= today.dayOfMonth }.minOrNull() else null) ?: byDay.keys.minOrNull() ?: 1)
    }

    run {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            val past = current && day < today.dayOfMonth
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
                                    .then(if (current && day == today.dayOfMonth && !isSelected) Modifier.border(1.dp, c.textSecondary, RoundedCornerShape(12.dp)) else Modifier),
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
private fun RecurringRow(item: RecurringItem, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {}) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Row(
        Modifier
            .trackScreenBounds(onBounds)
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
                when {
                    !item.active -> "Paused"
                    item.everyMonths == 12 -> "Yearly · next ${item.toRecurring().nextChargeDate().friendlyLabel()}"
                    item.everyMonths > 1 -> "Every ${item.everyMonths} months · next ${item.toRecurring().nextChargeDate().friendlyLabel()}"
                    else -> "Every ${ordinal(item.dayOfMonth)} · next ${item.toRecurring().nextChargeDate().friendlyLabel()}"
                },
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
    accounts: List<AccountWithBalance>,
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
    val now = YearMonth.now()
    var every by rememberSaveable { mutableStateOf(initial.everyMonths.takeIf { it in cycles } ?: 1) }
    // For bills every few months: the month of the next charge.
    var billMonth by rememberSaveable {
        mutableStateOf((if (initial.everyMonths > 1) YearMonth.from(initial.nextChargeDate()) else now).toString())
    }
    var chargeThisMonth by rememberSaveable { mutableStateOf(true) }
    var skipNext by rememberSaveable { mutableStateOf(false) }
    var accountId by rememberSaveable { mutableStateOf(initial.accountId) }
    val amount = AmountInput.toMinor(amountText) ?: 0L
    // Months that can be picked for the next charge: the coming cycle, or the whole year for yearly bills.
    val monthChoices = (0 until if (every == 12) 12 else every).map { now.plusMonths(it.toLong()) }
    val nextMonth = YearMonth.parse(billMonth).takeIf { it in monthChoices } ?: now
    val dueThisMonth = every == 1 || nextMonth == now
    val dayPassed = dueThisMonth && day <= today.dayOfMonth

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
                    // Payments get bills and streaming; income gets salary, rent received and the like.
                    templates.filter { it.type == type }.forEach { t ->
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
            GlassTextField(name, { name = it.take(30) }, placeholder = if (type == TxType.INCOME) "e.g. Salary" else "e.g. Netflix", label = "Name")
            SegmentedToggle(cycles, every, ::cycleLabel, { every = it }, Modifier.fillMaxWidth())
            MoneyField(
                amountText, { amountText = it },
                label = when (every) {
                    1 -> "Amount every month"
                    12 -> "Amount every year"
                    else -> "Amount every $every months"
                },
            )
            if (every > 1 && amount > 0) {
                Text(
                    "About ${LocalMoney.current.format(amount / every)} a month",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Text("Category", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.type == type }.forEach { cat ->
                    Chip(cat.name, icon = CategoryIcons[cat.emoji], iconTint = Color(cat.color), selected = cat.id == categoryId, onClick = { categoryId = cat.id })
                }
            }

            if (accounts.size > 1) {
                Text(
                    if (type == TxType.INCOME) "Paid into" else "Paid from",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        Chip(a.name, { accountId = a.id }, icon = CategoryIcons[a.icon], iconTint = Color(a.color), selected = accountId == a.id)
                    }
                }
            }

            if (every > 1) {
                Text(if (every == 12) "Month" else "Next charge", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Yearly lists Jan–Dec; shorter cycles list the coming months.
                    monthChoices.sortedBy { if (every == 12) it.monthValue else 0 }.forEach { m ->
                        Chip(m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), { billMonth = m.toString() }, selected = m == nextMonth)
                    }
                }
            }
            val monthName = nextMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            val dayText = if (day == 31) "the last day of" else day.toString()
            Text(
                "${if (type == TxType.INCOME) "Received" else "Deducted"} on " + when (every) {
                    1 -> "the ${if (day == 31) "last day" else ordinal(day)} of every month"
                    12 -> "$dayText $monthName every year"
                    else -> "$dayText $monthName, then every $every months"
                },
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
            DayOfMonthGrid(selected = day, onSelect = { day = it })

            if (isNew && dayPassed) {
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Log ${if (every == 1) "this month's" else "this"} ${if (type == TxType.EXPENSE) "payment" else "income"} now", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
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

            if (!isNew && initial.active) {
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Skip the next one", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(
                                "${initial.nextChargeDate().friendlyLabel()} won't be logged; it carries on after",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Switch(
                            checked = skipNext,
                            onCheckedChange = { skipNext = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                        )
                    }
                }
            }

            PrimaryButton(
                "Save",
                {
                    val start = if (every > 1) {
                        // The chosen next month, unless that one is already logged or the user chose not to log it now.
                        var m = nextMonth
                        val loggedAlready = initial.lastPostedMonth != null && initial.lastPostedMonth >= m.toString()
                        if (loggedAlready || (m == now && dayPassed && isNew && !chargeThisMonth)) m = m.plusMonths(every.toLong())
                        m.toString()
                    } else {
                        when {
                            !isNew && initial.everyMonths == 1 -> initial.startMonth
                            !isNew -> now.toString()
                            dayPassed && !chargeThisMonth -> now.plusMonths(1).toString()
                            else -> now.toString()
                        }
                    }
                    var saved = initial.copy(name = name.trim(), amountMinor = amount, type = type, categoryId = categoryId, dayOfMonth = day, startMonth = start, everyMonths = every, accountId = accountId)
                    if (skipNext) {
                        // Marking the next charge as already logged skips it without touching the schedule.
                        saved = saved.copy(lastPostedMonth = YearMonth.from(saved.nextChargeDate()).toString())
                    }
                    onSave(saved)
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

private val cycles = listOf(1, 3, 6, 12)

private fun cycleLabel(months: Int) = when (months) {
    1 -> "Monthly"
    12 -> "Yearly"
    else -> "$months months"
}

/** "Looks like a subscription": add it with one tap, or say no and it won't ask again. */
@Composable
private fun SuggestionCard(s: SubscriptionSuggestion, onAdd: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Repeat, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Looks like a subscription", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            }
            Text(
                "${s.name} · ${money.format(s.amountMinor)} around the ${ordinal(s.dayOfMonth)} each month",
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Add it", onAdd, selected = true, icon = Lucide.Plus)
                Chip("No", onDismiss)
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

/**
 * A subscription or recurring income as a card in the middle of the screen, like a person on Money owed: what it
 * is, when it's next charged and how much it costs a year, with Edit and Pause or Resume.
 */
@Composable
internal fun SubscriptionCard(
    item: RecurringItem,
    accountName: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    from: androidx.compose.ui.geometry.Rect? = null,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val r = item.toRecurring()
    val income = item.type == TxType.INCOME
    val tone = if (!item.active) c.textSecondary else if (income) c.positive else Color(item.categoryColor)
    val next = if (item.active) r.nextChargeDate() else null
    val days = next?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), it) }
    val cycle = when (item.everyMonths) {
        12 -> "Yearly"
        6 -> "Every 6 months"
        3 -> "Every 3 months"
        else -> "Monthly"
    }
    val started = YearMonth.parse(item.startMonth).let { "${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} ${it.year}" }
    MorphCard(from = from, onDismiss = onDismiss) { close ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(item.categoryEmoji, item.categoryColor, size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(cycle, item.categoryName, accountName?.let { if (income) "into $it" else "from $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
                // When it's next charged, on its own tinted panel.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(tone.copy(alpha = 0.10f))
                        .border(1.dp, tone.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        when {
                            !item.active -> "Paused"
                            income -> "Next payment"
                            else -> "Next charge"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = tone,
                    )
                    Text((if (income) "+" else "") + money.format(item.amountMinor), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
                    Text(
                        if (next == null) "Not logged until you resume it"
                        else next.friendlyLabel() + when (days) {
                            0L -> " · today"
                            1L -> " · tomorrow"
                            else -> " · in $days days"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Label on the left, value on the right, so long amounts and dates never get cut.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.glass).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    SubscriptionFact(if (income) "In a year" else "Costs a year", money.format(r.monthlyShareMinor * 12))
                    RowDivider(inset = 0.dp)
                    SubscriptionFact(
                        if (income) "Paid on" else "Charged on",
                        if (item.everyMonths == 1) "the ${ordinal(item.dayOfMonth)}, monthly" else "the ${ordinal(item.dayOfMonth)}, every ${item.everyMonths} months",
                    )
                    RowDivider(inset = 0.dp)
                    SubscriptionFact("Since", started)
                }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Edit", { close(onEdit) }, Modifier.weight(1f))
                if (item.active) {
                    SecondaryButton("Pause", { onSetActive(false); onDismiss() }, Modifier.weight(1f), icon = Lucide.Pause)
                } else {
                    PrimaryButton("Resume", { onSetActive(true); onDismiss() }, Modifier.weight(1f), icon = Lucide.Play)
                }
            }
        }
}

@Composable
private fun SubscriptionFact(label: String, value: String) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
    }
}
