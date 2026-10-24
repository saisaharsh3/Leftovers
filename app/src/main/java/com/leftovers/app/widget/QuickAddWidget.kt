package com.leftovers.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
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
import com.leftovers.app.data.Category
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.util.Money
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Home-screen widget that logs a spend in one tap, without opening the app: a few everyday amounts, logged
 * as an expense today in the chosen category (the most used one to begin with; tap it to switch) from the
 * default account. It shows what was spent today, says what it just logged with Undo for a mis-tap, and
 * "Other amount" opens the full Add screen. A taller widget gets bigger buttons, not empty space.
 */
class QuickAddWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(NARROW, COMPACT, TALL_NARROW, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as LeftoversApp).container
        // First values for the flows below, so the widget never draws empty.
        val firstItems = container.repository.getAllTransactions()
        val firstCategories = container.repository.categories.first()
        val firstSettings = container.settings.settings.first()
        provideContent {
            // Live: an entry added in the app, or by a tap here, shows in Today straight away.
            val items by container.repository.allTransactions.collectAsState(firstItems)
            val categories by container.repository.categories.collectAsState(firstCategories)
            val settings by container.settings.settings.collectAsState(firstSettings)
            val prefs = currentState<Preferences>()
            val choices = topCategories(items, categories)
            val chosen = choices.firstOrNull { it.id == prefs[CATEGORY] } ?: choices.firstOrNull()
            // What was just logged; it stops showing a minute later, at the next refresh.
            val fresh = (System.currentTimeMillis() - (prefs[LOGGED_AT] ?: 0L)) < 60_000
            val money = Money(settings.currencyCode)
            val today = LocalDate.now().toEpochDay()
            Content(
                context,
                View(
                    category = chosen,
                    canSwitch = choices.size > 1,
                    spentToday = money.formatWhole(items.filter { it.type == TxType.EXPENSE && it.epochDay == today }.sumOf { it.amountMinor }),
                    amounts = steps(settings.currencyCode).map { it to money.formatWhole(it) },
                    message = if (fresh) prefs[MESSAGE] else null,
                ),
            )
        }
    }

    private data class View(
        val category: Category?,
        val canSwitch: Boolean,
        val spentToday: String,
        val amounts: List<Pair<Long, String>>,
        val message: String?,
    )

    @Composable
    private fun Content(context: Context, v: View) {
        val size = LocalSize.current
        val tall = size.height >= TALL_NARROW.height
        // Too narrow for four: the biggest one goes.
        val amounts = if (size.width < COMPACT.width) v.amounts.take(3) else v.amounts
        val openAdd = actionStartActivity(
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ADD, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(SURFACE)
                .padding(horizontal = 14.dp, vertical = if (tall) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Header(context, v)
            Spacer(GlanceModifier.height(if (tall) 12.dp else 8.dp))
            val buttonHeight = if (tall) 52.dp else 44.dp
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                amounts.forEachIndexed { i, (minor, label) ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    val action: Action = v.category?.let { actionRunCallback<LogQuickAdd>(actionParametersOf(AMOUNT to minor, CATEGORY_ID to it.id)) }
                        ?: actionStartActivity(Intent(context, MainActivity::class.java))
                    Box(
                        GlanceModifier.defaultWeight().height(buttonHeight).cornerRadius(buttonHeight / 2).background(BUTTON).clickable(action),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = TextStyle(color = ColorProvider(TEXT), fontSize = if (tall) 16.sp else 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
                if (!tall) {
                    Spacer(GlanceModifier.width(6.dp))
                    Box(GlanceModifier.size(buttonHeight).cornerRadius(buttonHeight / 2).background(ACCENT).clickable(openAdd), contentAlignment = Alignment.Center) {
                        Text("+", style = TextStyle(color = ColorProvider(ON_ACCENT), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            if (tall) {
                Spacer(GlanceModifier.height(8.dp))
                Box(GlanceModifier.fillMaxWidth().height(44.dp).cornerRadius(22.dp).background(ACCENT).clickable(openAdd), contentAlignment = Alignment.Center) {
                    Text("+  Other amount", style = TextStyle(color = ColorProvider(ON_ACCENT), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }

    /** The category (tap to switch) and today's spend; after a tap, what was logged and Undo. */
    @Composable
    private fun Header(context: Context, v: View) {
        Row(GlanceModifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            if (v.message != null) {
                Text("✓", style = TextStyle(color = ColorProvider(ACCENT), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(6.dp))
                Text(v.message, style = TextStyle(color = ColorProvider(TEXT), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Box(
                    GlanceModifier.height(28.dp).cornerRadius(14.dp).background(BUTTON).padding(horizontal = 12.dp).clickable(actionRunCallback<UndoQuickAdd>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Undo", style = TextStyle(color = ColorProvider(ACCENT), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
                return@Row
            }
            val category = v.category
            Row(
                GlanceModifier
                    .height(28.dp)
                    .cornerRadius(14.dp)
                    .background(BUTTON)
                    .padding(start = 10.dp, end = 12.dp)
                    .then(
                        if (category == null) GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                        else if (v.canSwitch) GlanceModifier.clickable(actionRunCallback<SwitchQuickAddCategory>(actionParametersOf(CATEGORY_ID to category.id)))
                        else GlanceModifier,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(category?.let { Color(it.color) } ?: MUTED)) {}
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    category?.name ?: "Add a category first",
                    style = TextStyle(color = ColorProvider(TEXT), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                if (v.canSwitch) {
                    Spacer(GlanceModifier.width(5.dp))
                    Text("⇄", style = TextStyle(color = ColorProvider(MUTED), fontSize = 11.sp))
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Text("Today ", style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp), maxLines = 1)
            Text(v.spentToday, style = TextStyle(color = ColorProvider(TEXT), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    companion object {
        private val NARROW = DpSize(180.dp, 70.dp)
        private val COMPACT = DpSize(220.dp, 70.dp)
        // Responsive widgets are drawn at the largest of these that fits, so LocalSize is one of them.
        private val TALL_NARROW = DpSize(180.dp, 150.dp)
        private val TALL = DpSize(220.dp, 150.dp)

        private val SURFACE = Color(0xFF15151B)
        private val BUTTON = Color(0xFF26262F)
        private val TEXT = Color(0xFFF5F5F7)
        private val MUTED = Color(0xFFA1A1AA)
        private val ACCENT = Color(0xFFCDF46F)
        private val ON_ACCENT = Color(0xFF151A04)

        internal val AMOUNT = ActionParameters.Key<Long>("amount")
        internal val CATEGORY_ID = ActionParameters.Key<Long>("category")
        internal val CATEGORY = longPreferencesKey("category")
        internal val MESSAGE = stringPreferencesKey("message")
        internal val LOGGED_AT = longPreferencesKey("logged_at")
        internal val LAST_ID = longPreferencesKey("last_id")

        /** Everyday sums: 50, 100, 200, 500 in rupees; 5, 10, 20, 50 in other currencies (in minor units). */
        internal fun steps(currency: String) = (if (currency == "INR") listOf(50L, 100L, 200L, 500L) else listOf(5L, 10L, 20L, 50L)).map { it * 100 }

        /**
         * Up to four expense categories to switch between: the most used in the last 60 days first, then the
         * rest in their usual order when there isn't enough history.
         */
        internal fun topCategories(items: List<TransactionItem>, categories: List<Category>, today: LocalDate = LocalDate.now()): List<Category> {
            val expense = categories.filter { it.type == TxType.EXPENSE }
            val since = today.minusDays(60).toEpochDay()
            val used = items.filter { it.type == TxType.EXPENSE && it.epochDay >= since }.groupingBy { it.categoryId }.eachCount()
            val ranked = expense.filter { (used[it.id] ?: 0) > 0 }.sortedByDescending { used[it.id] }
            return (ranked + expense.filter { it !in ranked }).take(4)
        }
    }
}

/** Logs the tapped amount as today's expense, then says so on the widget with Undo. */
class LogQuickAdd : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val amount = parameters[QuickAddWidget.AMOUNT] ?: return
        val container = (context.applicationContext as LeftoversApp).container
        val categories = container.repository.categories.first()
        val category = categories.firstOrNull { it.id == parameters[QuickAddWidget.CATEGORY_ID] }
            ?: QuickAddWidget.topCategories(container.repository.getAllTransactions(), categories).firstOrNull()
            ?: return
        val s = container.settings.settings.first()
        val marker = System.currentTimeMillis()
        container.repository.saveTransaction(
            Transaction(amountMinor = amount, type = TxType.EXPENSE, categoryId = category.id, epochDay = LocalDate.now().toEpochDay(), createdAt = marker, accountId = s.defaultAccountId),
        )
        val saved = container.repository.getAllTransactions().firstOrNull { it.createdAt == marker }
        updateAppWidgetState(context, glanceId) {
            it[QuickAddWidget.MESSAGE] = "${Money(s.currencyCode).formatWhole(amount)} · ${category.name}"
            it[QuickAddWidget.LOGGED_AT] = marker
            if (saved != null) it[QuickAddWidget.LAST_ID] = saved.id
        }
        QuickAddWidget().update(context, glanceId)
        // A minute on, the "Logged" line gives way to the category and today's total again.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "quick_add_refresh",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<QuickAddRefresh>().setInitialDelay(61, TimeUnit.SECONDS).build(),
        )
    }
}

class QuickAddRefresh(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        QuickAddWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/** Moves the widget on to the next of the most used categories. */
class SwitchQuickAddCategory : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as LeftoversApp).container
        val choices = QuickAddWidget.topCategories(container.repository.getAllTransactions(), container.repository.categories.first())
        if (choices.isEmpty()) return
        val at = choices.indexOfFirst { it.id == parameters[QuickAddWidget.CATEGORY_ID] }
        val next = choices[(at + 1) % choices.size]
        updateAppWidgetState(context, glanceId) {
            it[QuickAddWidget.CATEGORY] = next.id
            // A new category means the last "Logged" line no longer applies.
            it.remove(QuickAddWidget.MESSAGE)
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
