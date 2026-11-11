package com.leftovers.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.AccountWithBalance
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionPeekViewModel(
    private val repository: TransactionRepository,
    private val accountRepository: AccountRepository,
    settings: com.leftovers.app.data.SettingsRepository,
    private val planning: com.leftovers.app.data.PlanningRepository,
) : ViewModel() {
    /** The same entry again, for today: for things logged often. [onAdded] gets its id, for Undo. */
    fun addAgain(item: TransactionItem, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            onAdded(
                repository.saveTransaction(
                    item.toTransaction().copy(
                        id = 0, epochDay = java.time.LocalDate.now().toEpochDay(), createdAt = System.currentTimeMillis(),
                        receiptPath = null, splitOf = null,
                    ),
                ),
            )
        }
    }

    suspend fun removeAgain(id: Long) {
        repository.getTransaction(id)?.let { repository.removeTransaction(it) }
    }

    /** What people owe for bills split with them. */
    val debts: StateFlow<List<com.leftovers.app.data.Debt>> =
        planning.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A whole bill split with people: their shares go with it, and come back with Undo. */
    fun deleteShares(shares: List<com.leftovers.app.data.Debt>) {
        viewModelScope.launch { shares.forEach { planning.deleteDebt(it) } }
    }

    suspend fun restoreShares(shares: List<com.leftovers.app.data.Debt>) = shares.forEach { planning.saveDebt(it) }

    fun saveShare(debt: com.leftovers.app.data.Debt) {
        viewModelScope.launch { planning.saveDebt(debt) }
    }

    fun settleShare(debt: com.leftovers.app.data.Debt) {
        viewModelScope.launch { planning.settleEntry(debt) }
    }

    fun deleteShare(debt: com.leftovers.app.data.Debt) {
        viewModelScope.launch { planning.deleteDebt(debt) }
    }

    /** Splits shown as one bill: then a split opens whole, to change all of it at once. */
    val oneBill: StateFlow<Boolean> = settings.settings.map { it.splitsAsOneBill }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun saveTransfer(transfer: com.leftovers.app.data.Transfer) {
        viewModelScope.launch { accountRepository.saveTransfer(transfer) }
    }

    fun deleteTransfer(transfer: com.leftovers.app.data.Transfer) {
        viewModelScope.launch { accountRepository.deleteTransfer(transfer) }
    }

    /** Puts a deleted transfer back, from the Undo bar. */
    suspend fun restoreTransfer(transfer: com.leftovers.app.data.Transfer) = accountRepository.saveTransfer(transfer)

    val accounts: StateFlow<List<AccountWithBalance>> =
        accountRepository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every entry, to find the rest of a split bill. */
    val all: StateFlow<List<TransactionItem>> =
        repository.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(items: List<TransactionItem>) {
        viewModelScope.launch { items.forEach { repository.deleteTransaction(it.toTransaction()) } }
    }

    /** Puts deleted entries back. Runs from the Undo bar, after this card has gone. */
    suspend fun restore(items: List<TransactionItem>) = items.forEach { repository.saveTransaction(it.toTransaction()) }


}

/** Which entry's card is open, where it grew from, and which row steps aside until it shrinks back. */
class TransactionPeek {
    var open by mutableStateOf<TransactionItem?>(null)
    /** A person's share of a split bill, open on its own to change it. */
    var share by mutableStateOf<com.leftovers.app.data.Debt?>(null)
    /** A transfer between accounts, open on its own card. */
    var transfer by mutableStateOf<TransferLine?>(null)
    var hidden by mutableStateOf<Long?>(null)
    var from: Rect? = null
    val bounds = mutableMapOf<Long, Rect>()

    fun show(item: TransactionItem) {
        from = bounds[item.id]
        open = item
    }

    /** Transfers are kept apart from entries, so their rows go by the id below zero. */
    fun show(line: TransferLine) {
        from = bounds[-line.transfer.id]
        transfer = line
    }
}

@Composable
fun rememberTransactionPeek() = remember { TransactionPeek() }

/** For a row that opens [peek]: remembers where it is, and hides it while its card is open. */
fun Modifier.peekSource(peek: TransactionPeek, id: Long): Modifier =
    trackScreenBounds { peek.bounds[id] = it }.graphicsLayer { alpha = if (peek.hidden == id) 0f else 1f }

/**
 * Tapping an entry: a card in the middle with everything about it (amount, category, account, date, note, photos),
 * to edit or delete without opening the full editor.
 */
@Composable
fun TransactionPeekCard(
    peek: TransactionPeek,
    onEdit: (Long) -> Unit,
    viewModel: TransactionPeekViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    // A person's share, on its own: the same editor as on Money owed.
    peek.share?.let { debt ->
        val owing by viewModel.debts.collectAsStateWithLifecycle()
        val accountList by viewModel.accounts.collectAsStateWithLifecycle()
        DebtEditor(
            initial = debt,
            people = owing.map { it.person.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            accounts = accountList,
            onDismiss = { peek.share = null },
            onSave = { viewModel.saveShare(it); peek.share = null },
            onDelete = { viewModel.deleteShare(debt); peek.share = null },
            onSettle = { viewModel.settleShare(it); peek.share = null },
        )
    }
    TransferPeek(peek, viewModel)
    val item = peek.open ?: return
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val income = item.type == TxType.INCOME
    val tone = if (income) c.positive else c.textPrimary
    val account = accounts.firstOrNull { it.id == item.accountId }
    var photo by remember { mutableStateOf<String?>(null) }
    val photos = item.receiptPath?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    val all by viewModel.all.collectAsStateWithLifecycle()
    // The bill this entry belongs to: the entry it was split from, and every part of it.
    val main = if (item.splitOf != null) all.firstOrNull { it.id == item.splitOf } ?: item else item
    val bill = listOf(main) + all.filter { it.splitOf == main.id }
    val debtList by viewModel.debts.collectAsStateWithLifecycle()
    val shares = debtList.filter { it.splitOf == main.id && !it.repayment }
    val isBill = bill.size > 1 || shares.isNotEmpty()
    var askDelete by remember { mutableStateOf(false) }
    val oneBill by viewModel.oneBill.collectAsStateWithLifecycle()

    MorphCard(
        from = peek.from,
        onDismiss = { peek.open = null; peek.hidden = null },
        onReveal = { peek.hidden = null },
        onShown = { peek.hidden = item.id },
    ) { close ->
        photo?.let { ReceiptViewer(it, onDismiss = { photo = null }) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(item.categoryEmoji, item.categoryColor, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    com.leftovers.app.data.noteText(item.note).ifBlank { item.categoryName },
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.categoryName.takeIf { com.leftovers.app.data.noteText(item.note).isNotBlank() }, account?.name?.takeIf { accounts.size > 1 }).joinToString(" · ")
                        .ifBlank { if (income) "Income" else "Spending" },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        // Its tags, apart from the note.
        val tags = com.leftovers.app.data.hashtags(item.note)
        if (tags.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    Text(
                        "#$tag",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.glass).border(1.dp, c.borderBottom, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // The amount, on its own panel, with when it happened.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (income) c.positive.copy(alpha = 0.09f) else c.glass)
                .border(1.dp, if (income) c.positive.copy(alpha = 0.22f) else c.glassStrong, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(if (income) "Received" else "Spent", style = MaterialTheme.typography.labelLarge, color = if (income) c.positive else c.textSecondary)
            // "Spent" already says which way it went, so no minus sign.
            Text(if (income) "+" + money.format(item.amountMinor) else money.format(item.amountMinor), style = MaterialTheme.typography.displaySmall, color = tone)
            val foreign = item.foreignMinor
            val code = item.foreignCurrency
            Text(
                listOfNotNull(
                    item.date.friendlyLabel() + " · " + item.timeLabel(),
                    if (foreign != null && code != null) "paid ${Money(code).format(foreign)}" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (photos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEach { path -> ReceiptThumb(path, onOpen = { photo = path }, size = 64.dp) }
            }
        }
        // Part of a split bill: the whole bill, with this entry marked, and a way to split it up again.
        if (isBill) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.glass).border(1.dp, c.borderBottom, RoundedCornerShape(22.dp)).padding(vertical = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Split bill · ${money.format(bill.sumOf { it.amountMinor } + shares.sumOf { it.amountMinor })} in all" +
                            if (shares.isNotEmpty()) " · with ${shares.size} ${if (shares.size == 1) "other" else "others"}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    // Split with people who still owe: one message to the group's chat, each finds their own share.
                    if (shares.any { it.amountMinor > 0 && !it.settled }) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val shareScope = androidx.compose.runtime.rememberCoroutineScope()
                        com.leftovers.app.ui.components.RoundButton(Lucide.Share2, "Share with the group", {
                            shareScope.launch {
                                com.leftovers.app.util.DebtReminder.groupWithLink(context, main.id, money.currency.currencyCode, money::format)
                                    ?.let { com.leftovers.app.util.DebtReminder.share(context, it, "the group") }
                            }
                        }, size = 36.dp, tint = c.textPrimary)
                    }
                }
                // Tapping a line opens it to change: a part on its own, the main entry with the whole bill.
                bill.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().background(if (b.id == item.id) c.glassStrong else androidx.compose.ui.graphics.Color.Transparent)
                            .pressable({ close { onEdit(b.id) } }, pressedScale = 0.98f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(b.categoryEmoji, b.categoryColor, size = 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            // With people, your share is "You".
                            if (shares.isNotEmpty() && b.id == main.id) "You · ${b.categoryName}" else com.leftovers.app.data.noteText(b.note).ifBlank { b.categoryName },
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(money.format(b.amountMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        androidx.compose.material3.Icon(Lucide.ChevronRight, contentDescription = "Edit", tint = c.textTertiary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                }
                shares.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().pressable({ close { peek.share = d } }, pressedScale = 0.98f).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.leftovers.app.ui.components.IconTile(if (d.settled) Lucide.Check else Lucide.HandCoins, if (d.settled) c.textTertiary else c.positive, size = 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${d.person} · ${if (d.settled) "settled" else "owes you"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(money.format(d.amountMinor), style = MaterialTheme.typography.titleSmall, color = if (d.settled) c.textTertiary else c.positive)
                        androidx.compose.material3.Icon(Lucide.ChevronRight, contentDescription = "Edit ${d.person}'s share", tint = c.textTertiary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                }
            }
        }
        if (askDelete) {
            // A split bill: all of it, or just this entry.
            Text("Delete the whole bill, or only this entry?", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Only this", {
                    close {
                        viewModel.delete(listOf(item))
                        undo.show("Entry deleted") { viewModel.restore(listOf(item)) }
                    }
                }, Modifier.weight(1f))
                PrimaryButton("Whole bill", {
                    close {
                        viewModel.delete(bill)
                        viewModel.deleteShares(shares)
                        undo.show("Bill deleted") { viewModel.restore(bill); viewModel.restoreShares(shares) }
                    }
                }, Modifier.weight(1f), icon = Lucide.Trash2)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Round, like Add again, so the three fit side by side.
                com.leftovers.app.ui.components.RoundButton(Lucide.Trash2, "Delete", {
                    if (isBill) askDelete = true
                    else close {
                        viewModel.delete(listOf(item))
                        undo.show("Entry deleted") { viewModel.restore(listOf(item)) }
                    }
                }, size = 56.dp)
                // The same again, for today.
                com.leftovers.app.ui.components.RoundButton(Lucide.Repeat, "Add again for today", {
                    close { viewModel.addAgain(item) { id -> undo.show("Added again for today") { viewModel.removeAgain(id) } } }
                }, size = 56.dp)
                PrimaryButton(
                    if (isBill && oneBill) "Edit bill" else "Edit",
                    { close { onEdit(if (isBill && oneBill) main.id else item.id) } },
                    Modifier.weight(1f),
                    icon = Lucide.Pencil,
                )
            }
        }
    }
}

/** Tapping a transfer in Activity: a card with where the money went, how much and when, to change or delete it. */
@Composable
private fun TransferPeek(peek: TransactionPeek, viewModel: TransactionPeekViewModel) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<com.leftovers.app.data.Transfer?>(null) }
    editing?.let { transfer ->
        if (accounts.size >= 2) {
            TransferSheet(accounts, onDismiss = { editing = null }, initial = transfer) { from, to, amount, note, day ->
                viewModel.saveTransfer(transfer.copy(fromAccountId = from, toAccountId = to, amountMinor = amount, note = note, epochDay = day.toEpochDay()))
                editing = null
            }
        }
    }
    val line = peek.transfer ?: return
    val transfer = line.transfer
    MorphCard(
        from = peek.from,
        onDismiss = { peek.transfer = null; peek.hidden = null },
        onReveal = { peek.hidden = null },
        onShown = { peek.hidden = -transfer.id },
    ) { close ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.leftovers.app.ui.components.IconTile(Lucide.Repeat, c.textSecondary, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${line.from} → ${line.to}", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf("Transfer", transfer.note).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
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
            Text("Moved", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Text(money.format(transfer.amountMinor), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
            Text(
                java.time.LocalDate.ofEpochDay(transfer.epochDay).friendlyLabel() + " · not spending or income",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.leftovers.app.ui.components.RoundButton(Lucide.Trash2, "Delete", {
                close {
                    viewModel.deleteTransfer(transfer)
                    undo.show("Transfer deleted") { viewModel.restoreTransfer(transfer) }
                }
            }, size = 56.dp)
            PrimaryButton("Edit", { close { editing = transfer } }, Modifier.weight(1f), icon = Lucide.Pencil)
        }
    }
}
