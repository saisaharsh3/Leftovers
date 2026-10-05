package com.leftovers.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

/**
 * A subscription, bill or recurring income that is logged automatically every month
 * on [dayOfMonth] (clamped to the month's length, so 31 means "last day").
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
) {
    fun chargeDate(month: YearMonth): LocalDate = month.atDay(dayOfMonth.coerceAtMost(month.lengthOfMonth()))

    /** True when this month's charge hasn't been logged yet. */
    fun isPendingIn(month: YearMonth): Boolean =
        active && startMonth <= month.toString() && (lastPostedMonth == null || lastPostedMonth < month.toString())

    /** The next date this will be charged, from [today] on. */
    fun nextChargeDate(today: LocalDate = LocalDate.now()): LocalDate {
        val current = YearMonth.from(today)
        val first = maxOf(current, YearMonth.parse(startMonth))
        return if (isPendingIn(first)) chargeDate(first) else chargeDate(first.plusMonths(1))
    }
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
    val categoryName: String,
    val categoryEmoji: String,
    val categoryColor: Long,
) {
    fun toRecurring() = Recurring(id, name, amountMinor, type, categoryId, dayOfMonth, startMonth, lastPostedMonth, active)
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
