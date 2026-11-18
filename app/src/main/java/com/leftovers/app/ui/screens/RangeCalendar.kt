package com.leftovers.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Picking a first and a last day on a month calendar, like Activity's (#61): tap the first day, then the last, and
 * the days between light up. Tapping again starts over; a day before the first makes that the first.
 */
@Composable
fun RangeCalendarSheet(
    from: LocalDate,
    to: LocalDate,
    title: String,
    onDone: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassSheet(onDismiss) {
        RangeCalendar(from, to, title, onDone, Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp))
    }
}

/** The same calendar as a card in the middle of the screen, for picking dates without a sheet rising. */
@Composable
fun RangeCalendarDialog(
    from: LocalDate,
    to: LocalDate,
    title: String,
    onDone: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(30.dp)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, com.leftovers.app.ui.components.glassBorder(c), shape),
        ) {
            RangeCalendar(from, to, title, onDone, Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun RangeCalendar(
    from: LocalDate,
    to: LocalDate,
    title: String,
    onDone: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    var start by rememberSaveable { mutableStateOf(from.toEpochDay()) }
    var end by rememberSaveable { mutableStateOf<Long?>(to.toEpochDay()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(from).toString()) }
    val shown = YearMonth.parse(month)
    val first = LocalDate.ofEpochDay(start)
    val last = end?.let(LocalDate::ofEpochDay)
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    // Tapping the month's name swaps the days for a grid of years, to jump far without stepping month by month.
    var picking by rememberSaveable { mutableStateOf(false) }
    // The first of the twelve years in the grid; its arrows move it by twelve.
    var page by rememberSaveable { mutableStateOf(0) }
    fun pageStart(year: Int) = year - Math.floorMod(year - LocalDate.now().year + 11, 12)

    fun tap(day: LocalDate) {
        when {
            last != null -> { start = day.toEpochDay(); end = null }
            day < first -> start = day.toEpochDay()
            else -> end = day.toEpochDay()
        }
    }

    run {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                if (last == null) "From ${first.format(fmt)}. Now tap the last day."
                else "${first.format(fmt)} to ${last.format(fmt)} · ${ChronoUnit.DAYS.between(first, last) + 1} days",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(
                    Lucide.ChevronLeft, if (picking) "Earlier years" else "Previous month",
                    { if (picking) page -= 12 else month = shown.minusMonths(1).toString() }, size = 44.dp,
                )
                AnimatedContent(
                    Triple(shown, picking, page),
                    transitionSpec = {
                        val forward = targetState.first > initialState.first || targetState.third > initialState.third
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "rangeMonth",
                ) { (m, years, _) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable({
                            if (!picking) page = pageStart(shown.year)
                            picking = !picking
                        }, pressedScale = 0.96f).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (years) "$page – ${page + 11}" else m.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + m.year,
                            style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                        )
                        Spacer(Modifier.width(4.dp))
                        androidx.compose.material3.Icon(
                            if (years) Lucide.ChevronUp else Lucide.ChevronDown,
                            contentDescription = if (years) "Back to the days" else "Pick a year",
                            tint = c.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                RoundButton(
                    Lucide.ChevronRight, if (picking) "Later years" else "Next month",
                    { if (picking) page += 12 else month = shown.plusMonths(1).toString() }, size = 44.dp,
                )
            }
            AnimatedContent(picking, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "rangeView") { years ->
                if (years) YearGrid(page, shown.year, first, last) { y -> month = shown.withYear(y).toString(); picking = false }
                else Month(shown, first, last, onTap = ::tap)
            }
            PrimaryButton("Done", { last?.let { onDone(first, it) } }, Modifier.fillMaxWidth(), enabled = last != null)
        }
    }
}

/**
 * Twelve years from [from]; the one showing is filled, years the picked range touches are tinted, this year is
 * ringed. Picking one keeps the month and moves to that year.
 */
@Composable
private fun YearGrid(from: Int, shownYear: Int, first: LocalDate, last: LocalDate?, onPick: (Int) -> Unit) {
    val c = LocalAppColors.current
    val now = LocalDate.now().year
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (from until from + 12).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { y ->
                    val on = y == shownYear
                    val inside = last != null && y >= first.year && y <= last.year
                    val bg by animateColorAsState(
                        when {
                            on -> c.accent
                            inside -> c.accent.copy(alpha = 0.22f)
                            else -> c.glass
                        },
                        label = "yearTile",
                    )
                    Box(
                        Modifier.weight(1f).height(54.dp)
                            .clip(shape)
                            .pressable({ onPick(y) }, pressedScale = 0.94f)
                            .background(bg)
                            .then(if (y == now && !on) Modifier.border(1.dp, c.textSecondary, shape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$y", style = MaterialTheme.typography.labelLarge, color = if (on) c.onAccent else c.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Month(month: YearMonth, first: LocalDate, last: LocalDate?, onTap: (LocalDate) -> Unit) {
    val c = LocalAppColors.current
    val today = LocalDate.now()
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<Int?> = List(leading) { null } + (1..month.lengthOfMonth()).toList()
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day == null) return@Box
                        val date = month.atDay(day)
                        val end = date == first || date == last
                        val inside = last != null && date > first && date < last
                        val bg by animateColorAsState(
                            when {
                                end -> c.accent
                                inside -> c.accent.copy(alpha = 0.22f)
                                else -> Color.Transparent
                            },
                            label = "rangeDay",
                        )
                        Box(
                            Modifier.matchParentSize()
                                .pressable({ onTap(date) }, pressedScale = 0.9f)
                                .clip(shape)
                                .background(bg)
                                .then(if (date == today && !end) Modifier.border(1.dp, c.textSecondary, shape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.toString(), style = MaterialTheme.typography.labelLarge, color = if (end) c.onAccent else c.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
