package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.CategoryTotal
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.categoryTotals
import com.leftovers.app.data.spendingTrends
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Card
import com.leftovers.desktop.ui.components.DayBars
import com.leftovers.desktop.ui.components.Donut
import com.leftovers.desktop.ui.components.Empty
import com.leftovers.desktop.ui.components.EntryRow
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.IconBadge
import com.leftovers.desktop.ui.components.IconButton
import com.leftovers.desktop.ui.components.Meter
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.ShareBar
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** One month of spending or income, as the phone's Insights works it out. */
private data class Insights(
    val total: Long,
    val previous: Long,
    val budget: Long,
    val categories: List<CategoryTotal>,
    val daily: List<Long>,
    val items: List<TransactionItem>,
    val max: TransactionItem?,
    val trends: List<com.leftovers.app.data.Trend>,
)

@Composable
fun InsightsScreen(view: View) {
    val c = T.c
    val money = LocalMoney.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    val isExpense = type == TxType.EXPENSE
    val s = remember(view, month, type) {
        val all = view.entries
        val plan = view.book.plan
        val current = all.filter { YearMonth.from(it.date) == month }
        val ofType = current.filter { it.type == type }
        val daily = LongArray(month.lengthOfMonth())
        ofType.forEach { daily[it.date.dayOfMonth - 1] += it.amountMinor }
        Insights(
            total = current.totalOf(type),
            previous = all.filter { YearMonth.from(it.date) == month.minusMonths(1) }.totalOf(type),
            budget = if (plan.isSet && plan.cycle(month).start == month.atDay(1)) plan.budgetFor(month, all) else 0L,
            categories = current.categoryTotals(type),
            daily = daily.toList(),
            items = ofType,
            max = ofType.maxByOrNull { it.amountMinor },
            trends = spendingTrends(all, month, type, money::formatWhole),
        )
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Screen(
        "Insights",
        actions = {
            Segmented(listOf(TxType.EXPENSE to "Spending", TxType.INCOME to "Income"), type, { type = it })
            Spacer(Modifier.width(4.dp))
            MonthArrows(month) { month = it }
        },
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight(), padding = 26.dp) {
                Text(if (isExpense) "Spent in $monthName" else "Income in $monthName", style = T.type.bodyStrong, color = c.text2)
                Spacer(Modifier.height(8.dp))
                com.leftovers.desktop.ui.components.RollingText(money.formatWhole(s.total), T.type.hero, c.text)
                Spacer(Modifier.height(6.dp))
                if (s.previous > 0) {
                    val diff = s.total - s.previous
                    val pct = (kotlin.math.abs(diff) * 100 / s.previous)
                    val good = if (isExpense) diff <= 0 else diff >= 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (diff >= 0) Lucide.TrendingUp else Lucide.TrendingDown, null, Modifier.size(15.dp), tint = if (good) c.positive else c.negative)
                        Spacer(Modifier.width(6.dp))
                        Text("$pct% ${if (diff >= 0) "more" else "less"} than last month's ${money.formatWhole(s.previous)}", style = T.type.small, color = c.text2)
                    }
                } else Text("No ${if (isExpense) "spending" else "income"} logged last month", style = T.type.small, color = c.text3)
                Spacer(Modifier.weight(1f).height(18.dp))
                if (isExpense && s.budget > 0) {
                    Meter(s.total.toFloat() / s.budget, height = 8.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (s.total <= s.budget) "${money.formatWhole(s.budget - s.total)} left of ${money.formatWhole(s.budget)}"
                        else "${money.formatWhole(s.total - s.budget)} over the budget of ${money.formatWhole(s.budget)}",
                        style = T.type.small, color = if (s.total > s.budget) c.negative else c.text2,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val days = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
                    Tile("Daily average", money.formatWhole(s.total / days.coerceAtLeast(1)), Modifier.weight(1f))
                    Tile(if (isExpense) "Biggest spend" else "Biggest income", s.max?.let { money.formatWhole(it.amountMinor) } ?: "None", Modifier.weight(1f), s.max?.note?.ifBlank { s.max.categoryName })
                    Tile("Entries", s.items.size.toString(), Modifier.weight(1f))
                }
            }
            Card(Modifier.weight(1.3f).fillMaxHeight(), padding = 26.dp) {
                Text("Day by day", style = T.type.bodyStrong, color = c.text2)
                Spacer(Modifier.height(4.dp))
                Text("Point at a bar to see that day", style = T.type.small, color = c.text3)
                Spacer(Modifier.height(14.dp))
                val todayIndex = if (month == YearMonth.now()) LocalDate.now().dayOfMonth - 1 else null
                val dailyBudget = if (isExpense && s.budget > 0) s.budget / month.lengthOfMonth() else null
                DayBars(
                    s.daily, if (isExpense) c.text else c.positive, Modifier.fillMaxWidth().weight(1f).height(200.dp), todayIndex, dailyBudget,
                ) { i -> "${month.atDay(i + 1).friendlyLabel()} · ${money.formatWhole(s.daily[i])}" }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("1", style = T.type.small, color = c.text3, modifier = Modifier.weight(1f))
                    Text("${month.lengthOfMonth() / 2}", style = T.type.small, color = c.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("${month.lengthOfMonth()}", style = T.type.small, color = c.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
        if (s.trends.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), padding = 22.dp) {
                Text(if (isExpense) "What changed" else "How your income compares", style = T.type.heading, color = c.text)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.trends.forEach { t ->
                        Row(verticalAlignment = Alignment.Top) {
                            val tone = when (t.good) { true -> c.positive; false -> c.negative; null -> c.text2 }
                            Box(Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(if (t.down) Lucide.TrendingDown else Lucide.TrendingUp, null, Modifier.size(13.dp), tint = tone)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(t.text, style = T.type.body, color = c.text)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Card(Modifier.weight(1.3f), padding = 22.dp) {
                Text("By category", style = T.type.heading, color = c.text)
                Spacer(Modifier.height(14.dp))
                if (s.categories.isEmpty()) Empty(Lucide.ChartPie, "Nothing this month", "Entries you add show here by category.")
                else Categories(s, month)
            }
            Card(Modifier.weight(1f), padding = 22.dp) {
                Text("Calendar", style = T.type.heading, color = c.text)
                Spacer(Modifier.height(14.dp))
                Calendar(month, s.daily, isExpense)
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, line: String? = null) {
    val c = T.c
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.raised).padding(12.dp)) {
        Text(label, style = T.type.small, color = c.text2, maxLines = 1)
        Text(value, style = T.type.heading, color = c.text, maxLines = 1)
        if (line != null) Text(line, style = T.type.small, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Categories(s: Insights, month: YearMonth) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    var hover by remember { mutableStateOf<Int?>(null) }
    var open by remember(month) { mutableStateOf<Long?>(null) }
    val total = s.categories.sumOf { it.totalMinor }.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Donut(s.categories.map { it.totalMinor to Color(it.color) }, hover, { hover = it }, Modifier.size(180.dp))
            AnimatedContent(hover, transitionSpec = { fadeIn(Motion.settle()) togetherWith fadeOut(Motion.quick()) }, label = "centre") { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val cat = h?.let { s.categories.getOrNull(it) }
                    Text(cat?.name ?: "In all", style = T.type.small, color = c.text2, maxLines = 1, modifier = Modifier.widthIn(max = 110.dp), overflow = TextOverflow.Ellipsis)
                    Text(money.formatWhole(cat?.totalMinor ?: s.total), style = T.type.heading, color = c.text)
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            s.categories.forEachIndexed { i, cat ->
                val lit by animateFloatAsState(if (hover == null || hover == i) 1f else 0.45f, Motion.quick())
                Column(Modifier.alpha(lit)) {
                    HoverRow({ open = if (open == cat.categoryId) null else cat.categoryId }, Modifier.fillMaxWidth()) {
                        IconBadge(cat.emoji, cat.color, 30.dp, app.store.book.icons)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row {
                                Text(cat.name, style = T.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${cat.totalMinor * 100 / total}%", style = T.type.small, color = c.text3)
                                Spacer(Modifier.width(10.dp))
                                Text(money.formatWhole(cat.totalMinor), style = T.type.bodyStrong, color = c.text)
                            }
                            ShareBar(cat.totalMinor.toFloat() / total, Color(cat.color))
                        }
                        val turn by animateFloatAsState(if (open == cat.categoryId) 90f else 0f, Motion.quick())
                        Spacer(Modifier.width(6.dp))
                        Icon(Lucide.ChevronRight, null, Modifier.size(15.dp).rotate(turn), tint = c.text3)
                    }
                    AnimatedVisibility(open == cat.categoryId, enter = expandVertically(Motion.settle()) + fadeIn(), exit = shrinkVertically(Motion.quick()) + fadeOut()) {
                        Column(Modifier.padding(start = 20.dp)) {
                            s.items.filter { it.categoryId == cat.categoryId }.forEach { item ->
                                androidx.compose.runtime.key(item.id) { EntryRow(item, { app.edit(item.id) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Each day coloured by how it compares with the month's usual day: lighter, typical or heavier. */
@Composable
private fun Calendar(month: YearMonth, daily: List<Long>, isExpense: Boolean) {
    val c = T.c
    val today = LocalDate.now()
    val days = daily.filter { it > 0 }
    val average = if (days.isEmpty()) 0L else days.sum() / days.size
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val cells: List<Int?> = List(lead) { null } + (1..month.lengthOfMonth()).toList()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            (0 until 7).forEach { i ->
                Text(first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = T.type.label, color = c.text3, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day == null) return@Box
                        val v = daily.getOrElse(day - 1) { 0L }
                        val tone = when {
                            v == 0L -> null
                            !isExpense -> c.positive
                            v <= average * 3 / 4 -> c.positive
                            v <= average * 5 / 4 -> c.warning
                            else -> c.negative
                        }
                        val shape = RoundedCornerShape(9.dp)
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(tone?.copy(alpha = 0.2f) ?: Color.Transparent)
                                .then(if (month.atDay(day) == today) Modifier.border(1.5.dp, c.text, shape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Text(day.toString(), style = T.type.small, color = tone ?: c.text3) }
                    }
                }
            }
        }
        if (isExpense) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.positive, "Lighter day"); Legend(c.warning, "Typical"); Legend(c.negative, "Heavier")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = T.type.small, color = T.c.text2)
    }
}

/** ‹ November › : the month slides the way it moved. */
@Composable
fun MonthArrows(month: YearMonth, onChange: (YearMonth) -> Unit) {
    val c = T.c
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.raised).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Lucide.ChevronLeft, { onChange(month.minusMonths(1)) }, size = 28.dp, tint = c.text)
        AnimatedContent(
            month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(Motion.offset) { if (forward) it / 3 else -it / 3 } + fadeIn(Motion.settle()))
                    .togetherWith(slideOutHorizontally(Motion.offset) { if (forward) -it / 3 else it / 3 } + fadeOut(Motion.quick()))
            },
            label = "month",
        ) { m ->
            Text(
                m.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + (if (m.year != YearMonth.now().year) " ${m.year}" else ""),
                style = T.type.bodyStrong, color = c.text, modifier = Modifier.widthIn(min = 100.dp).padding(horizontal = 6.dp), textAlign = TextAlign.Center,
            )
        }
        val canNext = month < YearMonth.now()
        IconButton(Lucide.ChevronRight, { if (canNext) onChange(month.plusMonths(1)) }, size = 28.dp, tint = if (canNext) c.text else c.text3)
    }
}
