package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.RecurringItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.findLikelySubscriptions
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.Sum
import com.leftovers.desktop.data.View
import com.leftovers.desktop.data.ordinal
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Card
import com.leftovers.desktop.ui.components.Empty
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.RollingText
import com.leftovers.desktop.ui.components.SectionHeader
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.SettingRow
import com.leftovers.desktop.ui.components.SidePanel
import com.leftovers.desktop.ui.components.Toggle
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class Template(val name: String, val icon: String, val categoryName: String, val type: TxType = TxType.EXPENSE)

private val templates = listOf(
    Template("Netflix", "tv", "Entertainment"), Template("Spotify", "music", "Entertainment"), Template("Rent", "house", "Rent"),
    Template("Electricity", "zap", "Bills & Utilities"), Template("Mobile plan", "smartphone", "Bills & Utilities"),
    Template("Internet", "wifi", "Bills & Utilities"), Template("Gym", "dumbbell", "Health"),
    Template("Salary", "briefcase", "Salary", TxType.INCOME), Template("Freelance", "laptop", "Freelance", TxType.INCOME),
    Template("Rent received", "house", "Other", TxType.INCOME), Template("Interest", "landmark", "Investments", TxType.INCOME),
    Template("Dividends", "trending-up", "Investments", TxType.INCOME), Template("Pocket money", "gift", "Gifts", TxType.INCOME),
    Template("Pension", "piggy-bank", "Other", TxType.INCOME),
)

private val cycles = listOf(1 to "Monthly", 3 to "Every 3 months", 6 to "Every 6 months", 12 to "Yearly")

fun scheduleLine(item: RecurringItem): String = when {
    !item.active -> "Paused"
    item.everyMonths == 12 -> "Yearly · next ${item.toRecurring().nextChargeDate().friendlyLabel()}"
    item.everyMonths > 1 -> "Every ${item.everyMonths} months · next ${item.toRecurring().nextChargeDate().friendlyLabel()}"
    else -> "Every ${ordinal(item.dayOfMonth)} · next ${item.toRecurring().nextChargeDate().friendlyLabel()}"
}

@Composable
fun SubscriptionsScreen(view: View) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var calendarMonth by remember { mutableStateOf(YearMonth.now()) }
    val income = type == TxType.INCOME
    // One-offs scheduled for a later day aren't subscriptions; they're on Home, under Upcoming.
    val shown = view.recurring.filter { it.type == type && !it.once }
    val active = shown.filter { it.active }
    val monthly = active.sumOf { it.toRecurring().monthlyShareMinor }
    val suggestions = remember(view) { if (income) emptyList() else findLikelySubscriptions(view.entries, view.recurring.filterNot { it.once }, view.book.prefs.dismissed).take(3) }
    fun new(name: String = "", amount: Long = 0, categoryId: Long? = null, day: Int = LocalDate.now().dayOfMonth) = Recurring(
        name = name, amountMinor = amount, type = type,
        categoryId = categoryId ?: view.book.categories.firstOrNull { it.type == type && !it.hidden }?.id ?: 0,
        dayOfMonth = day, startMonth = YearMonth.now().toString(), accountId = view.book.prefs.defaultAccountId.takeIf { it != 0L },
    )
    androidx.compose.runtime.LaunchedEffect(app.intent) { if (app.take("new")) editing = new() }
    Screen(
        "Subscriptions",
        subtitle = "Logged by themselves on their day",
        actions = {
            Segmented(listOf(TxType.EXPENSE to "Payments", TxType.INCOME to "Income"), type, { type = it })
            Button(if (income) "Add recurring income" else "Add subscription", { editing = new() }, kind = ButtonKind.Primary, icon = Lucide.Plus)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.fillMaxWidth(), padding = 26.dp) {
                    Text(if (income) "Coming in every month" else "Going out every month", style = T.type.bodyStrong, color = c.text2)
                    Spacer(Modifier.height(8.dp))
                    RollingText(money.formatWhole(monthly), T.type.hero, if (income) c.positive else c.text)
                    Spacer(Modifier.height(4.dp))
                    val next = active.minByOrNull { it.toRecurring().nextChargeDate() }
                    Text(
                        listOfNotNull(
                            "${active.size} active",
                            next?.let { "next: ${it.name}, ${it.toRecurring().nextChargeDate().friendlyLabel()}" },
                            (money.formatWhole(monthly * 12) + " a year").takeIf { monthly > 0 },
                        ).joinToString(" · "),
                        style = T.type.small, color = c.text2,
                    )
                }
                suggestions.forEach { s ->
                    Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Looks like a subscription", style = T.type.small, color = c.text2)
                                Text("${s.name} · ${money.format(s.amountMinor)} around the ${ordinal(s.dayOfMonth)}", style = T.type.bodyStrong, color = c.text)
                            }
                            Button("No", { store.setPrefs { copy(dismissed = dismissed + s.key) } }, kind = ButtonKind.Quiet)
                            Spacer(Modifier.width(6.dp))
                            Button("Add it", {
                                val month = YearMonth.now()
                                // This month's was already logged by hand, so it starts next month.
                                store.saveRecurring(new(s.name, s.amountMinor, s.categoryId, s.dayOfMonth).copy(lastPostedMonth = month.toString()))
                                app.tell("${s.name} added")
                            }, kind = ButtonKind.Primary, icon = Lucide.Plus)
                        }
                    }
                }
                Card(Modifier.fillMaxWidth(), padding = 6.dp) {
                    if (shown.isEmpty()) {
                        Empty(
                            Lucide.Repeat, if (income) "No recurring income yet" else "No subscriptions yet",
                            if (income) "Add your salary and it's logged by itself every month." else "Add rent, streaming or phone bills: they're logged on their day and kept out of your daily allowance.",
                        )
                    }
                    shown.forEach { item ->
                        androidx.compose.runtime.key(item.id) {
                            HoverRow({ editing = item.toRecurring() }, Modifier.fillMaxWidth().alpha(if (item.active) 1f else 0.55f)) {
                                IconBadge(item.categoryEmoji, item.categoryColor, 36.dp, view.book.icons)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        scheduleLine(item) + (if (item.manual && item.active) " · waits for its payment" else ""),
                                        style = T.type.small, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    if (item.ratePercent != null) "${item.ratePercent}% a year" else (if (income) "+" else "") + money.format(item.amountMinor),
                                    style = T.type.bodyStrong, color = if (income) c.positive else c.text,
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(if (item.active) Lucide.Pause else Lucide.Play, { setActive(store, item, !item.active) }, tooltip = if (item.active) "Pause" else "Resume")
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Schedule")
                ScheduleCalendar(calendarMonth, { calendarMonth = it }, active)
            }
        }
    }
    val e = editing
    SidePanel(e != null, if (e?.id == 0L) (if (income) "New recurring income" else "New subscription") else e?.name.orEmpty(), { editing = null }, width = 500.dp) {
        if (e != null) androidx.compose.runtime.key(e.id, e.type) { RecurringEditor(e, view) { editing = null } }
    }
}

private fun setActive(store: com.leftovers.desktop.data.Store, item: RecurringItem, active: Boolean) {
    // Resuming shouldn't back-charge the months it was paused for.
    val updated = if (active) item.toRecurring().copy(active = true, lastPostedMonth = YearMonth.now().minusMonths(1).toString())
    else item.toRecurring().copy(active = false)
    store.saveRecurring(updated)
    store.postDueRecurring()
}

/** The month with each charge on its day, and what goes out in it. */
@Composable
private fun ScheduleCalendar(month: YearMonth, onMonth: (YearMonth) -> Unit, active: List<RecurringItem>) {
    val c = T.c
    val money = LocalMoney.current
    val today = LocalDate.now()
    val due = active.filter { it.toRecurring().isDueIn(month) }
    val byDay = due.groupBy { it.toRecurring().chargeDate(month).dayOfMonth }
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val cells: List<Int?> = List(lead) { null } + (1..month.lengthOfMonth()).toList()
    Card(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(Lucide.ChevronLeft, { onMonth(month.minusMonths(1)) }, size = 28.dp, tint = c.text)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + if (month.year != today.year) " ${month.year}" else "", style = T.type.bodyStrong, color = c.text)
                Text(if (due.isEmpty()) "Nothing due" else "${money.formatWhole(due.sumOf { it.amountMinor })} · ${due.size} due", style = T.type.small, color = c.text2)
            }
            IconButton(Lucide.ChevronRight, { onMonth(month.plusMonths(1)) }, size = 28.dp, tint = c.text)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            (0 until 7).forEach { i ->
                Text(first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = T.type.label, color = c.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day == null) return@Box
                        val items = byDay[day].orEmpty()
                        val isToday = month.atDay(day) == today
                        Column(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)).background(if (items.isNotEmpty()) c.raised else Color.Transparent),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(day.toString(), style = if (isToday) T.type.bodyStrong else T.type.small, color = if (isToday) c.text else if (items.isEmpty()) c.text3 else c.text)
                            if (items.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    items.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(Color(it.categoryColor))) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (due.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            due.sortedBy { it.toRecurring().chargeDate(month) }.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(r.categoryColor)))
                    Spacer(Modifier.width(10.dp))
                    Text(r.toRecurring().chargeDate(month).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")), style = T.type.small, color = c.text3, modifier = Modifier.width(52.dp))
                    Text(r.name, style = T.type.small, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(money.format(r.amountMinor), style = T.type.small, color = c.text2)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditor(initial: Recurring, view: View, onDone: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val c = T.c
    val money = LocalMoney.current
    val isNew = initial.id == 0L
    val today = LocalDate.now()
    val now = YearMonth.now()
    var name by remember { mutableStateOf(initial.name) }
    var amountText by remember { mutableStateOf(minorText(initial.amountMinor)) }
    var type by remember { mutableStateOf(initial.type) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var day by remember { mutableStateOf(initial.dayOfMonth) }
    var every by remember { mutableStateOf(initial.everyMonths.takeIf { e -> cycles.any { it.first == e } } ?: 1) }
    var billMonth by remember { mutableStateOf(if (initial.everyMonths > 1) YearMonth.from(initial.nextChargeDate()) else now) }
    var chargeThisMonth by remember { mutableStateOf(true) }
    var skipNext by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(initial.manual) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var interest by remember { mutableStateOf(initial.ratePercent != null) }
    var rateText by remember { mutableStateOf(initial.ratePercent.orEmpty()) }
    var askDelete by remember { mutableStateOf(false) }
    val accounts = view.accounts
    val interestAccount = accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()
    val interestOn = interest && type == TxType.INCOME
    val estimate = if (interestOn) initial.copy(ratePercent = rateText.takeIf { it.isNotBlank() }, everyMonths = every).interestOn(interestAccount?.balanceMinor ?: 0L) else null
    val amount = estimate ?: Sum.minor(amountText) ?: 0L
    val monthChoices = (0 until if (every == 12) 12 else every).map { now.plusMonths(it.toLong()) }
    val nextMonth = billMonth.takeIf { it in monthChoices } ?: now
    val dueThisMonth = every == 1 || nextMonth == now
    val dayPassed = dueThisMonth && day <= today.dayOfMonth
    val valid = name.isNotBlank() && (amount > 0 || (interestOn && (rateText.toBigDecimalOrNull()?.signum() ?: 0) > 0 && interestAccount != null)) &&
        view.book.categories.any { it.id == categoryId && it.type == type }

    fun save() {
        if (!valid) return
        val start = if (every > 1) {
            var m = nextMonth
            val loggedAlready = initial.lastPostedMonth != null && initial.lastPostedMonth!! >= m.toString()
            if (loggedAlready || (m == now && dayPassed && isNew && !chargeThisMonth)) m = m.plusMonths(every.toLong())
            m.toString()
        } else when {
            !isNew && initial.everyMonths == 1 -> initial.startMonth
            !isNew -> now.toString()
            dayPassed && !chargeThisMonth -> now.plusMonths(1).toString()
            else -> now.toString()
        }
        var saved = initial.copy(
            name = name.trim(), amountMinor = amount, type = type, categoryId = categoryId, dayOfMonth = day, startMonth = start, everyMonths = every,
            accountId = if (interestOn) accountId ?: interestAccount?.id else accountId,
            ratePercent = rateText.takeIf { interestOn && it.isNotBlank() }, manual = manual && !interestOn,
        )
        if (skipNext) saved = saved.copy(lastPostedMonth = YearMonth.from(saved.nextChargeDate()).toString())
        store.saveRecurring(saved)
        // Logs this month's charge straight away if it's already due.
        val posted = store.postDueRecurring()
        app.tell(if (posted.isNotEmpty()) "${saved.name} saved · this month's logged" else "${saved.name} saved")
        onDone()
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (isNew) {
            Segmented(listOf(TxType.EXPENSE to "Payment", TxType.INCOME to "Income"), type, {
                type = it
                categoryId = view.book.categories.firstOrNull { cat -> cat.type == it && !cat.hidden }?.id ?: categoryId
            })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.filter { it.type == type }.forEach { t ->
                    Chip(t.name, name == t.name) {
                        name = t.name
                        view.book.categories.firstOrNull { it.type == type && it.name.equals(t.categoryName, true) }?.let { categoryId = it.id }
                        if (t.name == "Interest") interest = true
                    }
                }
            }
        }
        Field(name, { name = it.take(30) }, Modifier.fillMaxWidth(), placeholder = if (type == TxType.INCOME) "Name, like Salary" else "Name, like Netflix", onEnter = ::save)
        if (type == TxType.INCOME) {
            SettingRow("Interest on a balance", "A yearly rate on an account's balance, worked out each time it's logged.") { Toggle(interest) { interest = it } }
        }
        if (interestOn) {
            Field(rateText, { t -> if (t.length <= 6 && t.all { it.isDigit() || it == '.' }) rateText = t }, Modifier.fillMaxWidth(), placeholder = "Interest rate, % a year")
            estimate?.takeIf { it > 0 }?.let {
                Text("About ${money.format(it)} each time at today's balance${interestAccount?.let { a -> " of ${a.name}" }.orEmpty()}.", style = T.type.small, color = c.text2)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MoneyField(amountText, { amountText = it }, when (every) { 1 -> "Amount every month"; 12 -> "Amount every year"; else -> "Amount every $every months" }, large = true, onEnter = ::save)
                if (every > 1 && amount > 0) Text("About ${money.format(amount / every)} a month", style = T.type.small, color = c.text2)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("How often")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cycles.forEach { (n, label) -> Chip(label, every == n) { every = n } }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                view.book.categories.filter { it.type == type && (!it.hidden || it.id == categoryId) }.forEach { cat ->
                    Chip(cat.name, cat.id == categoryId) { categoryId = cat.id }
                }
            }
        }
        if (accounts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (type == TxType.INCOME) "Paid into" else "Paid from")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!interestOn) Chip("Default account", accountId == null) { accountId = null }
                    accounts.forEach { a -> Chip(a.name, accountId == a.id || (interestOn && accountId == null && a.id == interestAccount?.id)) { accountId = a.id } }
                }
            }
        }
        if (every > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (every == 12) "Month" else "Next charge")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    monthChoices.sortedBy { if (every == 12) it.monthValue else 0 }.forEach { m ->
                        Chip(m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), m == nextMonth) { billMonth = m }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val monthName = nextMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val dayText = if (day == 31) "the last day of" else day.toString()
            SectionLabel("Day")
            Text(
                "${if (type == TxType.INCOME) "Received" else "Deducted"} on " + when (every) {
                    1 -> "the ${if (day == 31) "last day" else ordinal(day)} of every month"
                    12 -> "$dayText $monthName every year"
                    else -> "$dayText $monthName, then every $every months"
                },
                style = T.type.small, color = c.text2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 7) {
                (1..31).forEach { d ->
                    val on = d == day
                    Box(
                        Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(8.dp)).background(if (on) c.accent else c.raised).pressable(scaleTo = 0.92f) { day = d },
                        contentAlignment = Alignment.Center,
                    ) { Text(d.toString(), style = T.type.small, color = if (on) c.onAccent else c.text) }
                }
            }
        }
        AnimatedVisibility(isNew && dayPassed) {
            SettingRow(
                "Log ${if (every == 1) "this month's" else "this"} ${if (type == TxType.EXPENSE) "payment" else "income"} now",
                if (day == today.dayOfMonth) "The ${ordinal(day)} is today" else "The ${ordinal(day)} has already passed this month",
            ) { Toggle(chargeThisMonth) { chargeThisMonth = it } }
        }
        if (!interestOn) {
            SettingRow(
                "Wait for its payment",
                if (manual) "Shows as due on Home on its day, to mark ${if (type == TxType.INCOME) "received" else "paid"} when it's really paid, for what was paid."
                else "Off: logged by itself on its day.",
            ) { Toggle(manual) { manual = it } }
        }
        if (!isNew && initial.active) {
            SettingRow("Skip the next one", "${initial.nextChargeDate().friendlyLabel()} won't be logged; it carries on after.") { Toggle(skipNext) { skipNext = it } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isNew) {
                if (askDelete) Button("Delete · past entries stay", {
                    store.deleteRecurring(initial)
                    app.tell("${initial.name} deleted") { store.saveRecurring(initial) }
                    onDone()
                }, kind = ButtonKind.Danger)
                else Button("Delete", { askDelete = true }, kind = ButtonKind.Quiet)
            }
            Spacer(Modifier.weight(1f))
            Button("Save", ::save, kind = ButtonKind.Primary, enabled = valid)
        }
    }
}
