package com.leftovers.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.ai.ChartData
import com.leftovers.app.ai.ChartKind
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney

/** Colours for parts that have none of their own (accounts, "Everything else"). */
private val spare = listOf(0xFF8BC34A, 0xFF64B5F6, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DB6AC, 0xFFE57373, 0xFF90A4AE, 0xFFFFD54F)

private fun colorOf(color: Long?, i: Int) = Color(color ?: spare[i % spare.size])

/**
 * A chart from the assistant, drawn with the same charts as Insights: bars or a line over time, two bars a month
 * for in and out, a ring for a few parts, ranked rows for more.
 */
@Composable
fun ChatChart(chart: ChartData) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val points = chart.points
    // About six labels along the bottom, whatever the number of bars.
    val every = ((points.size + 5) / 6).coerceAtLeast(1)
    val axis: (Int) -> String? = { i -> if (i % every == 0) points.getOrNull(i)?.label else null }
    when (chart.kind) {
        ChartKind.BARS -> ColumnChart(
            values = points.map { it.value },
            highlight = null,
            modifier = Modifier.fillMaxWidth().height(170.dp),
            axis = axis,
            title = { points[it].label },
            value = { money.format(points[it].value) },
            scale = money::compact,
        )
        ChartKind.LINE -> if (points.size >= 2) TrendLine(
            values = points.map { it.value },
            color = c.accent,
            modifier = Modifier.fillMaxWidth().height(170.dp),
            axis = axis,
            title = { points[it].label },
            value = { money.format(points[it].value) },
            scale = money::compact,
        ) else Text(points.firstOrNull()?.let { "${it.label}: ${money.format(it.value)}" } ?: "", color = c.textPrimary)
        ChartKind.PAIR -> {
            PairBars(
                pairs = points.map { it.value to it.second },
                first = c.positive,
                second = c.textSecondary,
                modifier = Modifier.fillMaxWidth().height(170.dp),
                axis = { i -> if (i % every == 0) points[i].label else "" },
                title = { points[it].label },
                value = { "in ${money.formatWhole(points[it].value)} · out ${money.formatWhole(points[it].second)}" },
                scale = money::compact,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Key(c.positive, chart.firstName)
                Key(c.textSecondary, chart.secondName)
            }
        }
        ChartKind.DONUT -> {
            val total = points.sumOf { it.value }.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(points.mapIndexed { i, p -> ChartSlice(p.value.toFloat(), colorOf(p.color, i)) }, Modifier.size(112.dp)) {}
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    points.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colorOf(p.color, i)))
                            Spacer(Modifier.width(8.dp))
                            Text(p.label, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${Math.round(p.value * 100.0 / total)}%", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                    }
                }
            }
        }
        ChartKind.RANKED -> {
            val max = points.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                points.forEachIndexed { i, p ->
                    Column {
                        Row {
                            Text(p.label, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(money.format(p.value), style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
                        }
                        Spacer(Modifier.height(5.dp))
                        ProgressLine(p.value.toFloat() / max, Modifier.fillMaxWidth(), color = colorOf(p.color, i), height = 5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(color: Color, text: String) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}
