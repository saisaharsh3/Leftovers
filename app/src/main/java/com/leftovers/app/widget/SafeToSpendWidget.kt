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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.leftovers.app.LeftoversApp
import com.leftovers.app.MainActivity
import com.leftovers.app.data.TxType
import com.leftovers.app.data.dailyBudget
import com.leftovers.app.data.pendingExpenses
import com.leftovers.app.util.Money
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** [used]: how much of today's allowance is spent, for its bar; null without a budget. */
private data class WidgetState(val label: String, val amount: String, val caption: String, val over: Boolean, val used: Float? = null)

/** Home-screen widget: what's safe to spend today, with a quick add button. */
class SafeToSpendWidget : GlanceAppWidget() {
    // Drawn at whichever fits: one row, or two and more.
    override val sizeMode = androidx.glance.appwidget.SizeMode.Responsive(setOf(SHORT, TALL))

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

    private suspend fun load(context: Context): WidgetState {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val money = Money(s.currencyCode)
        val all = container.repository.getAllTransactions()
        val today = LocalDate.now()
        val cycle = s.plan.cycleOf(today)
        val items = all.filter { it.date in cycle }
        val spentToday = items.filter { it.type == TxType.EXPENSE && it.epochDay == today.toEpochDay() }.sumOf { it.amountMinor }
        val budget = dailyBudget(
            s.plan.budgetFor(cycle.month, all),
            items,
            if (s.plan.envelope) 0L else container.planning.recurring.first().pendingExpenses(cycle),
            periodEnd = cycle.end,
        )
        val daysLeft = cycle.daysLeft(today)
        return when {
            budget == null -> WidgetState("Spent today", money.formatWhole(spentToday), "Tap + to log a spend", false)
            budget.monthUsedUp -> WidgetState("Over budget", money.formatWhole(abs(budget.monthLeft)), "$daysLeft days left this month", true, 1f)
            budget.leftToday < 0 -> WidgetState("Over today", money.formatWhole(abs(budget.leftToday)), "$daysLeft days left this month", true, 1f)
            else -> WidgetState(
                "Safe to spend today",
                money.formatWhole(budget.leftToday),
                "${money.formatWhole(spentToday)} of ${money.formatWhole(budget.dailyLimit)} spent",
                false,
                if (budget.dailyLimit > 0) (spentToday.toFloat() / budget.dailyLimit).coerceIn(0f, 1f) else 0f,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, state: WidgetState, look: WidgetLook) {
        val white = ColorProvider(look.text)
        val muted = ColorProvider(look.muted)
        // The + keeps the app's accent in every style; bars take the style's own green.
        val accent = Color(0xFFCDF46F)
        // One row high: just what's safe to spend and the +.
        val short = androidx.glance.LocalSize.current.height < TALL.height
        Row(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                // Glass, dark or light, as chosen in Settings.
                .background(androidx.glance.ImageProvider(look.background))
                .padding(horizontal = 18.dp, vertical = if (short) 8.dp else 18.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(state.label, style = TextStyle(color = muted, fontSize = if (short) 11.sp else 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                if (!short) Spacer(GlanceModifier.height(4.dp))
                Text(
                    state.amount,
                    style = TextStyle(
                        color = if (state.over) ColorProvider(look.negative) else white,
                        fontSize = if (short) 22.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                // Today's allowance, as a bar: how much of it is gone.
                if (!short) state.used?.let { used ->
                    Spacer(GlanceModifier.height(8.dp))
                    androidx.glance.appwidget.LinearProgressIndicator(
                        progress = used,
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                        color = ColorProvider(if (state.over) look.negative else look.bar),
                        backgroundColor = ColorProvider(look.track),
                    )
                }
                if (!short) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(state.caption, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(14.dp))
            Box(
                GlanceModifier
                    .size(if (short) 40.dp else 52.dp)
                    .cornerRadius(if (short) 20.dp else 26.dp)
                    .background(accent)
                    .clickable(
                        actionStartActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_ADD, true)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = ColorProvider(Color(0xFF151A04)), fontSize = if (short) 22.sp else 28.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    private companion object {
        val SHORT = androidx.compose.ui.unit.DpSize(180.dp, 56.dp)
        val TALL = androidx.compose.ui.unit.DpSize(180.dp, 110.dp)
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}
