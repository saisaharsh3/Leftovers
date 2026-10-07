package com.leftovers.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

/**
 * A subscription, bill or recurring income that is logged automatically on [dayOfMonth] (clamped to
 * the month's length, so 31 means "last day") every [everyMonths] months: 1 = monthly, 12 = yearly,
 * counted from [startMonth].
 */
@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoryId")],
)
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long,
    val dayOfMonth: Int,
    /** First month to charge, as "yyyy-MM". */
    val startMonth: String,
    /** Last month a transaction was created for, as "yyyy-MM"; null if never. */
    val lastPostedMonth: String? = null,
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "1") val everyMonths: Int = 1,
    /** The account it's paid from or into; null uses the default account. */
    val accountId: Long? = null,
) {
    val yearly: Boolean get() = everyMonths == 12

    fun chargeDate(month: YearMonth): LocalDate = month.atDay(dayOfMonth.coerceAtMost(month.lengthOfMonth()))

    /** True when [month] is a billing month: on or after the start, on the right cycle. */
    fun isDueIn(month: YearMonth): Boolean {
        val start = YearMonth.parse(startMonth)
        if (month < start) return false
        val months = (month.year - start.year) * 12 + (month.monthValue - start.monthValue)
        return months % everyMonths.coerceAtLeast(1) == 0
    }

    /** True when [month] is a billing month whose charge hasn't been logged yet. */
    fun isPendingIn(month: YearMonth): Boolean =
        active && isDueIn(month) && (lastPostedMonth == null || lastPostedMonth < month.toString())

    /** The next date this will be charged, from [today] on. */
    fun nextChargeDate(today: LocalDate = LocalDate.now()): LocalDate {
        var month = maxOf(YearMonth.from(today), YearMonth.parse(startMonth))
        // At most one cycle ahead (12 months for yearly) plus the current month.
        repeat(everyMonths.coerceAtLeast(1) + 1) {
            if (isPendingIn(month)) return chargeDate(month)
            month = month.plusMonths(1)
        }
        return chargeDate(month)
    }

    /** What this costs per month on average (a yearly bill spread over 12 months). */
    val monthlyShareMinor: Long get() = amountMinor / everyMonths.coerceAtLeast(1)
}

data class RecurringItem(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long,
    val dayOfMonth: Int,
    val startMonth: String,
    val lastPostedMonth: String?,
    val active: Boolean,
    val everyMonths: Int,
    val accountId: Long?,
    val categoryName: String,
    val categoryEmoji: String,
    val categoryColor: Long,
) {
    fun toRecurring() = Recurring(id, name, amountMinor, type, categoryId, dayOfMonth, startMonth, lastPostedMonth, active, everyMonths, accountId)
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Long,
    val targetMinor: Long,
    /** Month by which the goal should be reached, as "yyyy-MM". */
    val targetMonth: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Money put into (positive) or taken out of (negative) a goal. */
@Entity(
    tableName = "goal_deposits",
    foreignKeys = [
        ForeignKey(entity = Goal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("goalId")],
)
data class GoalDeposit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GoalWithSaved(
    val id: Long,
    val name: String,
    val emoji: String,
    val color: Long,
    val targetMinor: Long,
    val targetMonth: String,
    val createdAt: Long,
    val savedMinor: Long,
) {
    fun toGoal() = Goal(id, name, emoji, color, targetMinor, targetMonth, createdAt)

    val remainingMinor: Long get() = (targetMinor - savedMinor).coerceAtLeast(0)
    val fraction: Float get() = if (targetMinor > 0) (savedMinor.toFloat() / targetMinor).coerceIn(0f, 1f) else 0f
    val reached: Boolean get() = savedMinor >= targetMinor

    /** Months left including the current one (at least 1). */
    fun monthsLeft(today: LocalDate = LocalDate.now()): Int {
        val now = YearMonth.from(today)
        val target = YearMonth.parse(targetMonth)
        return (now.until(target, java.time.temporal.ChronoUnit.MONTHS).toInt() + 1).coerceAtLeast(1)
    }

    /** How much to put aside each month from now on to hit the target in time. */
    fun monthlyNeeded(today: LocalDate = LocalDate.now()): Long = ceilDiv(remainingMinor, monthsLeft(today).toLong())

    /** What should have been saved by now if saving evenly from creation to the target month. */
    fun expectedByNow(today: LocalDate = LocalDate.now()): Long {
        val start = YearMonth.from(java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()))
        val target = YearMonth.parse(targetMonth)
        val total = (start.until(target, java.time.temporal.ChronoUnit.MONTHS) + 1).coerceAtLeast(1)
        val elapsed = (start.until(YearMonth.from(today), java.time.temporal.ChronoUnit.MONTHS) + 1).coerceIn(0, total)
        return targetMinor * elapsed / total
    }
}

fun ceilDiv(a: Long, b: Long): Long = if (b <= 0) a else (a + b - 1) / b
