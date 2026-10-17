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
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionPeekViewModel(private val repository: TransactionRepository, accounts: AccountRepository) : ViewModel() {
    val accounts: StateFlow<List<AccountWithBalance>> =
        accounts.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(item: TransactionItem) {
        viewModelScope.launch { repository.deleteTransaction(item.toTransaction()) }
    }

    /** Puts a deleted entry back. Runs from the Undo bar, after this card has gone. */
    suspend fun restore(item: TransactionItem) = repository.saveTransaction(item.toTransaction())
}

/** Which entry's card is open, where it grew from, and which row steps aside until it shrinks back. */
class TransactionPeek {
    var open by mutableStateOf<TransactionItem?>(null)
    var hidden by mutableStateOf<Long?>(null)
    var from: Rect? = null
    val bounds = mutableMapOf<Long, Rect>()

    fun show(item: TransactionItem) {
        from = bounds[item.id]
        open = item
        hidden = item.id
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

    MorphCard(
        from = peek.from,
        onDismiss = { peek.open = null; peek.hidden = null },
        onReveal = { peek.hidden = null },
    ) { close ->
        photo?.let { ReceiptViewer(it, onDismiss = { photo = null }) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(item.categoryEmoji, item.categoryColor, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.note.ifBlank { item.categoryName },
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.categoryName.takeIf { item.note.isNotBlank() }, account?.name?.takeIf { accounts.size > 1 }).joinToString(" · ")
                        .ifBlank { if (income) "Income" else "Expense" },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Delete", {
                close {
                    viewModel.delete(item)
                    undo.show("Entry deleted") { viewModel.restore(item) }
                }
            }, Modifier.weight(1f), icon = Lucide.Trash2)
            PrimaryButton("Edit", { close { onEdit(item.id) } }, Modifier.weight(1.4f), icon = Lucide.Pencil)
        }
    }
}
