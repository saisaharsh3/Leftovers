package com.leftovers.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.LeftoversApp
import com.leftovers.app.data.Debt
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import com.leftovers.app.util.SharedOwed
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/**
 * Money owed that a friend sent from their Leftovers, opened from the link in their message: what it says, the
 * other way round (what they lent, you borrowed), to add to Money owed under their name. Entries already added
 * from an earlier message are left out, so tapping a link twice adds nothing twice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedOwedSheet(shared: SharedOwed.Shared, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val undo = LocalUndo.current
    val container = (LocalContext.current.applicationContext as LeftoversApp).container
    val planning = container.planning
    val device by androidx.compose.runtime.produceState<String?>(null) { value = container.settings.syncDevice() }
    val scope = rememberCoroutineScope()
    val debts by planning.debts.collectAsState(initial = null)
    val have = debts ?: return
    val mine = device ?: return
    // Known already: received before, or this phone's own entries by the id it sends them with.
    val known = have.map { SharedOwed.idOf(mine, it) }.toSet()
    val fresh = shared.lines.filter { it.id !in known }
    // Sent in another currency than this phone's: shown in theirs, and said so.
    val theirs = if (shared.currency.isNotBlank() && shared.currency != money.currency.currencyCode) Money(shared.currency) else money
    val net = -fresh.sumOf { it.amountMinor }
    // Entries received from them before say who this is.
    val sender = have.firstOrNull { it.sharedId != null && shared.lines.any { l -> l.id == it.sharedId } }?.person
    var person by rememberSaveable { mutableStateOf(sender.orEmpty()) }
    val people = have.map { it.person.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(8)

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("From a friend's Leftovers", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            if (fresh.isEmpty()) {
                Text("This is already in your Money owed. Nothing was added again.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
                return@Column
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassStrong, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(if (net < 0) "You owe" else "You're owed", style = MaterialTheme.typography.labelLarge, color = if (net < 0) c.textSecondary else c.positive)
                Text(theirs.format(abs(net)), style = MaterialTheme.typography.displaySmall, color = if (net < 0) c.textPrimary else c.positive)
                if (fresh.size == 1) {
                    Text(
                        listOf(fresh[0].note, LocalDate.ofEpochDay(fresh[0].epochDay).friendlyLabel()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
            }
            if (fresh.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fresh.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    line.note.ifBlank { if (line.repayment) "Paid back" else if (line.amountMinor > 0) "Borrowed" else "Lent" },
                                    style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(LocalDate.ofEpochDay(line.epochDay).friendlyLabel(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                            Spacer(Modifier.width(12.dp))
                            // As it changes what you owe: up for money you borrowed, down for what was paid back.
                            Text((if (line.amountMinor > 0) "" else "−") + theirs.format(abs(line.amountMinor)), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        }
                    }
                }
            }
            if (theirs !== money) {
                Text(
                    "Sent in ${theirs.currency.currencyCode}; your app is in ${money.currency.currencyCode}. The amounts are added as they are, so change them after if you need to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            GlassTextField(person, { person = it.take(30) }, placeholder = "Their name", label = "Who sent this?")
            if (people.isNotEmpty() && sender == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { name -> Chip(name, onClick = { person = name }, selected = person.trim().equals(name, ignoreCase = true)) }
                }
            }
            PrimaryButton("Add to Money owed", {
                val added: List<Debt> = fresh.map { SharedOwed.mirror(it, person) }
                val ids = fresh.map { it.id }.toSet()
                // Closed only once all of it is saved: closing ends this sheet's work, and would cut the saving short.
                scope.launch {
                    added.forEach { planning.saveDebt(it) }
                    undo.show("Added to Money owed") { planning.debts.first().filter { it.sharedId in ids }.forEach { planning.deleteDebt(it) } }
                    onDismiss()
                }
            }, Modifier.fillMaxWidth(), enabled = person.isNotBlank())
        }
    }
}
