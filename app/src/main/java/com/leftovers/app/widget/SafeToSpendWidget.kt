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
import androidx.glance.layout.height
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
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private data class WidgetState(val label: String, val amount: String, val caption: String, val over: Boolean)

/** Home-screen widget: what's safe to spend today, with a quick add button. */
class SafeToSpendWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { Content(context, state) }
    }

    private suspend fun load(context: Context): WidgetState {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val money = Money(s.currencyCode)
        val all = container.repository.getAllTransactions()
        val month = YearMonth.now()
        val items = all.filter { YearMonth.from(it.date) == month }
        val today = LocalDate.now()
        val spentToday = items.filter { it.type == TxType.EXPENSE && it.epochDay == today.toEpochDay() }.sumOf { it.amountMinor }
        val budget = dailyBudget(
            s.plan.budgetFor(month, all),
            items,
            container.planning.recurring.first().pendingExpenses(month),
        )
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        return when {
            budget == null -> WidgetState("Spent today", money.formatWhole(spentToday), "Tap + to log a spend", false)
            budget.leftToday < 0 -> WidgetState("Over today", money.formatWhole(abs(budget.leftToday)), "$daysLeft days left this month", true)
            else -> WidgetState(
                "Safe to spend today",
                money.formatWhole(budget.leftToday),
                "${money.formatWhole(spentToday)} of ${money.formatWhole(budget.dailyLimit)} spent",
                false,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, state: WidgetState) {
        val white = ColorProvider(Color(0xFFF5F5F7))
        val muted = ColorProvider(Color(0xFFA1A1AA))
        val accent = Color(0xFFCDF46F)
        Row(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(Color(0xFF15151B))
                .padding(18.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(state.label, style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    state.amount,
                    style = TextStyle(
                        color = if (state.over) ColorProvider(Color(0xFFF58A8A)) else white,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(state.caption, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
            }
            Box(
                GlanceModifier
                    .size(52.dp)
                    .cornerRadius(26.dp)
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
                Text("+", style = TextStyle(color = ColorProvider(Color(0xFF151A04)), fontSize = 28.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}
