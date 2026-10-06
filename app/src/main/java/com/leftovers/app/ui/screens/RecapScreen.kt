package com.leftovers.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.GoalWithSaved
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.data.categoryTotals
import com.leftovers.app.data.totalOf
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class RecapData(
    val month: YearMonth,
    val spent: Long,
    val income: Long,
    val previousSpent: Long,
    val count: Int,
    val topCategoryName: String,
    val topCategoryIcon: String,
    val topCategoryColor: Long,
    val topCategoryAmount: Long,
    val biggestDay: LocalDate?,
    val biggestDayAmount: Long,
    val busiestWeekday: DayOfWeek?,
    val favouriteNote: String?,
    val favouriteCount: Int,
    val goals: List<GoalWithSaved>,
)

class RecapViewModel(
    savedState: SavedStateHandle,
    repository: TransactionRepository,
    planning: PlanningRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val month: YearMonth = savedState.get<String>("month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.now().minusMonths(1)

    val data: StateFlow<RecapData?> = combine(repository.allTransactions, planning.goals) { all, goals ->
        val items = all.filter { YearMonth.from(it.date) == month }
        val expenses = items.filter { it.type == TxType.EXPENSE }
        val top = items.categoryTotals(TxType.EXPENSE).firstOrNull()
        val byDay = expenses.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val biggest = byDay.maxByOrNull { it.value }
        val favourite = expenses.filter { it.note.isNotBlank() }.groupingBy { it.note.trim().lowercase() }.eachCount().maxByOrNull { it.value }
        RecapData(
            month = month,
            spent = items.totalOf(TxType.EXPENSE),
            income = items.totalOf(TxType.INCOME),
            previousSpent = all.filter { YearMonth.from(it.date) == month.minusMonths(1) }.totalOf(TxType.EXPENSE),
            count = items.size,
            topCategoryName = top?.name.orEmpty(),
            topCategoryIcon = top?.emoji ?: "sparkles",
            topCategoryColor = top?.color ?: 0xFF9C9CF8,
            topCategoryAmount = top?.totalMinor ?: 0,
            biggestDay = biggest?.key,
            biggestDayAmount = biggest?.value ?: 0,
            busiestWeekday = expenses.groupingBy { it.date.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key,
            favouriteNote = favourite?.takeIf { it.value >= 2 }?.let { f -> expenses.first { it.note.trim().lowercase() == f.key }.note.trim() },
            favouriteCount = favourite?.value ?: 0,
            goals = goals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { settings.setRecapSeen(month.toString()) }
    }
}

private const val PAGE_MS = 5_500

@Composable
fun RecapScreen(onClose: () -> Unit, viewModel: RecapViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val c = LocalAppColors.current
    val pages = recapPages(d)
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val context = LocalContext.current
    // The current page is recorded as it draws, so it can be shared as an image.
    val shot = rememberGraphicsLayer()
    val background = c.background.toArgb()
    val footer = c.textTertiary.toArgb()

    // Keyed on the settled page: currentPage flips halfway through a scroll, which would restart
    // this effect and cancel the scroll, leaving the pager stuck between two pages.
    LaunchedEffect(pager.settledPage) {
        val page = pager.settledPage
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PAGE_MS, easing = LinearEasing))
        if (page < pages.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) }
    }

    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.55f))) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    shot.record { this@drawWithContent.drawContent() }
                    drawLayer(shot)
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        scope.launch {
                            if (offset.x < size.width / 3f) {
                                pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                            } else if (pager.currentPage < pages.lastIndex) {
                                pager.animateScrollToPage(pager.currentPage + 1)
                            }
                        }
                    }
                },
        ) { page -> pages[page]() }

        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { i ->
                    val fill = when {
                        i < pager.settledPage -> 1f
                        i == pager.settledPage -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(c.glassStrong)) {
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(c.textPrimary))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} recap",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                RoundButton(Lucide.Share2, "Share", {
                    scope.launch { shareRecap(context, shot.toImageBitmap().asAndroidBitmap(), background, footer) }
                }, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                RoundButton(Lucide.X, "Close", onClose, size = 40.dp)
            }
        }

        if (pager.currentPage == pages.lastIndex) {
            PrimaryButton(
                "Done",
                onClose,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

/** Shares the page on a solid background with a small "Leftovers" mark, through the share sheet. */
private suspend fun shareRecap(context: Context, page: Bitmap, background: Int, footer: Int) {
    val uri = withContext(Dispatchers.IO) {
        val src = page.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        AndroidCanvas(out).apply {
            drawColor(background)
            drawBitmap(src, 0f, 0f, null)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = footer
                textSize = src.width / 28f
                textAlign = Paint.Align.CENTER
            }
            drawText("Leftovers", src.width / 2f, src.height - src.width / 12f, paint)
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "recap.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share recap"))
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun recapPages(d: RecapData): List<@Composable () -> Unit> {
    val money = LocalMoney.current
    val c = LocalAppColors.current
    val monthName = d.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val pages = mutableListOf<@Composable () -> Unit>()

    pages += {
        Story(Lucide.Sparkles, c.accent, "In $monthName you spent", money.formatWhole(d.spent), "across ${d.count} entries · about ${money.formatWhole(d.spent / (if (d.month == YearMonth.now()) LocalDate.now().dayOfMonth else d.month.lengthOfMonth()))} a day")
    }
    if (d.previousSpent > 0) {
        val change = (d.spent - d.previousSpent).toFloat() / d.previousSpent * 100
        val less = change <= 0
        pages += {
            Story(
                if (less) Lucide.TrendingDown else Lucide.TrendingUp,
                if (less) c.positive else c.negative,
                if (less) "That's less than the month before" else "That's more than the month before",
                "${abs(change).roundToInt()}%",
                if (less) "Nice. ${money.formatWhole(d.previousSpent - d.spent)} stayed in your pocket." else "${money.formatWhole(d.spent - d.previousSpent)} more than last month.",
            )
        }
    }
    if (d.topCategoryAmount > 0) {
        pages += {
            StoryPage {
                CategoryIcon(d.topCategoryIcon, d.topCategoryColor, size = 72.dp, modifier = Modifier.appear(0))
                Spacer(Modifier.height(28.dp))
                Text("Most of it went to", style = MaterialTheme.typography.titleMedium, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.appear(1))
                Spacer(Modifier.height(6.dp))
                Headline(d.topCategoryName, Color(d.topCategoryColor), Modifier.appear(2))
                Spacer(Modifier.height(14.dp))
                Text(
                    "${money.formatWhole(d.topCategoryAmount)} · ${(d.topCategoryAmount * 100 / d.spent.coerceAtLeast(1))}% of your spending",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.appear(3),
                )
            }
        }
    }
    if (d.biggestDay != null) {
        pages += {
            Story(
                Lucide.Calendar,
                c.warning,
                "Your biggest day was ${d.biggestDay.format(dayFormat)}",
                money.formatWhole(d.biggestDayAmount),
                d.busiestWeekday?.let { "You spend most often on ${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}s." }.orEmpty(),
            )
        }
    }
    if (d.favouriteNote != null) {
        pages += { Story(Lucide.Coffee, c.accent, "Your regular", d.favouriteNote, "${d.favouriteCount} times this month") }
    }
    val saved = d.income - d.spent
    pages += {
        Story(
            Lucide.PiggyBank,
            if (saved >= 0) c.positive else c.negative,
            if (d.income == 0L) "Goals" else if (saved >= 0) "You kept" else "You spent more than you earned by",
            if (d.income == 0L) "${d.goals.count { !it.reached }} active" else money.formatWhole(abs(saved)),
            d.goals.firstOrNull { !it.reached }?.let { "${it.name} is ${(it.fraction * 100).toInt()}% funded. Keep going." }
                ?: "Set a savings goal to give this money a purpose.",
        )
    }
    return pages
}

/** Every recap page shares the same centred column inside the safe area. */
@Composable
private fun StoryPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** Big centred headline that steps down in size for long text so it never runs off screen. */
@Composable
private fun Headline(text: String, color: Color, modifier: Modifier = Modifier) {
    val style = when {
        text.length <= 8 -> MaterialTheme.typography.displayMedium
        text.length <= 14 -> MaterialTheme.typography.displaySmall
        else -> MaterialTheme.typography.headlineLarge
    }
    Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}

@Composable
private fun Story(icon: ImageVector, tint: Color, eyebrow: String, headline: String, body: String) {
    val c = LocalAppColors.current
    StoryPage {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)).appear(0), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(eyebrow, style = MaterialTheme.typography.titleMedium, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.appear(1))
        Spacer(Modifier.height(6.dp))
        Headline(headline, c.textPrimary, Modifier.appear(2))
        if (body.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.appear(3))
        }
    }
}