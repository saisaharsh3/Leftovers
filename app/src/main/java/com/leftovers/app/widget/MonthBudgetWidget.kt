package com.leftovers.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.leftovers.app.LeftoversApp
import com.leftovers.app.MainActivity
import com.leftovers.app.data.TxType
import com.leftovers.app.util.Money
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** One 50/30/20 group in the widget: its name, how much of it is used, and whether it's over. */
private data class GroupState(val label: String, val percent: String, val used: Float, val over: Boolean)

private data class MonthState(
    val label: String, val amount: String, val caption: String, val used: Float?, val over: Boolean,
    /** In 50/30/20: Needs, Wants and Savings, shown in place of the one budget. */
    val groups: List<GroupState> = emptyList(),
)

/** Home-screen widget: what's left of this month's budget. */
class MonthBudgetWidget : GlanceAppWidget() {
    // Drawn at whichever fits: one row, or two and more.
    override val sizeMode = androidx.glance.appwidget.SizeMode.Responsive(setOf(SHORT_SIZE, TALL_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        val styles = (context.applicationContext as LeftoversApp).container.settings.settings.map { it.widgetStyle }
        val first = styles.first()
        provideContent {
            // Follows Settings at once, even while the widget is already showing.
            val style by styles.collectAsState(first)
            Content(context, state, WidgetLook.of(style))
        }
    }

    private suspend fun load(context: Context): MonthState {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val money = Money(s.currencyCode)
        val all = container.repository.getAllTransactions()
        val cycle = s.plan.cycleOf(LocalDate.now())
        val month = cycle.month
        val spent = all.filter { it.type == TxType.EXPENSE && !it.outOfBudget && it.date in cycle }.sumOf { it.amountMinor }
        val daysLeft = cycle.daysLeft()
        if (s.plan.buckets) {
            val items = all.filter { it.date in cycle }
            val budget = if (s.plan.isSet) s.plan.budgetFor(month, all) else 0L
            val base = com.leftovers.app.data.bucketBase(s.plan.bucketIncome, items.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }, budget)
            val saved = container.planning.allDeposits.first()
                .filter { it.epochDay in cycle.start.toEpochDay()..cycle.end.toEpochDay() }.sumOf { it.amountMinor }
            val uses = com.leftovers.app.data.bucketUse(base, items, container.repository.categories.first(), saved)
            return MonthState(
                "50/30/20", "", "$daysLeft days left", null, false,
                uses.map { u ->
                    val share = if (u.allowed > 0) u.used.toFloat() / u.allowed else 0f
                    GroupState(u.bucket.label, if (u.allowed > 0) "${(share * 100).toInt()}%" else money.formatWhole(u.used), share.coerceIn(0f, 1f), u.allowed > 0 && u.used > u.allowed)
                },
            )
        }
        if (!s.plan.isSet) return MonthState("Spent this month", money.formatWhole(spent), "Set a budget to track what's left", null, false)
        val budget = s.plan.budgetFor(month, all)
        val left = budget - spent
        return MonthState(
            if (left >= 0) "Left this month" else "Over budget",
            money.formatWhole(abs(left)),
            "${money.formatWhole(spent)} of ${money.formatWhole(budget)} · $daysLeft days left",
            if (budget > 0) (spent.toFloat() / budget).coerceIn(0f, 1f) else 1f,
            left < 0,
        )
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, state: MonthState, look: WidgetLook) {
        val white = ColorProvider(look.text)
        val muted = ColorProvider(look.muted)
        val short = androidx.glance.LocalSize.current.height < TALL_SIZE.height
        // In the middle, however tall the widget is made.
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                // Glass, dark or light, as chosen in Settings.
                .background(androidx.glance.ImageProvider(look.background))
                .padding(horizontal = 18.dp, vertical = if (short) 8.dp else 18.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
        ) {
            if (state.groups.isNotEmpty()) Groups(state, look, white, muted, short) else OneBudget(state, look, white, muted, short)
        }
    }
}

/** What's left of the one budget: the amount, a bar, and how much of it is spent. */
@androidx.compose.runtime.Composable
private fun OneBudget(state: MonthState, look: WidgetLook, white: ColorProvider, muted: ColorProvider, short: Boolean) {
    // One row high: what's left, with how much is spent beside it.
    if (short) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = androidx.glance.layout.Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(state.label, style = TextStyle(color = muted, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(state.amount, style = TextStyle(color = if (state.over) ColorProvider(look.negative) else white, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Text(state.caption.substringBefore(" · "), style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
        }
        return
    }
    Text(state.label, style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    Spacer(GlanceModifier.height(4.dp))
    Text(
        state.amount,
        style = TextStyle(color = if (state.over) ColorProvider(look.negative) else white, fontSize = 30.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
    state.used?.let { used ->
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
            progress = used,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(if (state.over) look.negative else look.bar),
            backgroundColor = ColorProvider(look.track),
        )
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(state.caption, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
}

/**
 * Needs, Wants and Savings side by side, so all three fit the widget's two rows: each one's share of its part used,
 * and a bar, red once it's over.
 */
@androidx.compose.runtime.Composable
private fun Groups(state: MonthState, look: WidgetLook, white: ColorProvider, muted: ColorProvider, short: Boolean) {
    // One row high: the three, without the heading.
    if (!short) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(state.label, style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
            Text(state.caption, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.height(10.dp))
    }
    Row(GlanceModifier.fillMaxWidth()) {
        state.groups.forEachIndexed { i, g ->
            if (i > 0) Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(g.label, style = TextStyle(color = muted, fontSize = if (short) 11.sp else 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(g.percent, style = TextStyle(color = if (g.over) ColorProvider(look.negative) else white, fontSize = if (short) 16.sp else 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(if (short) 3.dp else 6.dp))
                LinearProgressIndicator(
                    progress = g.used,
                    modifier = GlanceModifier.fillMaxWidth().height(if (short) 3.dp else 5.dp),
                    color = ColorProvider(if (g.over) look.negative else look.bar),
                    backgroundColor = ColorProvider(look.track),
                )
            }
        }
    }
}

private val SHORT_SIZE = androidx.compose.ui.unit.DpSize(180.dp, 56.dp)
private val TALL_SIZE = androidx.compose.ui.unit.DpSize(180.dp, 110.dp)

class MonthBudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthBudgetWidget()
}
