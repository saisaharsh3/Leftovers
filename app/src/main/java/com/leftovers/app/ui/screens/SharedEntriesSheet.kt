package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.Category
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import com.leftovers.app.util.SharedEntries
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Entries a friend shared from their Leftovers: each one with the category it'll go under here (theirs by name, or
 * the closest), ticked to add. Ones that look like entries already here start unticked. Nothing is added until
 * Add is tapped; they go to the default account, and can be changed after like any entry.
 */
@Composable
fun SharedEntriesSheet(sent: SharedEntries.Shared, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val scope = rememberCoroutineScope()
    val container = (LocalContext.current.applicationContext as LeftoversApp).container
    val categories by container.repository.categories.collectAsState(initial = null)
    val history by container.repository.allTransactions.collectAsState(initial = null)
    val cats = categories ?: return
    val have = history ?: return
    val theirs = if (sent.currency.isNotBlank() && sent.currency != money.currency.currencyCode) Money(sent.currency) else money

    /** Their category by name here, else Other (or the first) of the same kind. */
    fun categoryFor(line: SharedEntries.Line): Category? {
        val type = if (line.income) TxType.INCOME else TxType.EXPENSE
        val usable = cats.filter { it.type == type && !it.hidden }
        return usable.firstOrNull { it.name.equals(line.category, ignoreCase = true) }
            ?: usable.firstOrNull { it.name.equals("Other", ignoreCase = true) }
            ?: usable.firstOrNull()
    }
    // Already here: same amount, day and kind.
    val looksAdded = remember(have) {
        sent.lines.map { l -> have.any { it.amountMinor == l.amountMinor && it.epochDay == l.epochDay && (it.type == TxType.INCOME) == l.income } }
    }
    val picked = remember { mutableStateListOf<Boolean>().apply { addAll(looksAdded.map { !it }) } }
    var adding by remember { mutableStateOf(false) }

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Shared with you", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                if (sent.lines.size == 1) "An entry from a friend's Leftovers. Add it to yours?" else "${sent.lines.size} entries from a friend's Leftovers. Tick the ones to add to yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            if (theirs !== money) {
                Text(
                    "They're in ${sent.currency}; yours are in ${money.currency.currencyCode}. They'll be added with the same numbers, so check them after.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warning,
                )
            }
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    sent.lines.forEachIndexed { i, line ->
                        if (i > 0) RowDivider(inset = 56.dp)
                        val cat = categoryFor(line)
                        Row(
                            Modifier.fillMaxWidth().pressable({ picked[i] = !picked[i] }, pressedScale = 0.99f).padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = picked[i],
                                onCheckedChange = { picked[i] = it },
                                colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent),
                            )
                            if (cat != null) CategoryIcon(cat.emoji, cat.color, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(line.note.ifBlank { line.category.ifBlank { "Entry" } }, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(cat?.name, line.date.friendlyLabel(), if (looksAdded[i]) "maybe already added" else null).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (looksAdded[i]) c.warning else c.textSecondary,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                (if (line.income) "+" else "") + theirs.format(line.amountMinor),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (line.income) c.positive else c.textPrimary,
                            )
                        }
                    }
                }
            }
            val count = picked.count { it }
            PrimaryButton(
                when {
                    adding -> "Adding…"
                    count == 0 -> "Nothing ticked"
                    count == 1 -> "Add 1 entry"
                    else -> "Add $count entries"
                },
                {
                    adding = true
                    scope.launch {
                        val account = container.settings.settings.first().defaultAccountId.takeIf { id -> container.accounts.accounts.first().any { it.id == id } }
                        val ids = sent.lines.filterIndexed { i, _ -> picked[i] }.mapNotNull { line ->
                            val cat = categoryFor(line) ?: return@mapNotNull null
                            container.repository.saveTransaction(
                                Transaction(
                                    amountMinor = line.amountMinor,
                                    type = if (line.income) TxType.INCOME else TxType.EXPENSE,
                                    categoryId = cat.id,
                                    epochDay = line.epochDay,
                                    note = line.note,
                                    accountId = account,
                                ),
                            )
                        }
                        onDismiss()
                        undo.show(if (ids.size == 1) "Entry added" else "${ids.size} entries added") {
                            // Gone for good: they were only just added, so they don't belong in Deleted entries.
                            ids.forEach { id -> container.repository.getTransaction(id)?.let { container.repository.deleteTransaction(it) } }
                            container.repository.deleteForever(ids)
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = count > 0 && !adding,
            )
        }
    }
}
