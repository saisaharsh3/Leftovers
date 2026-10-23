package com.leftovers.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.leftovers.app.LeftoversApp
import com.leftovers.app.MainActivity
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import com.leftovers.app.util.Money
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Home-screen widget that logs a spend in one tap, without opening the app: a few amounts, logged as an
 * expense today in the category used most lately, from the default account. After a tap it says what it
 * logged, with Undo for a mis-tap; + opens the full Add screen for anything else.
 */
class QuickAddWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val setup = load(context)
        provideContent {
            val prefs = currentState<Preferences>()
            // What was just logged; it stops showing a minute later, at the next refresh.
            val fresh = (System.currentTimeMillis() - (prefs[LOGGED_AT] ?: 0L)) < 60_000
            Content(context, setup, if (fresh) prefs[MESSAGE] else null)
        }
    }

    internal data class Setup(val categoryId: Long?, val categoryName: String, val amounts: List<Pair<Long, String>>)

    private suspend fun load(context: Context): Setup {
        val container = (context.applicationContext as LeftoversApp).container
        val s = container.settings.settings.first()
        val money = Money(s.currencyCode)
        val category = favouriteCategory(context)
        val names = container.repository.categories.first().associate { it.id to it.name }
        // Everyday sums: 50, 100, 200, 500 in rupees; 5, 10, 20, 50 in other currencies.
        val steps = if (s.currencyCode == "INR") listOf(50L, 100L, 200L, 500L) else listOf(5L, 10L, 20L, 50L)
        return Setup(category, category?.let { names[it] } ?: "Add a category first", steps.map { it * 100 to money.formatWhole(it * 100) })
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, setup: Setup, message: String?) {
        val white = ColorProvider(Color(0xFFF5F5F7))
        val muted = ColorProvider(Color(0xFFA1A1AA))
        val accent = Color(0xFFCDF46F)
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(Color(0xFF15151B))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            // Centred, so a taller widget doesn't leave the buttons at the top.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message ?: "Quick add · ${setup.categoryName}",
                    style = TextStyle(color = if (message != null) white else muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (message != null) {
                    Text(
                        "Undo",
                        style = TextStyle(color = ColorProvider(accent), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(horizontal = 6.dp).clickable(actionRunCallback<UndoQuickAdd>()),
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                setup.amounts.forEach { (minor, label) ->
                    Box(
                        GlanceModifier
                            .defaultWeight()
                            .height(44.dp)
                            .cornerRadius(22.dp)
                            .background(Color(0xFF26262F))
                            .then(
                                if (setup.categoryId != null) GlanceModifier.clickable(actionRunCallback<LogQuickAdd>(actionParametersOf(AMOUNT to minor)))
                                else GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.width(6.dp))
                }
                Box(
                    GlanceModifier
                        .size(44.dp)
                        .cornerRadius(22.dp)
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
                    Text("+", style = TextStyle(color = ColorProvider(Color(0xFF151A04)), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    companion object {
        internal val AMOUNT = ActionParameters.Key<Long>("amount")
        internal val MESSAGE = stringPreferencesKey("message")
        internal val LOGGED_AT = longPreferencesKey("logged_at")
        internal val LAST_ID = longPreferencesKey("last_id")

        /** The expense category used most in the last 60 days; the first expense category when there's no history. */
        internal suspend fun favouriteCategory(context: Context): Long? {
            val container = (context.applicationContext as LeftoversApp).container
            val since = LocalDate.now().minusDays(60).toEpochDay()
            val used = container.repository.getAllTransactions()
                .filter { it.type == TxType.EXPENSE && it.epochDay >= since }
                .groupingBy { it.categoryId }.eachCount()
            val categories = container.repository.categories.first().filter { it.type == TxType.EXPENSE }
            return used.entries.filter { e -> categories.any { it.id == e.key } }.maxByOrNull { it.value }?.key ?: categories.firstOrNull()?.id
        }
    }
}

/** Logs the tapped amount as today's expense, then says so on the widget with Undo. */
class LogQuickAdd : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val amount = parameters[QuickAddWidget.AMOUNT] ?: return
        val container = (context.applicationContext as LeftoversApp).container
        val category = QuickAddWidget.favouriteCategory(context) ?: return
        val s = container.settings.settings.first()
        val marker = System.currentTimeMillis()
        container.repository.saveTransaction(
            Transaction(amountMinor = amount, type = TxType.EXPENSE, categoryId = category, epochDay = LocalDate.now().toEpochDay(), createdAt = marker, accountId = s.defaultAccountId),
        )
        val saved = container.repository.getAllTransactions().firstOrNull { it.createdAt == marker }
        val name = container.repository.categories.first().firstOrNull { it.id == category }?.name.orEmpty()
        updateAppWidgetState(context, glanceId) {
            it[QuickAddWidget.MESSAGE] = "Logged ${Money(s.currencyCode).formatWhole(amount)} · $name"
            it[QuickAddWidget.LOGGED_AT] = marker
            if (saved != null) it[QuickAddWidget.LAST_ID] = saved.id
        }
        QuickAddWidget().update(context, glanceId)
    }
}

/** Takes back the entry the widget just logged (a mis-tap), without keeping it in Deleted entries. */
class UndoQuickAdd : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as LeftoversApp).container
        var last = 0L
        updateAppWidgetState(context, glanceId) {
            last = it[QuickAddWidget.LAST_ID] ?: 0L
            it.remove(QuickAddWidget.LAST_ID)
            it.remove(QuickAddWidget.MESSAGE)
            it.remove(QuickAddWidget.LOGGED_AT)
        }
        if (last > 0) container.repository.getTransaction(last)?.let { container.repository.removeTransaction(it) }
        QuickAddWidget().update(context, glanceId)
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
