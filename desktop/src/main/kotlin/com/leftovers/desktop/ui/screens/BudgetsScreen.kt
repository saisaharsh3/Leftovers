package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.Bucket
import com.leftovers.app.data.BudgetMode
import com.leftovers.app.data.BudgetPlan
import com.leftovers.app.data.CarryMode
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.YearSplit
import com.leftovers.app.data.bucketBase
import com.leftovers.app.data.bucketUse
import com.leftovers.app.data.dailyBudget
import com.leftovers.app.data.periodName
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.desktop.data.Sum
import com.leftovers.desktop.data.View
import com.leftovers.desktop.data.ordinal
import com.leftovers.desktop.data.pendingExpenses
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.Page
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Card
import com.leftovers.desktop.ui.components.EntryRow
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.Meter
import com.leftovers.desktop.ui.components.RollingText
import com.leftovers.desktop.ui.components.SectionHeader
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.SettingRow
import com.leftovers.desktop.ui.components.SidePanel
import com.leftovers.desktop.ui.components.Toggle
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Money as typed in a field: whole or with cents, never "1e3" or a trailing ".0". */
fun minorText(minor: Long): String = if (minor <= 0) "" else if (minor % 100 == 0L) (minor / 100).toString() else java.math.BigDecimal.valueOf(minor, 2).toPlainString()

/** An amount field: sums work in it too. */
@Composable
fun MoneyField(text: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier.fillMaxWidth(), large: Boolean = false, focus: FocusRequester? = null, onEnter: (() -> Unit)? = null) {
    val money = LocalMoney.current
    Field(
        text, { t -> if (t.length <= 40 && t.all { it.isDigit() || it in ".,+-*/×÷x() " }) onChange(t) }, modifier,
        placeholder = placeholder.ifEmpty { money.symbol + " 0" }, style = if (large) T.type.title else T.type.body, focusRequester = focus, onEnter = onEnter,
    )
}

@Composable
fun BudgetsScreen(view: View) {
    val app = LocalApp.current
    val c = T.c
    val money = LocalMoney.current
    var editing by remember { mutableStateOf(false) }
    val plan = view.book.plan
    val today = LocalDate.now()
    val cycle = plan.cycleOf(today)
    val all = view.entries
    val items = all.filter { it.date in cycle }
    val breakdown = plan.breakdownFor(cycle.month, all)
    val budget = breakdown.total
    val pending = if (plan.envelope) 0L else view.recurring.pendingExpenses(cycle, today)
    val daily = dailyBudget(budget, items, pending, today, periodEnd = cycle.end)
    val spent = items.filter { !it.outOfBudget }.totalOf(TxType.EXPENSE)
    val goalsMonthly = view.goals.filter { !it.reached }.sumOf { it.monthlyNeeded() }
    val used = items.filter { it.type == TxType.EXPENSE && !it.outOfBudget }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Screen(
        "Budgets",
        subtitle = if (plan.isSet) when (plan.mode) {
            BudgetMode.MONTHLY, BudgetMode.YEARLY -> if (cycle.start.dayOfMonth == 1) cycle.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else "${cycle.start.format(fmt)} to ${cycle.end.format(fmt)}"
            else -> "${cycle.start.format(fmt)} to ${cycle.end.format(fmt)}"
        } else null,
        actions = {
            if (plan.isSet) Button("Edit plan", { editing = true }, icon = Lucide.SlidersHorizontal)
            Button("Add entry", { app.edit() }, kind = ButtonKind.Primary, icon = Lucide.Plus)
        },
    ) {
        if (!plan.isSet) {
            Card(Modifier.fillMaxWidth(), padding = 28.dp) {
                Text("No budget yet", style = T.type.title, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text("A budget gives you a daily allowance on Home. Category limits below work on their own too.", style = T.type.body, color = c.text2)
                Spacer(Modifier.height(18.dp))
                Row { Button("Create a budget", { editing = true }, kind = ButtonKind.Primary) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.weight(1.35f).fillMaxHeight(), padding = 26.dp) {
                    val left = budget - spent
                    Text(if (left >= 0) "Left" else "Over by", style = T.type.bodyStrong, color = c.text2)
                    Spacer(Modifier.height(8.dp))
                    RollingText(money.formatWhole(abs(left)), T.type.hero, if (left < 0) c.negative else c.text)
                    Spacer(Modifier.height(4.dp))
                    Text("Spent ${money.format(spent)} of ${money.format(budget)}", style = T.type.small, color = c.text2)
                    Spacer(Modifier.weight(1f).height(16.dp))
                    Meter(if (budget > 0) spent.toFloat() / budget else 0f, height = 8.dp)
                    if (breakdown.income > 0 || breakdown.carried != 0L) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOfNotNull(
                                "Planned ${money.formatWhole(breakdown.planned)}",
                                breakdown.income.takeIf { it > 0 }?.let { "+ ${money.formatWhole(it)} extra income" },
                                breakdown.carried.takeIf { it != 0L }?.let { (if (it > 0) "+ " else "− ") + money.formatWhole(abs(it)) + " carried over" },
                            ).joinToString(" "),
                            style = T.type.small, color = c.text3,
                        )
                    }
                }
                Card(Modifier.weight(1f).fillMaxHeight(), padding = 22.dp) {
                    Text("Today", style = T.type.bodyStrong, color = c.text2)
                    Spacer(Modifier.height(12.dp))
                    Stat("Daily allowance", money.formatWhole(daily?.dailyLimit ?: 0))
                    Stat(if ((daily?.leftToday ?: 0) < 0) "Over today" else "Left today", money.formatWhole(abs(daily?.leftToday ?: 0)), if ((daily?.leftToday ?: 0) < 0) c.negative else c.text)
                    Stat("Days left", cycle.daysLeft(today).toString())
                    if (pending > 0) Stat("Bills still due", money.formatWhole(pending), c.text2, "Already set aside from the allowance")
                    if (goalsMonthly > 0) Stat("Savings goals", money.formatWhole(goalsMonthly) + " a month", c.text2, "Leave this unspent to stay on track")
                }
            }
            if (plan.buckets) {
                val income = bucketBase(plan.bucketIncome, items.totalOf(TxType.INCOME), budget)
                val saved = view.book.deposits.filter { it.epochDay in cycle.start.toEpochDay()..cycle.end.toEpochDay() }.sumOf { it.amountMinor }
                val buckets = bucketUse(income, items, view.book.categories, saved)
                SectionHeader("50 / 30 / 20")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    buckets.forEach { b ->
                        Card(Modifier.weight(1f), padding = 18.dp) {
                            Row {
                                Text(b.bucket.label, style = T.type.heading, color = c.text, modifier = Modifier.weight(1f))
                                Text("${b.bucket.share}%", style = T.type.small, color = c.text3)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("${money.formatWhole(b.used)} of ${money.formatWhole(b.allowed)}", style = T.type.bodyStrong, color = c.text)
                            Spacer(Modifier.height(8.dp))
                            // Savings is the one where more is better.
                            Meter(if (b.allowed > 0) b.used.toFloat() / b.allowed else 0f, color = if (b.bucket == Bucket.SAVE) c.positive else null)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Category limits")
                CategoryLimits(view, items)
            }
            if (plan.isSet) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(if (plan.envelope) "In this budget" else "Spent this budget", if (used.size > 6) "See all ${used.size}" else "See all") { app.page = Page.ACTIVITY }
                    Card(Modifier.fillMaxWidth(), padding = 6.dp) {
                        if (used.isEmpty()) Text("Nothing spent yet.", style = T.type.body, color = c.text2, modifier = Modifier.padding(14.dp))
                        used.sortedWith(compareByDescending<TransactionItem> { it.epochDay }.thenByDescending { it.createdAt }).take(6).forEach { item ->
                            androidx.compose.runtime.key(item.id) { EntryRow(item, { app.edit(item.id) }) }
                        }
                    }
                }
            }
        }
    }
    BudgetPlanPanel(editing, view) { editing = false }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color = T.c.text, line: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.type.body, color = T.c.text2)
            if (line != null) Text(line, style = T.type.small, color = T.c.text3)
        }
        Text(value, style = T.type.heading, color = color)
    }
}

/** Every spending category with its limit, if it has one; a click on a row sets or changes it right there. */
@Composable
private fun CategoryLimits(view: View, items: List<TransactionItem>) {
    val c = T.c
    val money = LocalMoney.current
    val store = LocalApp.current.store
    var open by remember { mutableStateOf<Long?>(null) }
    var text by remember { mutableStateOf("") }
    val spentBy = items.filter { it.type == TxType.EXPENSE }.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    val rows = view.book.categories.filter { it.type == TxType.EXPENSE && !it.hidden }
        .sortedWith(compareByDescending<com.leftovers.app.data.Category> { it.budgetMinor != null }.thenByDescending { spentBy[it.id] ?: 0L })
    Card(Modifier.fillMaxWidth(), padding = 6.dp) {
        rows.forEach { cat ->
            androidx.compose.runtime.key(cat.id) {
                val spent = spentBy[cat.id] ?: 0L
                val limit = cat.budgetMinor
                Column {
                    HoverRow({ open = if (open == cat.id) null else cat.id; text = minorText(limit ?: 0) }, Modifier.fillMaxWidth()) {
                        IconBadge(cat.emoji, cat.color, 34.dp, view.book.icons)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Text(cat.name, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (limit != null) "${money.formatWhole(spent)} of ${money.formatWhole(limit)}" else if (spent > 0) money.formatWhole(spent) + " · no limit" else "No limit",
                                    style = T.type.small, color = if (limit != null && spent > limit) c.negative else c.text2,
                                )
                            }
                            if (limit != null) Meter(spent.toFloat() / limit.coerceAtLeast(1), height = 5.dp)
                        }
                    }
                    AnimatedVisibility(open == cat.id) {
                        val focus = remember { FocusRequester() }
                        fun saveLimit() {
                            store.edit { copy(categories = categories.map { if (it.id == cat.id) it.copy(budgetMinor = Sum.minor(text)?.takeIf { v -> v > 0 }) else it }) }
                            open = null
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 58.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MoneyField(text, { text = it }, "Limit a month", Modifier.weight(1f), focus = focus, onEnter = ::saveLimit)
                            if (limit != null) Button("Remove", { text = ""; saveLimit() }, kind = ButtonKind.Quiet)
                            Button("Set", ::saveLimit, kind = ButtonKind.Primary)
                        }
                        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    }
                }
            }
        }
    }
}

/** The budget plan, as on the phone: monthly, yearly, every few weeks, or for dates, and how it behaves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetPlanPanel(visible: Boolean, view: View, onClose: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    val p = view.book.plan
    var mode by remember(visible) { mutableStateOf(p.mode) }
    var monthly by remember(visible) { mutableStateOf(minorText(p.monthlyMinor)) }
    var yearly by remember(visible) { mutableStateOf(minorText(p.yearlyMinor)) }
    var split by remember(visible) { mutableStateOf(p.split) }
    val custom = remember(visible) { mutableStateMapOf<Int, String>().apply { p.custom.forEach { (m, v) -> put(m, minorText(v)) } } }
    var carry by remember(visible) { mutableStateOf(p.carry) }
    var extraIncome by remember(visible) { mutableStateOf(p.extraIncome) }
    var startDay by remember(visible) { mutableStateOf(p.startDay) }
    var buckets by remember(visible) { mutableStateOf(p.buckets) }
    var bucketIncome by remember(visible) { mutableStateOf(minorText(p.bucketIncome)) }
    var period by remember(visible) { mutableStateOf(minorText(p.periodMinor)) }
    var periodDays by remember(visible) { mutableStateOf(p.periodDays) }
    var periodFrom by remember(visible) { mutableStateOf(p.periodFrom ?: LocalDate.now()) }
    var range by remember(visible) { mutableStateOf(minorText(p.rangeMinor)) }
    var rangeFrom by remember(visible) { mutableStateOf(p.rangeFrom ?: LocalDate.now()) }
    var rangeTo by remember(visible) { mutableStateOf(p.rangeTo ?: LocalDate.now().plusDays(13)) }
    var envelope by remember(visible) { mutableStateOf(p.envelope) }
    var tag by remember(visible) { mutableStateOf(p.rangeTag.orEmpty()) }
    var rangeAccount by remember(visible) { mutableStateOf(p.rangeAccount) }
    var picking by remember(visible) { mutableStateOf<String?>(null) }
    var askRemove by remember(visible) { mutableStateOf(false) }
    fun plan() = p.copy(
        mode = mode, monthlyMinor = Sum.minor(monthly) ?: 0, yearlyMinor = Sum.minor(yearly) ?: 0, split = split,
        custom = custom.mapNotNull { (m, t) -> Sum.minor(t)?.let { m to it } }.toMap(), carry = carry, extraIncome = extraIncome,
        startDay = startDay, rangeMinor = Sum.minor(range) ?: 0, rangeFrom = rangeFrom, rangeTo = rangeTo,
        rangeTag = tag.trim().removePrefix("#").lowercase().takeIf { envelope && it.isNotEmpty() }, rangeAccount = rangeAccount.takeIf { envelope },
        periodMinor = Sum.minor(period) ?: 0, periodDays = periodDays, periodFrom = periodFrom,
        buckets = buckets, bucketIncome = Sum.minor(bucketIncome) ?: 0,
    )
    fun save() {
        if (!plan().isSet) return
        store.setPlan(plan())
        app.tell("Budget saved")
        onClose()
    }
    val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    SidePanel(
        visible, "Budget plan", onClose, width = 500.dp, onSave = ::save,
        footer = {
            if (p.isSet) {
                if (askRemove) Button("Remove the budget", { store.setPlan(BudgetPlan()); app.tell("Budget removed"); onClose() }, kind = ButtonKind.Danger)
                else Button("Remove budget", { askRemove = true }, kind = ButtonKind.Quiet)
            }
            Spacer(Modifier.weight(1f))
            Button("Save plan", ::save, kind = ButtonKind.Primary, enabled = plan().isSet)
        },
    ) {
        Segmented(
            listOf(BudgetMode.MONTHLY to "Monthly", BudgetMode.YEARLY to "Yearly", BudgetMode.PERIOD to "Weeks", BudgetMode.RANGE to "Dates"),
            mode, { mode = it },
        )
        when (mode) {
            BudgetMode.MONTHLY -> MoneyField(monthly, { monthly = it }, "Budget every month", large = true, onEnter = ::save)
            BudgetMode.YEARLY -> {
                MoneyField(yearly, { yearly = it }, "Budget for ${LocalDate.now().year}", large = true, onEnter = ::save)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Split across months")
                    YearSplit.entries.forEach { s ->
                        SettingRow(s.title, s.description, onClick = { split = s }) { Toggle(split == s) { split = s } }
                    }
                }
                if (split == YearSplit.CUSTOM) {
                    val total = custom.values.sumOf { Sum.minor(it) ?: 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Assigned ${money.format(total)} of ${money.format(Sum.minor(yearly) ?: 0)}", style = T.type.small, color = c.text2, modifier = Modifier.weight(1f))
                        Button("Fill evenly", { Sum.minor(yearly)?.let { y -> (1..12).forEach { custom[it] = minorText(y / 12) } } }, kind = ButtonKind.Quiet)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                        (1..12).forEach { m ->
                            Column(Modifier.weight(1f)) {
                                Text(java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = T.type.small, color = c.text2)
                                MoneyField(custom[m].orEmpty(), { custom[m] = it }, "", Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            BudgetMode.PERIOD -> {
                MoneyField(period, { period = it }, "Budget " + periodName(periodDays), large = true, onEnter = ::save)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Every")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7 to "Week", 14 to "2 weeks", 28 to "4 weeks", 42 to "6 weeks", 56 to "8 weeks", 84 to "12 weeks").forEach { (d, label) ->
                            Chip(label, periodDays == d) { periodDays = d }
                        }
                    }
                }
                SettingRow("Starts on ${periodFrom.format(dayFmt)}", "A payday · click to change", onClick = { picking = if (picking == "period") null else "period" }) { Chevron() }
                AnimatedVisibility(picking == "period") { MonthCalendar(periodFrom) { periodFrom = it; picking = null } }
                val amount = Sum.minor(period) ?: 0
                if (amount > 0) Text("${money.format(amount / periodDays.coerceAtLeast(1))} a day · each period starts fresh", style = T.type.small, color = c.text2)
            }
            BudgetMode.RANGE -> {
                MoneyField(range, { range = it }, "Budget for these dates", large = true, onEnter = ::save)
                SettingRow("From ${rangeFrom.format(dayFmt)}", null, onClick = { picking = if (picking == "from") null else "from" }) { Chevron() }
                AnimatedVisibility(picking == "from") { MonthCalendar(rangeFrom) { rangeFrom = it; if (rangeTo < it) rangeTo = it; picking = null } }
                SettingRow("To ${rangeTo.format(dayFmt)}", null, onClick = { picking = if (picking == "to") null else "to" }) { Chevron() }
                AnimatedVisibility(picking == "to") { MonthCalendar(rangeTo) { if (it >= rangeFrom) rangeTo = it; picking = null } }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("What counts")
                    Segmented(listOf(false to "All spending", true to "Only chosen"), envelope, { envelope = it })
                    if (envelope) {
                        Field(tag, { tag = it }, Modifier.fillMaxWidth(), placeholder = "Entries tagged, like #trip")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Any account", rangeAccount == null) { rangeAccount = null }
                            view.accounts.forEach { a -> Chip(a.name, rangeAccount == a.id) { rangeAccount = a.id } }
                        }
                    }
                }
            }
        }
        if (mode == BudgetMode.MONTHLY || mode == BudgetMode.YEARLY) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Money left at the end of a month")
                Segmented(CarryMode.entries.map { it to it.label }, carry, { carry = it })
                Text(
                    when (carry) {
                        CarryMode.ASK -> "When a month ends, Home asks whether to add what's left to the next month."
                        CarryMode.ALWAYS -> "What's left is always added to the next month."
                        CarryMode.NEVER -> "Every month starts fresh with its planned amount."
                    },
                    style = T.type.small, color = c.text2,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Budget month starts on")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..28).forEach { d -> Chip(d.toString(), startDay == d) { startDay = d } }
                }
                val from = YearMonth.now().atDay(startDay)
                Text(
                    if (startDay == 1) "The 1st: budgets follow calendar months. Pick payday to run from one to the next."
                    else "The ${ordinal(startDay)}: the budget you're in runs ${from.format(DateTimeFormatter.ofPattern("d MMM"))} to ${from.plusMonths(1).minusDays(1).format(DateTimeFormatter.ofPattern("d MMM"))}.",
                    style = T.type.small, color = c.text2,
                )
            }
            SettingRow(
                "Extra income adds to the budget",
                if (extraIncome) "Gifts, bonuses and other income in categories that add to the budget raise that month's budget." else "Your budget stays the amount you set, whatever comes in.",
            ) { Toggle(extraIncome) { extraIncome = it } }
        }
        SettingRow("Over today, show the new daily limit", "Instead of how much over, what each day after today can be.") {
            Toggle(view.book.prefs.showNewDailyLimit) { on -> store.setPrefs { copy(showNewDailyLimit = on) } }
        }
        SettingRow("50 / 30 / 20", "Half of your income for needs, 30% for wants and 20% for savings.") { Toggle(buckets) { buckets = it } }
        if (buckets) {
            MoneyField(bucketIncome, { bucketIncome = it }, "Monthly income (empty: what you log, or your budget)")
            val income = Sum.minor(bucketIncome) ?: 0
            if (income > 0) Text("Needs ${money.formatWhole(income / 2)} · Wants ${money.formatWhole(income * 3 / 10)} · Savings ${money.formatWhole(income / 5)}", style = T.type.small, color = c.text2)
        }
    }
}
