package com.leftovers.app.ui.screens

import com.leftovers.app.ui.components.Haptics
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
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
import com.leftovers.app.data.openBalances
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
    /** Everyone with money still owed either way, largest first. */
    val owed: List<com.leftovers.app.data.PersonBalance> = emptyList(),
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
)

class HomeViewModel(
    repository: TransactionRepository,
    private val settings: SettingsRepository,
    private val planning: PlanningRepository,
    private val sms: SmsRepository,
    accounts: AccountRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        combine(repository.allTransactions, accounts.accounts, ::Pair),
        settings.settings,
        combine(planning.recurring, planning.debts, ::Pair),
        planning.goals,
        sms.suggestions,
    ) { (all, accountList), s, (recurring, debts), goals, suggestions ->
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
            owed = debts.openBalances().filter { it.netMinor != 0L },
            recent = shownAll.take(6),
            smsSuggestions = suggestions,
            recapMonth = month.minusMonths(1).takeIf { prev ->
                todayDate.dayOfMonth <= 10 && s.recapSeen != prev.toString() && all.any { YearMonth.from(it.date) == prev }
            },
            carryPrompt = s.plan.pendingCarry(month, all),
            balance = selected?.balanceMinor ?: accountList.filterNot { it.outOfTotal }.sumOf { it.balanceMinor },
            accountCount = accountList.size,
            // Bills and salary aren't tied to an account, so the forecast is only for all money together.
            forecast = if (selected == null) forecastMonthEnd(accountList.filterNot { it.outOfTotal }.sumOf { it.balanceMinor }, all, recurring, todayDate) else null,
            accounts = accountList,
            selectedAccount = selected,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun answerCarry(carry: Boolean) {
        viewModelScope.launch { settings.setCarryChoice(YearMonth.now().minusMonths(1), carry) }
    }

    fun setSubscriptionActive(item: RecurringItem, active: Boolean) {
        viewModelScope.launch { planning.saveRecurring(item.toRecurring().copy(active = active)) }
    }

    fun settleUp(person: String, amountMinor: Long, accountId: Long?) {
        viewModelScope.launch { planning.settleUp(person, amountMinor, accountId, chosen = true) }
    }

    fun saveDebt(debt: com.leftovers.app.data.Debt) {
        viewModelScope.launch { planning.saveDebt(debt) }
    }

    fun deleteDebt(debt: com.leftovers.app.data.Debt) {
        viewModelScope.launch { planning.deleteDebt(debt) }
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
    onOpenDebts: () -> Unit = {},
    /** Opens Subscriptions on this one: its day in the calendar, and its details. */
    onOpenSubscription: (RecurringItem) -> Unit = { onOpenSubscriptions(it.type == TxType.INCOME) },
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    if (!state.loaded) return
    var owedOpen by remember { mutableStateOf<String?>(null) }
    // The tapped card steps out of the way until its pop-up shrinks back into it.
    var owedHidden by remember { mutableStateOf<String?>(null) }
    var hiddenSubscription by remember { mutableStateOf<Long?>(null) }
    // Where the tapped card was, for the pop-up to grow out of.
    var owedFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var subscriptionFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var editingDebt by remember { mutableStateOf<com.leftovers.app.data.Debt?>(null) }
    var settling by remember { mutableStateOf<String?>(null) }
    val view = androidx.compose.ui.platform.LocalView.current
    var viewingSubscription by remember { mutableStateOf<Long?>(null) }
    val upcomingBounds = remember { mutableMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    // Tapping the balance opens a card with every account, to switch which one Home shows.
    var balanceOpen by remember { mutableStateOf(false) }
    var balanceHidden by remember { mutableStateOf(false) }
    var balanceFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var balanceBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    if (balanceOpen) state.balance?.let { balance ->
        val selected = state.selectedAccount
        MorphCard(from = balanceFrom, onDismiss = { balanceOpen = false; balanceHidden = false }, onReveal = { balanceHidden = false }, onShown = { balanceHidden = true }) { close ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) CategoryIcon(selected.icon, selected.color, size = 52.dp) else IconTile(Lucide.Wallet, c.textPrimary, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(selected?.name ?: "Total balance", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (selected != null) "One of ${state.accountCount} accounts" else if (state.accountCount == 1) "1 account" else "Across ${state.accountCount} accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassStrong, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text("Balance", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                val own = state.selectedAccount?.takeIf { it.currency != null }
                Text(own?.shownBalance(money) ?: money.format(balance), style = MaterialTheme.typography.displaySmall, color = if (own?.shownNegative ?: (balance < 0)) c.negative else c.textPrimary)
                state.forecast?.let { f ->
                    val end = state.month.atEndOfMonth().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                    Text(
                        "About ${money.formatWhole(f)} by $end",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (f < 0) c.negative else c.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // Which account Home shows; the one showing has a tick.
            if (state.accounts.size > 1) {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    val rows = listOf<AccountWithBalance?>(null) + state.accounts
                    rows.forEachIndexed { i, a ->
                        if (i > 0) RowDivider(inset = 50.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable({ viewModel.showAccount(a?.id) }, pressedScale = 0.98f)
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (a != null) CategoryIcon(a.icon, a.color, size = 36.dp) else IconTile(Lucide.Wallet, c.textSecondary, size = 36.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(a?.name ?: "All accounts", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val amount = a?.balanceMinor ?: state.accounts.filterNot { it.outOfTotal }.sumOf { it.balanceMinor }
                            Text(
                                a?.shownBalance(money, whole = true) ?: money.formatWhole(amount),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (a?.shownNegative ?: (amount < 0)) c.negative else c.textPrimary,
                            )
                            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
                                if (a?.id == selected?.id) Icon(Lucide.Check, contentDescription = "Showing", tint = c.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Accounts", { close(onOpenAccounts) }, Modifier.weight(1f))
                PrimaryButton("See activity", { close { onOpenActivity(null, selected?.id) } }, Modifier.weight(1.4f), icon = Lucide.ArrowUpRight)
            }
        }
    }
    // Tapping an entry opens a card about it; Edit there opens the full editor.
    val peek = rememberTransactionPeek()
    TransactionPeekCard(peek, onEdit = onOpenTransaction)
    state.upcoming.firstOrNull { it.id == viewingSubscription }?.let { r ->
        SubscriptionCard(
            r,
            from = subscriptionFrom,
            accountName = state.accounts.firstOrNull { it.id == r.accountId }?.name,
            onDismiss = { viewingSubscription = null; hiddenSubscription = null },
            onReveal = { hiddenSubscription = null },
            onShown = { hiddenSubscription = r.id },
            onEdit = {
                viewingSubscription = null
                onOpenSubscription(r)
            },
            onSetActive = { viewModel.setSubscriptionActive(r, it) },
        )
    }

    // Tapping someone on Money owed: a card in the middle with everything about them, to edit or settle.
    state.owed.firstOrNull { it.person.equals(owedOpen, ignoreCase = true) }?.let { b ->
        val theyOwe = b.netMinor > 0
        val tone = if (theyOwe) c.positive else c.warning
        val entries = b.entries.sortedByDescending { it.epochDay }
        val lent = b.entries.filter { !it.repayment && it.amountMinor > 0 }.sumOf { it.amountMinor }
        val borrowed = b.entries.filter { !it.repayment && it.amountMinor < 0 }.sumOf { -it.amountMinor }
        val paidBack = b.entries.filter { it.repayment }.sumOf { abs(it.amountMinor) }
        // How it adds up, when there's more than one kind of entry.
        val parts = listOfNotNull(
            if (lent > 0) "Lent ${money.format(lent)}" else null,
            if (borrowed > 0) "Borrowed ${money.format(borrowed)}" else null,
            if (paidBack > 0) "Paid back ${money.format(paidBack)}" else null,
        )
        MorphCard(from = owedFrom, onDismiss = { owedOpen = null; owedHidden = null }, onReveal = { owedHidden = null }, onShown = { owedHidden = owedOpen }) { close ->
                    // Who, and since when.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(b.person.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = tone)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.person, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Since " + LocalDate.ofEpochDay(b.entries.minOf { it.epochDay }).friendlyLabel() +
                                    " · ${b.entries.size} ${if (b.entries.size == 1) "entry" else "entries"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                    // The balance, on its own tinted panel.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(tone.copy(alpha = 0.09f))
                            .border(1.dp, tone.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Text(if (theyOwe) "Owes you" else "You owe", style = MaterialTheme.typography.labelLarge, color = tone)
                        Text(money.format(abs(b.netMinor)), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
                        if (parts.size > 1) {
                            Text(
                                parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    // A photo on an entry opens full screen from here.
                    var photo by remember { mutableStateOf<String?>(null) }
                    photo?.let { ReceiptViewer(it, onDismiss = { photo = null }) }
                    // Every entry; tapping one edits it.
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        entries.forEachIndexed { i, d ->
                            if (i > 0) RowDivider(inset = 44.dp)
                            val out = d.amountMinor > 0
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressable({ close { editingDebt = d } }, pressedScale = 0.98f)
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(c.glass),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (d.repayment) Lucide.Check else if (out) Lucide.ArrowUpRight else Lucide.ArrowDownLeft,
                                        contentDescription = if (d.repayment) "Paid back" else if (out) "Lent" else "Borrowed",
                                        tint = if (d.repayment) c.textSecondary else if (out) c.positive else c.warning,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        d.note.ifBlank { if (d.repayment) "Paid back" else if (out) "Lent" else "Borrowed" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        LocalDate.ofEpochDay(d.epochDay).friendlyLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(money.format(abs(d.amountMinor)), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                    Text(if (d.repayment) "paid back" else if (out) "lent" else "borrowed", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                                }
                            }
                            if (d.photos.isNotEmpty()) {
                                Row(
                                    Modifier.padding(start = 44.dp, bottom = 10.dp).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    d.photos.forEach { path -> ReceiptThumb(path, onOpen = { photo = path }, size = 52.dp) }
                                }
                            }
                        }
                    }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Edit", {
                        // One entry opens right here; several are easier to see on Money owed.
                        close { if (b.entries.size == 1) editingDebt = b.entries[0] else onOpenDebts() }
                    }, Modifier.weight(1f))
                    // Someone who owes you can be reminded, in a message you send from WhatsApp, SMS or any app.
                    if (b.netMinor > 0) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        RoundButton(Lucide.Bell, "Remind ${b.person}", {
                            com.leftovers.app.util.DebtReminder.share(context, com.leftovers.app.util.DebtReminder.message(b, money::format), b.person)
                        }, size = 56.dp)
                    }
                    PrimaryButton("Pay back", { close { settling = b.person } }, Modifier.weight(1.4f), icon = Lucide.Check)
                }
            }
    }
    state.owed.firstOrNull { it.person.equals(settling, ignoreCase = true) }?.let { b ->
        SettleSheet(b, state.accounts, onDismiss = { settling = null }) { amount, account ->
            viewModel.settleUp(b.person, amount, account)
            Haptics.confirm(view)
            settling = null
        }
    }
    editingDebt?.let { debt ->
        DebtEditor(
            initial = debt,
            people = state.owed.map { it.person },
            accounts = state.accounts,
            onDismiss = { editingDebt = null },
            onSave = {
                viewModel.saveDebt(it)
                editingDebt = null
            },
            onDelete = {
                viewModel.deleteDebt(debt)
                editingDebt = null
            },
        )
    }

    // Pull down to run every check now (email, subscriptions due, sync) and reload.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var refreshNote by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(refreshNote) {
        if (refreshNote != null) {
            kotlinx.coroutines.delay(2200)
            refreshNote = null
        }
    }
    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            refreshScope.launch {
                val note = (appContext as com.leftovers.app.LeftoversApp).container.refreshNow()
                refreshing = false
                refreshNote = note
                Haptics.tick(view)
            }
        },
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = pull,
                isRefreshing = refreshing,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = c.accent,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
    ) {
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
                        Text(greeting() + " · " + LocalDate.now().format(greetingDate), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1)
                        Text(state.month.label(), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
                    }
                    RoundButton(Lucide.MessageCircle, "AI assistant", onOpenAssistant)
                    Spacer(Modifier.width(8.dp))
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
                    Glass(
                        Modifier
                            .padding(top = 12.dp)
                            .sharedContainer("accounts")
                            .fillMaxWidth()
                            .appear(2)
                            .trackScreenBounds { balanceBounds = it }
                            .graphicsLayer { alpha = if (balanceHidden) 0f else 1f },
                    ) {
                        Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable({ balanceFrom = balanceBounds; balanceOpen = true }, pressedScale = 0.98f)
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(Lucide.Wallet, c.textPrimary, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(state.selectedAccount?.name ?: "Total balance", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                                val shown = state.selectedAccount?.takeIf { it.currency != null }
                                RollingText(
                                    shown?.shownBalance(money, whole = true) ?: money.formatWhole(balance),
                                    MaterialTheme.typography.headlineSmall,
                                    if (shown?.shownNegative ?: (balance < 0)) c.negative else c.textPrimary,
                                )
                                state.forecast?.let { f ->
                                    val end = state.month.atEndOfMonth().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                                    Text(
                                        "About ${money.formatWhole(f)} by $end",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (f < 0) c.negative else c.textSecondary,
                                    )
                                }
                            }
                            // On narrow phones the balance needs the room.
                            if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 400) {
                                Text(
                                    if (state.accountCount == 1) "1 account" else "${state.accountCount} accounts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                        }
                        // The month's money out and in, under the balance it changes.
                        Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(c.borderTop.copy(alpha = c.borderTop.alpha * 0.6f)))
                        MonthSplit(state, onOpenActivity)
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
                                    title = s.merchant.ifBlank { if (s.isIncome) "Money in" else "Bank payment" },
                                    subtitle = java.time.LocalDate.ofEpochDay(s.epochDay).friendlyLabel() + " · " +
                                        s.sender.ifBlank { if (s.source == SmsSuggestion.SOURCE_EMAIL) "email" else if (s.source == SmsSuggestion.SOURCE_APP) "payment app" else "bank SMS" },
                                    leading = {
                                        IconTile(
                                            when (s.source) {
                                                SmsSuggestion.SOURCE_EMAIL -> Lucide.Mail
                                                SmsSuggestion.SOURCE_APP -> Lucide.Bell
                                                else -> Lucide.Smartphone
                                            },
                                            c.textPrimary,
                                            size = 42.dp,
                                        )
                                    },
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                if (s.isIncome) "+" + money.format(s.amountMinor) else money.format(s.amountMinor),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (s.isIncome) c.positive else c.textPrimary,
                                            )
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

            // Without a balance to show, the month's money out and in keep their own row.
            if (state.balance == null) item {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .appear(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 400) "Spent this month" else "Spent",
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
                    CardStrip(state.upcoming, key = { it.id }, modifier = Modifier.appear(3)) { r, size ->
                        UpcomingCard(r, size ?: Modifier.width(156.dp), hidden = hiddenSubscription == r.id, onBounds = { upcomingBounds[r.id] = it }) { subscriptionFrom = upcomingBounds[r.id]; viewingSubscription = r.id }
                    }
                }
            }

            if (state.owed.isNotEmpty()) {
                item { SectionHeader("Money owed", Modifier.appear(3), action = "See all", onAction = onOpenDebts) }
                item { OwedSummary(state.owed, Modifier.appear(3), open = owedHidden, onOpen = { person, at -> owedFrom = at; owedOpen = person }, onSeeAll = onOpenDebts) }
            }

            if (state.goals.isNotEmpty()) {
                item { SectionHeader("Saving for", Modifier.appear(4), action = "All goals", onAction = onOpenGoals) }
                item {
                    CardStrip(state.goals, key = { it.id }, modifier = Modifier.appear(4)) { g, size -> GoalMiniCard(g, size ?: Modifier.width(200.dp)) { onOpenGoal(g.id) } }
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
                                TransactionRow(item, onClick = { peek.show(item) }, subtitle = item.date.friendlyLabel(), modifier = Modifier.peekSource(peek, item.id))
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
        // What the pull found, for a moment.
        androidx.compose.animation.AnimatedVisibility(
            refreshNote != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.9f),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            Text(
                refreshNote.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, c.borderTop, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
    }
}

/** Today next to the greeting: "Sat, 10 Oct". */
private val greetingDate = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/** Spent and income this month, side by side; each opens Activity filtered to it. */
@Composable
private fun MonthSplit(state: HomeUiState, onOpenActivity: (TxType?, Long?) -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 400
    // The rolling numbers can't be measured ahead, so the divider has a set height rather than matching the row.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SplitHalf(if (wide) "Spent this month" else "Spent", money.formatWhole(state.monthSpent), Lucide.ArrowUpRight, c.negative, Modifier.weight(1f)) {
            onOpenActivity(TxType.EXPENSE, state.selectedAccount?.id)
        }
        Box(Modifier.width(1.dp).height(40.dp).background(c.borderTop.copy(alpha = c.borderTop.alpha * 0.6f)))
        SplitHalf("Income", money.formatWhole(state.monthIncome), Lucide.ArrowDownLeft, c.positive, Modifier.weight(1f)) {
            onOpenActivity(TxType.INCOME, state.selectedAccount?.id)
        }
    }
}

@Composable
private fun SplitHalf(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier.pressable(onClick, pressedScale = 0.96f).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        RollingText(value, MaterialTheme.typography.titleLarge, c.textPrimary)
    }
}

@Composable
private fun TodayCard(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val budget = state.today
    // The one number that matters most sits large on the background itself, not in a card, so nothing competes with it.
    Box(modifier.sharedContainer("budget").fillMaxWidth().pressable(onClick, pressedScale = 0.98f)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            // The month's budget already used up: say so, and by how much, instead of "₹0 of ₹0".
            val usedUp = budget != null && budget.monthUsedUp
            val over = budget != null && (usedUp || budget.leftToday < 0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        budget == null -> "Spent today"
                        usedUp -> "Over this month's budget"
                        over -> "Over today's budget"
                        else -> "Safe to spend today"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(2.dp))
            RollingText(
                text = money.formatWhole(
                    when {
                        budget == null -> state.spentToday
                        usedUp -> abs(budget.monthLeft)
                        else -> abs(budget.leftToday)
                    },
                ),
                style = MaterialTheme.typography.displayLarge,
                color = if (over) c.negative else c.textPrimary,
            )
            Spacer(Modifier.height(14.dp))
            if (budget != null) {
                ProgressLine(budget.usedFraction)
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        if (usedUp) "${money.formatWhole(budget.monthlyBudget - budget.monthLeft)} of ${money.formatWhole(budget.monthlyBudget)} this month"
                        else "${money.formatWhole(state.spentToday)} of ${money.formatWhole(budget.dailyLimit)}",
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

/**
 * A row of small cards that fills the width: one card spans it, two share it equally, and more scroll
 * sideways at their own size. [content] gets the size to give its card, or null for its usual one; a lone card
 * gets [WideCard], and lays itself out in a row so the width isn't left empty.
 */
/** The size a card gets when it's the only one in its row. */
private val WideCard = Modifier.fillMaxWidth()

@Composable
private fun <T> CardStrip(items: List<T>, key: (T) -> Any, modifier: Modifier = Modifier, content: @Composable (T, Modifier?) -> Unit) {
    when (items.size) {
        1 -> Box(modifier.fillMaxWidth()) { content(items[0], WideCard) }
        2 -> Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item -> Box(Modifier.weight(1f)) { content(item, Modifier.fillMaxWidth().fillMaxHeight()) } }
        }
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
            items(items, key = key) { item -> content(item, null) }
        }
    }
}

@Composable
private fun UpcomingCard(
    r: RecurringItem,
    size: Modifier = Modifier.width(156.dp),
    hidden: Boolean = false,
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Glass(size.trackScreenBounds(onBounds).graphicsLayer { alpha = if (hidden) 0f else 1f }, shape = RoundedCornerShape(24.dp), onClick = onClick) {
        val amount = (if (r.type == TxType.INCOME) "+" else "") + money.format(r.amountMinor)
        val tint = if (r.type == TxType.INCOME) c.positive else c.textPrimary
        if (size === WideCard) {
            // On its own: icon, name and date, and the amount across one row.
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(r.categoryEmoji, r.categoryColor, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.toRecurring().nextChargeDate().friendlyLabel(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                }
                Spacer(Modifier.width(12.dp))
                Text(amount, style = MaterialTheme.typography.titleLarge, color = tint)
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                CategoryIcon(r.categoryEmoji, r.categoryColor, size = 36.dp)
                Spacer(Modifier.height(12.dp))
                Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.toRecurring().nextChargeDate().friendlyLabel(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text(amount, style = MaterialTheme.typography.titleMedium, color = tint)
            }
        }
    }
}

/**
 * Money owed on Home: the totals, then the people with the biggest balances, each with what their latest entry
 * was for. Tapping someone offers Edit or Settled.
 */
@Composable
private fun OwedSummary(
    owed: List<com.leftovers.app.data.PersonBalance>,
    modifier: Modifier = Modifier,
    open: String? = null,
    onOpen: (String, androidx.compose.ui.geometry.Rect?) -> Unit,
    onSeeAll: () -> Unit,
) {
    val rows = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val owedToYou = owed.filter { it.netMinor > 0 }.sumOf { it.netMinor }
    val youOwe = owed.filter { it.netMinor < 0 }.sumOf { -it.netMinor }
    val shown = owed.sortedByDescending { abs(it.netMinor) }.take(3)
    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("You're owed", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text(money.format(owedToYou), style = MaterialTheme.typography.titleLarge, color = if (owedToYou > 0) c.positive else c.textPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("You owe", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text(money.format(youOwe), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                }
            }
            shown.forEach { b ->
                val theyOwe = b.netMinor > 0
                val latest = b.entries.maxByOrNull { it.epochDay }
                RowDivider(inset = 16.dp)
                com.leftovers.app.ui.components.ListRow(
                    b.person,
                    subtitle = latest?.let { d ->
                        listOfNotNull(d.note.ifBlank { null }, LocalDate.ofEpochDay(d.epochDay).friendlyLabel()).joinToString(" · ") +
                            if (b.entries.size > 1) " · +${b.entries.size - 1} more" else ""
                    },
                    leading = { IconTile(Lucide.HandCoins, if (theyOwe) c.positive else c.warning, size = 38.dp) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money.format(abs(b.netMinor)), style = MaterialTheme.typography.titleSmall, color = if (theyOwe) c.positive else c.textPrimary)
                            Text(if (theyOwe) "owes you" else "you owe", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    },
                    onClick = { onOpen(b.person, rows[b.person]) },
                    modifier = Modifier.sharedContainer("owed-${b.person.lowercase()}").trackScreenBounds { rows[b.person] = it }
                        .graphicsLayer { alpha = if (b.person.equals(open, ignoreCase = true)) 0f else 1f },
                )
            }
            if (owed.size > shown.size) {
                RowDivider(inset = 16.dp)
                Text(
                    "${owed.size - shown.size} more · See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.fillMaxWidth().pressable(onSeeAll, pressedScale = 0.98f).padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun GoalMiniCard(g: GoalWithSaved, size: Modifier = Modifier.width(200.dp), onClick: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    Glass(Modifier.sharedContainer("goal-${g.id}").then(size), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        if (size === WideCard) {
            // On its own: the goal on the left, how far along on the right, and the bar under both.
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(g.emoji, g.color, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(g.fraction * 100).toInt()}% saved", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.format(g.savedMinor), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        Text("of ${money.format(g.targetMinor)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                ProgressLine(g.fraction, color = Color(g.color), height = 5.dp)
            }
        } else {
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
}

