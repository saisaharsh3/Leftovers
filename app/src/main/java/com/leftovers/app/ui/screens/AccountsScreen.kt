package com.leftovers.app.ui.screens

import com.leftovers.app.data.TxType
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.Account
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.AccountWithBalance
import com.leftovers.app.data.Palette
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.Transfer
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
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

/** Where an account's balance comes from, so a surprising number can be traced. */
data class BalanceBreakdown(
    val opening: Long,
    val income: Long,
    val spent: Long,
    val transfersIn: Long,
    val transfersOut: Long,
    val entries: Int,
    /** Entries dated before the account was added, whose effect the opening balance may already include. */
    val olderCount: Int,
    val olderNet: Long,
) {
    val balance: Long get() = opening + income - spent + transfersIn - transfersOut
}

class AccountsViewModel(
    private val repository: AccountRepository,
    private val settings: SettingsRepository,
    transactions: TransactionRepository,
) : ViewModel() {
    val allTransactions: StateFlow<List<TransactionItem>> =
        transactions.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun breakdown(account: Account, items: List<TransactionItem>, transferList: List<Transfer>): BalanceBreakdown {
        val mine = items.filter { it.accountId == account.id }
        val addedOn = java.time.Instant.ofEpochMilli(account.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val older = mine.filter { it.epochDay < addedOn }
        return BalanceBreakdown(
            opening = account.openingMinor,
            income = mine.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
            spent = mine.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
            transfersIn = transferList.filter { it.toAccountId == account.id }.sumOf { it.amountMinor },
            transfersOut = transferList.filter { it.fromAccountId == account.id }.sumOf { it.amountMinor },
            entries = mine.size,
            olderCount = older.size,
            olderNet = older.sumOf { if (it.type == TxType.INCOME) it.amountMinor else -it.amountMinor },
        )
    }

    val accounts: StateFlow<List<AccountWithBalance>> =
        repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transfers: StateFlow<List<Transfer>> =
        repository.transfers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val defaultAccountId: StateFlow<Long> =
        settings.settings.map { it.defaultAccountId }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1L)

    fun save(account: Account, makeDefault: Boolean) {
        viewModelScope.launch {
            val newId = repository.saveAccount(account)
            val id = if (account.id == 0L) newId else account.id
            if (makeDefault && id > 0) settings.setDefaultAccount(id)
        }
    }

    fun delete(account: Account, onBlocked: (Int) -> Unit) {
        viewModelScope.launch {
            val used = repository.deleteAccount(account)
            if (used > 0) onBlocked(used)
        }
    }

    fun moveEntries(from: Long, to: Long, onDone: (List<Long>) -> Unit) {
        viewModelScope.launch { onDone(repository.moveEntries(from, to)) }
    }

    fun undoMove(ids: List<Long>, backTo: Long) {
        viewModelScope.launch { repository.setAccount(ids, backTo) }
    }

    fun transfer(from: Long, to: Long, amount: Long, note: String) {
        viewModelScope.launch {
            repository.saveTransfer(Transfer(fromAccountId = from, toAccountId = to, amountMinor = amount, epochDay = LocalDate.now().toEpochDay(), note = note))
        }
    }

    fun deleteTransfer(transfer: Transfer) {
        viewModelScope.launch { repository.deleteTransfer(transfer) }
    }
}

private val accountIcons = listOf("banknote", "landmark", "wallet", "credit-card", "smartphone", "piggy-bank", "coins", "building", "briefcase", "trending-up")

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val defaultId by viewModel.defaultAccountId.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Account?>(null) }
    var transferring by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Account?>(null) }

    GlassScreen(
        title = "Accounts",
        onBack = onBack,
        snackbar = snackbar,
        bottomBar = {
            Row(
                Modifier.frosted().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("Transfer", { transferring = true }, Modifier.weight(1f), icon = Lucide.Repeat)
                PrimaryButton(
                    "New account",
                    { editing = Account(name = "", icon = "wallet", color = Palette.IRIS) },
                    Modifier.weight(1f),
                    icon = Lucide.Plus,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Total across accounts", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        RollingText(money.format(accounts.sumOf { it.balanceMinor }), MaterialTheme.typography.displaySmall, c.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Starts from each account's opening balance", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                    }
                }
            }
            item { SectionHeader("Your accounts", Modifier.appear(1)) }
            item {
                Glass(Modifier.fillMaxWidth().appear(1)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        accounts.forEachIndexed { i, a ->
                            if (i > 0) RowDivider()
                            ListRow(
                                title = a.name,
                                subtitle = if (a.id == defaultId) "Default for new entries" else null,
                                leading = { CategoryIcon(a.icon, a.color, size = 42.dp) },
                                trailing = {
                                    Text(
                                        money.format(a.balanceMinor),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (a.balanceMinor < 0) c.negative else c.textPrimary,
                                    )
                                },
                                onClick = { editing = a.toAccount() },
                            )
                        }
                    }
                }
            }
            if (transfers.isNotEmpty()) {
                item { SectionHeader("Recent transfers", Modifier.appear(2)) }
                item {
                    Glass(Modifier.fillMaxWidth().appear(2)) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            transfers.take(10).forEachIndexed { i, t ->
                                if (i > 0) RowDivider(inset = 16.dp)
                                val from = accounts.find { it.id == t.fromAccountId }?.name ?: "?"
                                val to = accounts.find { it.id == t.toAccountId }?.name ?: "?"
                                ListRow(
                                    title = "$from → $to",
                                    subtitle = listOf(LocalDate.ofEpochDay(t.epochDay).friendlyLabel(), t.note).filter { it.isNotBlank() }.joinToString(" · "),
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(money.format(t.amountMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                            Icon(
                                                Lucide.X, contentDescription = "Delete transfer", tint = c.textTertiary,
                                                modifier = Modifier.padding(start = 10.dp).size(18.dp).pressable({ viewModel.deleteTransfer(t) }),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { account ->
        val all by viewModel.allTransactions.collectAsStateWithLifecycle()
        AccountEditor(
            initial = account,
            breakdown = if (account.id != 0L) viewModel.breakdown(account, all, transfers) else null,
            isDefault = account.id == defaultId,
            onDismiss = { editing = null },
            onSave = { a, makeDefault ->
                viewModel.save(a, makeDefault)
                editing = null
            },
            onMoveEntries = if (account.id != 0L && accounts.size >= 2) {
                {
                    moving = account
                    editing = null
                }
            } else {
                null
            },
            onDelete = if (account.id != 0L && account.id != defaultId) {
                {
                    viewModel.delete(account) { used ->
                        scope.launch { snackbar.showSnackbar("${account.name} has $used entries, so it can't be deleted") }
                    }
                    editing = null
                }
            } else {
                null
            },
        )
    }

    moving?.let { source ->
        MoveEntriesSheet(
            source = source,
            targets = accounts.filter { it.id != source.id },
            onDismiss = { moving = null },
        ) { target ->
            moving = null
            viewModel.moveEntries(source.id, target.id) { ids ->
                scope.launch {
                    if (ids.isEmpty()) {
                        snackbar.showSnackbar("${source.name} had no entries to move")
                    } else {
                        val result = snackbar.showSnackbar(
                            "Moved ${ids.size} ${if (ids.size == 1) "entry" else "entries"} to ${target.name}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoMove(ids, source.id)
                    }
                }
            }
        }
    }

    if (transferring && accounts.size >= 2) {
        TransferSheet(accounts, onDismiss = { transferring = false }) { from, to, amount, note ->
            viewModel.transfer(from, to, amount, note)
            transferring = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(transferring, accounts.size) {
        if (transferring && accounts.size < 2) {
            transferring = false
            snackbar.showSnackbar("Add a second account to transfer between them")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditor(
    initial: Account,
    breakdown: BalanceBreakdown?,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (Account, Boolean) -> Unit,
    onMoveEntries: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val c = LocalAppColors.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var icon by rememberSaveable { mutableStateOf(initial.icon) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var opening by rememberSaveable { mutableStateOf(if (initial.openingMinor != 0L) AmountInput.fromMinor(initial.openingMinor) else "") }
    var makeDefault by rememberSaveable { mutableStateOf(isDefault) }

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(icon, color, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Text(if (initial.id == 0L) "New account" else "Edit account", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            }
            GlassTextField(name, { name = it.take(24) }, placeholder = "e.g. HDFC, GPay, Credit card", label = "Name")
            MoneyField(opening, { opening = it }, label = "Current balance when you start tracking")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accountIcons.forEach { key ->
                    val selected = key == icon
                    val bg by animateColorAsState(if (selected) Color(color).copy(alpha = 0.22f) else c.glass, label = "accIcon")
                    Box(
                        Modifier.size(46.dp).pressable({ icon = key }, pressedScale = 0.9f).clip(RoundedCornerShape(15.dp)).background(bg)
                            .border(1.dp, if (selected) Color(color) else c.borderBottom, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(CategoryIcons[key], contentDescription = key, tint = if (selected) Color(color) else c.textSecondary, modifier = Modifier.size(22.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.all.take(8).forEach { col ->
                    Box(
                        Modifier.size(28.dp).pressable({ color = col }, pressedScale = 0.85f).clip(CircleShape).background(Color(col))
                            .then(if (col == color) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier),
                    )
                }
            }
            Chip(
                if (makeDefault) "Default for new entries" else "Make this the default",
                onClick = { makeDefault = !makeDefault },
                icon = if (makeDefault) Lucide.CircleCheck else Lucide.Plus,
                selected = makeDefault,
            )
            PrimaryButton(
                "Save",
                { onSave(initial.copy(name = name.trim(), icon = icon, color = color, openingMinor = AmountInput.toMinor(opening) ?: 0L), makeDefault) },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            )
            if (breakdown != null) BalanceBreakdownCard(breakdown, initial.createdAt)
            if (onMoveEntries != null) {
                SecondaryButton("Move its entries to another account", onMoveEntries, Modifier.fillMaxWidth(), icon = Lucide.Repeat)
            }
            if (onDelete != null) {
                Text("Delete account", style = MaterialTheme.typography.labelLarge, color = c.negative, modifier = Modifier.align(Alignment.CenterHorizontally).pressable(onDelete))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferSheet(
    accounts: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long, String) -> Unit,
) {
    val c = LocalAppColors.current
    var from by rememberSaveable { mutableStateOf(accounts[0].id) }
    var to by rememberSaveable { mutableStateOf(accounts[1].id) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val minor = AmountInput.toMinor(amount) ?: 0L

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Move money", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text("Transfers between your own accounts don't count as spending or income.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text("From", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> Chip(a.name, onClick = { from = a.id; if (to == a.id) to = accounts.first { it.id != a.id }.id }, icon = CategoryIcons[a.icon], iconTint = Color(a.color), selected = from == a.id) }
            }
            Text("To", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.filter { it.id != from }.forEach { a -> Chip(a.name, onClick = { to = a.id }, icon = CategoryIcons[a.icon], iconTint = Color(a.color), selected = to == a.id) }
            }
            MoneyField(amount, { amount = it }, label = "Amount", large = true)
            GlassTextField(note, { note = it.take(60) }, placeholder = "e.g. ATM withdrawal", label = "Note (optional)")
            PrimaryButton("Transfer", { onSave(from, to, minor, note.trim()) }, Modifier.fillMaxWidth(), enabled = minor > 0 && from != to)
        }
    }
}

/** Picks where every entry of [source] should go, e.g. before deleting an account. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveEntriesSheet(
    source: Account,
    targets: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    onMove: (AccountWithBalance) -> Unit,
) {
    val c = LocalAppColors.current
    var to by rememberSaveable { mutableStateOf(targets.first().id) }
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Move entries from ${source.name}", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "Every expense and income logged in ${source.name} moves to the account you pick, and both balances update. Transfers stay as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Text("Move to", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { a -> Chip(a.name, onClick = { to = a.id }, icon = CategoryIcons[a.icon], iconTint = Color(a.color), selected = to == a.id) }
            }
            PrimaryButton("Move entries", { targets.find { it.id == to }?.let(onMove) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BalanceBreakdownCard(b: BalanceBreakdown, createdAt: Long) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    @Composable
    fun line(label: String, amount: Long, sign: String, color: Color = c.textPrimary) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text("$sign${money.format(amount)}", style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("How this balance adds up", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            line("Opening balance", b.opening, "")
            line("Income (${b.entries} entries in total)", b.income, "+ ", c.positive)
            line("Spending", b.spent, "− ", c.negative)
            if (b.transfersIn > 0) line("Transfers in", b.transfersIn, "+ ")
            if (b.transfersOut > 0) line("Transfers out", b.transfersOut, "− ")
            RowDivider(inset = 0.dp)
            Spacer(Modifier.height(4.dp))
            line("Balance", b.balance, "")
            if (b.olderCount > 0) {
                val added = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().friendlyLabel()
                Spacer(Modifier.height(8.dp))
                Text(
                    "${b.olderCount} ${if (b.olderCount == 1) "entry is" else "entries are"} dated before you added this account ($added), " +
                        "changing it by ${money.format(b.olderNet)}. If the opening balance you typed already included ${if (b.olderCount == 1) "it" else "them"}, " +
                        "${if (b.olderCount == 1) "it is" else "they are"} counted twice: set the opening balance to what the account held before ${if (b.olderCount == 1) "it" else "them"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warning,
                )
            }
        }
    }
}
