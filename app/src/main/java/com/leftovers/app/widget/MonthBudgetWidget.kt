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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.leftovers.app.LeftoversApp
import com.leftovers.app.MainActivity
import com.leftovers.app.data.TxType
import com.leftovers.app.util.Money
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private data class MonthState(val label: String, val amount: String, val caption: String, val used: Float?, val over: Boolean)

/** Home-screen widget: what's left of this month's budget. */
class MonthBudgetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { Content(context, state) }
    }

    private suspend fun load(context: Context): MonthState {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val money = Money(s.currencyCode)
        val all = container.repository.getAllTransactions()
        val month = YearMonth.now()
        val spent = all.filter { it.type == TxType.EXPENSE && YearMonth.from(it.date) == month }.sumOf { it.amountMinor }
        val daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
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
    private fun Content(context: Context, state: MonthState) {
        val white = ColorProvider(Color(0xFFF5F5F7))
        val muted = ColorProvider(Color(0xFFA1A1AA))
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(Color(0xFF15151B))
                .padding(18.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            Text(state.label, style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                state.amount,
                style = TextStyle(color = if (state.over) ColorProvider(Color(0xFFF58A8A)) else white, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            state.used?.let { used ->
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(
                    progress = used,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = ColorProvider(if (state.over) Color(0xFFF58A8A) else Color(0xFFCDF46F)),
                    backgroundColor = ColorProvider(Color(0x22FFFFFF)),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(state.caption, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
        }
    }
}

class MonthBudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthBudgetWidget()
}
