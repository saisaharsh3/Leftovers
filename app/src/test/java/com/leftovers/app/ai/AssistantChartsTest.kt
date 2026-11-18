package com.leftovers.app.ai

import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class AssistantChartsTest {
    private var id = 1L
    private fun entry(day: String, amount: Long, category: String = "Food", type: TxType = TxType.EXPENSE, account: Long? = 1) = TransactionItem(
        id = id++, amountMinor = amount, type = type, categoryId = category.hashCode().toLong(), epochDay = LocalDate.parse(day).toEpochDay(),
        note = "", createdAt = 0, categoryName = category, categoryEmoji = "", categoryColor = 0xFF00FF00, accountId = account, receiptPath = null,
    )

    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov30 = LocalDate.of(2026, 11, 30)

    @Test fun spendingByMonthAddsUpEachMonth() {
        val entries = listOf(entry("2026-09-10", 500), entry("2026-11-02", 200), entry("2026-11-20", 300), entry("2026-11-21", 9_000, "Pay", TxType.INCOME))
        val chart = AssistantCharts.build(Measure.SPENDING, Grouping.MONTH, LocalDate.of(2026, 9, 1), nov30, entries, subtitle = "")
        assertEquals(ChartKind.BARS, chart.kind)
        assertEquals(listOf(500L, 0L, 500L), chart.points.map { it.value })
        assertEquals(1_000L, chart.total)
    }

    @Test fun manyDaysBecomeWeeksThenMonths() {
        assertEquals(Grouping.DAY, AssistantCharts.fit(Grouping.DAY, nov1, nov30))
        assertEquals(Grouping.WEEK, AssistantCharts.fit(Grouping.DAY, nov1, nov1.plusDays(80)))
        assertEquals(Grouping.MONTH, AssistantCharts.fit(Grouping.DAY, nov1, nov1.plusYears(1)))
    }

    @Test fun weeksStartOnMondayButNotBeforeTheFirstDay() {
        // 1 Nov 2026 is a Sunday: the first week is just that day.
        val weeks = AssistantCharts.buckets(Grouping.WEEK, nov1, LocalDate.of(2026, 11, 9))
        assertEquals(nov1..nov1, weeks[0].second)
        assertEquals(LocalDate.of(2026, 11, 2)..LocalDate.of(2026, 11, 8), weeks[1].second)
        assertEquals(3, weeks.size)
    }

    @Test fun aFewCategoriesAreARingAndManyAreRanked() {
        val few = listOf(entry("2026-11-02", 100, "Food"), entry("2026-11-03", 300, "Rent"))
        val ring = AssistantCharts.build(Measure.SPENDING, Grouping.CATEGORY, nov1, nov30, few, subtitle = "")
        assertEquals(ChartKind.DONUT, ring.kind)
        assertEquals("Rent", ring.points.first().label)
        val many = (1..12).map { entry("2026-11-02", it * 100L, "C$it") }
        val ranked = AssistantCharts.build(Measure.SPENDING, Grouping.CATEGORY, nov1, nov30, many, subtitle = "")
        assertEquals(ChartKind.RANKED, ranked.kind)
        assertEquals(AssistantCharts.MAX_PARTS, ranked.points.size)
        assertEquals("Everything else", ranked.points.last().label)
        assertEquals(many.sumOf { it.amountMinor }, ranked.points.sumOf { it.value })
    }

    @Test fun inAndOutPairsEachMonth() {
        val entries = listOf(entry("2026-11-02", 200), entry("2026-11-21", 9_000, "Pay", TxType.INCOME))
        val chart = AssistantCharts.build(Measure.IN_AND_OUT, Grouping.MONTH, nov1, nov30, entries, subtitle = "")
        assertEquals(ChartKind.PAIR, chart.kind)
        assertEquals(9_000L, chart.points.single().value)
        assertEquals(200L, chart.points.single().second)
        assertThrows(IllegalArgumentException::class.java) {
            AssistantCharts.build(Measure.IN_AND_OUT, Grouping.CATEGORY, nov1, nov30, entries, subtitle = "")
        }
    }

    @Test fun byAccountNamesThem() {
        val entries = listOf(entry("2026-11-02", 200, account = 1), entry("2026-11-03", 50, account = null))
        val chart = AssistantCharts.build(Measure.SPENDING, Grouping.ACCOUNT, nov1, nov30, entries, mapOf(1L to "Bank"), subtitle = "")
        assertEquals(listOf("Bank", "No account"), chart.points.map { it.label })
    }
}
