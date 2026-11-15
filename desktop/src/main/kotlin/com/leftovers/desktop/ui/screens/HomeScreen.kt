package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.PersonBalance
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.DueBill
import com.leftovers.desktop.data.HomeModel
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.Page
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Card
import com.leftovers.desktop.ui.components.Empty
import com.leftovers.desktop.ui.components.EntryRow
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.Meter
import com.leftovers.desktop.ui.components.RollingText
import com.leftovers.desktop.ui.components.SectionHeader
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val greetingDate = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private fun greeting(now: LocalTime = LocalTime.now()) = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
fun HomeScreen(view: View) {
    val app = LocalApp.current
    val home = remember(view) { HomeModel.of(view) }
    Screen(
        greeting(),
        subtitle = LocalDate.now().format(greetingDate),
        actions = { Button("Add entry", { app.edit() }, kind = ButtonKind.Primary, icon = Lucide.Plus) },
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TodayCard(home, Modifier.weight(1.35f).fillMaxHeight())
            BalanceCard(home, Modifier.weight(1f).fillMaxHeight())
        }
        AnimatedVisibility(home.carryPrompt != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            home.carryPrompt?.let { CarryCard(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Recent", action = "See all".takeIf { home.recent.isNotEmpty() }) { app.page = Page.ACTIVITY }
                Card(Modifier.fillMaxWidth().animateContentSize(), padding = 6.dp) {
                    if (home.recent.isEmpty()) {
                        Empty(Lucide.Wallet, "Nothing logged yet", "Press Add entry, or Ctrl+N, to add your first one.")
                    } else {
                        val ids = home.recent.map { it.id }.toSet()
                        home.recent.forEach { item ->
                            androidx.compose.runtime.key(item.id) {
                                EntryRow(item, { app.edit(item.id) }, part = item.splitOf != null && item.splitOf in ids, account = view.account(item.accountId)?.name?.takeIf { view.accounts.size > 1 })
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (home.due.isNotEmpty()) Side("Due", "Manage", { app.page = Page.SUBSCRIPTIONS }) { home.due.forEach { DueRow(it) } }
                if (home.upcoming.isNotEmpty()) Side("Upcoming", "Manage", { app.page = Page.SUBSCRIPTIONS }) { home.upcoming.forEach { UpcomingRow(it) } }
                if (home.owed.isNotEmpty()) Side("Money owed", "See all", { app.page = Page.OWED }) { home.owed.take(5).forEach { OwedRow(it) } }
                if (home.goals.isNotEmpty()) Side("Saving for", "All goals", { app.page = Page.GOALS }) { home.goals.forEach { GoalRow(it) } }
            }
        }
    }
}

@Composable
private fun Side(title: String, action: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title, action, onAction)
        Card(Modifier.fillMaxWidth(), padding = 6.dp) { content() }
    }
}

/** The one number that matters most: what can be spent today. */
@Composable
private fun TodayCard(home: HomeModel, modifier: Modifier) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    val budget = home.today
    val usedUp = budget != null && budget.monthUsedUp
    val over = budget != null && (usedUp || budget.leftToday < 0)
    val newLimit = if (over && !usedUp && home.newDailyLimit) budget?.newDailyLimit(home.daysLeft) else null
    val spentToday = budget?.spentToday ?: 0L
    Card(modifier, padding = 26.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    budget == null -> "Spent today"
                    usedUp -> "Over budget"
                    newLimit != null -> "New daily limit"
                    over -> "Over today's budget"
                    else -> "Safe to spend today"
                },
                style = T.type.bodyStrong, color = c.text2, modifier = Modifier.weight(1f),
            )
            Text("${home.daysLeft} ${if (home.daysLeft == 1) "day" else "days"} left", style = T.type.small, color = c.text3)
        }
        Spacer(Modifier.height(10.dp))
        val hero = money.formatWhole(
            when {
                budget == null -> spentToday
                usedUp -> abs(budget.monthLeft)
                newLimit != null -> newLimit
                else -> abs(budget.leftToday)
            },
        )
        // A long amount shrinks just enough to fit rather than run off the card.
        var room by remember { mutableStateOf(0) }
        Box(Modifier.fillMaxWidth().onSizeChanged { room = it.width }) {
            val full = T.type.hero.copy(fontSize = T.type.hero.fontSize * 1.25f)
            val measurer = rememberTextMeasurer()
            val width = remember(hero, full) { measurer.measure(hero, full).size.width }
            val style = if (room in 1 until width) full.copy(fontSize = full.fontSize * (room.toFloat() / width * 0.97f)) else full
            RollingText(hero, style, if (over && newLimit == null) c.negative else c.text)
        }
        Spacer(Modifier.weight(1f).height(18.dp))
        if (budget != null) {
            Meter(budget.usedFraction, height = 8.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    usedUp -> {
                        val spent = budget.monthlyBudget - budget.monthLeft - budget.reserved
                        if (budget.reserved > 0) "${money.formatWhole(spent)} spent + ${money.formatWhole(budget.reserved)} bills due, of ${money.formatWhole(budget.monthlyBudget)}"
                        else "${money.formatWhole(spent)} of ${money.formatWhole(budget.monthlyBudget)}"
                    }
                    newLimit != null -> "${money.formatWhole(abs(budget.leftToday))} over today · from tomorrow"
                    else -> "${money.formatWhole(spentToday)} of ${money.formatWhole(budget.dailyLimit)} today · ${money.formatWhole(budget.monthLeft.coerceAtLeast(0))} left this month"
                },
                style = T.type.small, color = c.text2,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set a budget to see a daily allowance", style = T.type.small, color = c.text2, modifier = Modifier.weight(1f))
                Button("Set a budget", { app.page = Page.BUDGETS }, kind = ButtonKind.Secondary)
            }
        }
    }
}

/** All the money there is, where it's heading, and this month's in and out. */
@Composable
private fun BalanceCard(home: HomeModel, modifier: Modifier) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    Card(modifier, padding = 26.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(home.selectedAccount?.name ?: "Total balance", style = T.type.bodyStrong, color = c.text2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (home.accounts.size > 1) AccountPicker(home)
        }
        Spacer(Modifier.height(10.dp))
        RollingText(money.formatWhole(home.balance), T.type.display, c.text)
        home.forecast?.let { f ->
            val end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
            Spacer(Modifier.height(4.dp))
            Text("About ${money.formatWhole(f)} by ${end.format(DateTimeFormatter.ofPattern("d MMM"))}", style = T.type.small, color = c.text2)
        }
        Spacer(Modifier.weight(1f).height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Flow(Lucide.ArrowUpRight, "Spent", money.formatWhole(home.monthSpent), c.negative, Modifier.weight(1f)) { app.page = Page.ACTIVITY }
            Flow(Lucide.ArrowDownLeft, "Income", money.formatWhole(home.monthIncome), c.positive, Modifier.weight(1f)) { app.page = Page.ACTIVITY }
        }
    }
}

@Composable
private fun Flow(icon: ImageVector, label: String, amount: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = T.c
    HoverRow(onClick, modifier.clip(RoundedCornerShape(12.dp)).background(c.raised), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(15.dp), tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = T.type.small, color = c.text2)
            Text(amount, style = T.type.bodyStrong, color = c.text, maxLines = 1)
        }
    }
}

/** Like switching wallets: Home's balance and entries follow the account picked. */
@Composable
private fun AccountPicker(home: HomeModel) {
    val store = LocalApp.current.store
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Lucide.ChevronDown, { open = true }, size = 28.dp, tooltip = "Pick an account")
        androidx.compose.material3.DropdownMenu(open, { open = false }, shape = RoundedCornerShape(12.dp), containerColor = if (T.c.dark) Color(0xFF1E1E26) else Color.White) {
            (listOf(null) + home.accounts).forEach { a ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(a?.name ?: "All accounts", style = T.type.body, color = T.c.text) },
                    trailingIcon = { if (a?.id == home.selectedAccount?.id) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = T.c.text) },
                    onClick = { store.setPrefs { copy(homeAccountId = a?.id) }; open = false },
                )
            }
        }
    }
}

@Composable
private fun CarryCard(left: Long) {
    val c = T.c
    val money = LocalMoney.current
    val store = LocalApp.current.store
    val now = java.time.YearMonth.now()
    Card(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${money.formatWhole(left)} left from ${now.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())}", style = T.type.heading, color = c.text)
                Text("Add it to ${now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}'s budget?", style = T.type.small, color = c.text2)
            }
            Button("No thanks", { store.setCarryChoice(now.minusMonths(1), false) }, kind = ButtonKind.Quiet)
            Spacer(Modifier.width(8.dp))
            Button("Carry over", { store.setCarryChoice(now.minusMonths(1), true) }, kind = ButtonKind.Primary)
        }
    }
}

@Composable
private fun DueRow(d: DueBill) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    val today = LocalDate.now()
    val income = d.item.type == TxType.INCOME
    HoverRow(null, Modifier.fillMaxWidth()) {
        IconBadge(d.item.categoryEmoji, d.item.categoryColor, 34.dp, app.store.book.icons)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.item.name, style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (d.date < today) "Was due " else "Due ") + d.date.friendlyLabel() + " · " + money.format(d.item.amountMinor), style = T.type.small, color = if (d.date < today) c.warning else c.text2, maxLines = 1)
        }
        IconButton(Lucide.X, {
            val before = d.item.toRecurring()
            app.store.skipRecurring(before, d.month)
            app.tell("${d.item.name} skipped this time") { app.store.edit { copy(recurring = recurring.map { if (it.id == before.id) before else it }) } }
        }, tooltip = "Skip this time")
        Spacer(Modifier.width(4.dp))
        Button(if (income) "Received" else "Paid", {
            val before = d.item.toRecurring()
            val id = app.store.payRecurring(before, d.month, d.item.amountMinor, today.toEpochDay(), null)
            app.tell("${d.item.name} marked ${if (income) "received" else "paid"}") { app.store.unpayRecurring(before, id) }
        }, icon = Lucide.Check)
    }
}

@Composable
private fun UpcomingRow(r: RecurringItem) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    val next = r.toRecurring().nextChargeDate()
    val days = (next.toEpochDay() - LocalDate.now().toEpochDay()).toInt()
    var menu by remember { mutableStateOf(false) }
    Box {
    HoverRow({ if (r.once) menu = true else app.page = Page.SUBSCRIPTIONS }, Modifier.fillMaxWidth()) {
        IconBadge(r.categoryEmoji, r.categoryColor, 34.dp, app.store.book.icons)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (r.once) "Scheduled · " else "") + when (days) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "In $days days · ${next.format(DateTimeFormatter.ofPattern("d MMM"))}" },
                style = T.type.small, color = c.text2,
            )
        }
        Text((if (r.type == TxType.INCOME) "+" else "") + money.format(r.amountMinor), style = T.type.bodyStrong, color = if (r.type == TxType.INCOME) c.positive else c.text)
    }
    // A scheduled one-off: add it now, or cancel it.
    androidx.compose.material3.DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(12.dp), containerColor = if (c.dark) Color(0xFF1E1E26) else Color.White) {
        androidx.compose.material3.DropdownMenuItem({ Text("Add it now", style = T.type.body, color = c.text) }, {
            menu = false
            app.store.addScheduledNow(r.toRecurring())
            app.tell("${r.name} added") { app.store.undo() }
        }, leadingIcon = { Icon(Lucide.Check, null, Modifier.size(16.dp), tint = c.text2) })
        androidx.compose.material3.DropdownMenuItem({ Text("Cancel it", style = T.type.body, color = c.negative) }, {
            menu = false
            app.store.deleteRecurring(r.toRecurring())
            app.tell("${r.name} cancelled") { app.store.undo() }
        }, leadingIcon = { Icon(Lucide.X, null, Modifier.size(16.dp), tint = c.negative) })
    }
    }
}

@Composable
private fun OwedRow(b: PersonBalance) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    val theyOwe = b.netMinor > 0 || (b.netMinor == 0L && b.entries.any { it.isPending && it.pending > 0 })
    HoverRow({ app.page = Page.OWED }, Modifier.fillMaxWidth()) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) {
            Text(b.person.trim().take(1).uppercase(), style = T.type.bodyStrong, color = c.text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.person.trim(), style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (theyOwe) "Owes you" else "You owe", style = T.type.small, color = c.text2)
        }
        Text(if (b.pendingOnly) "Pending" else money.format(abs(b.netMinor)), style = T.type.bodyStrong, color = if (theyOwe) c.positive else c.negative)
    }
}

@Composable
private fun GoalRow(g: GoalWithSaved) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    HoverRow({ app.page = Page.GOALS }, Modifier.fillMaxWidth()) {
        IconBadge(g.emoji, g.color, 34.dp, app.store.book.icons)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(g.name, style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${money.formatWhole(g.savedMinor)} of ${money.formatWhole(g.targetMinor)}", style = T.type.small, color = c.text2)
            }
            Meter(g.fraction, height = 5.dp, color = Color(g.color))
        }
    }
}
