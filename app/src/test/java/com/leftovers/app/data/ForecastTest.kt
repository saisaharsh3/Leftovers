package com.leftovers.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun tx(day: LocalDate, amount: Long, note: String = "", category: Long = 1, type: TxType = TxType.EXPENSE) =
    TransactionItem(0, amount, type, category, day.toEpochDay(), note, 0, "Food", "food", 0, null, null)

private fun sub(name: String, amount: Long, day: Int, start: String, type: TxType = TxType.EXPENSE, every: Int = 1, lastPosted: String? = null, category: Long = 2) =
    RecurringItem(0, name, amount, type, category, day, start, lastPosted, true, every, "Bills", "bills", 0)

class ForecastTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun tooEarlyInTheMonthGivesNothing() {
        assertNull(forecastMonthEnd(100_000, emptyList(), emptyList(), LocalDate.of(2026, 10, 2)))
    }

    @Test fun spendingCarriesOnAtThisMonthsPace() {
        // 1,000 over 10 days = 100 a day, 21 days left.
        val items = listOf(tx(today.minusDays(3), 60_000), tx(today, 40_000))
        assertEquals(1_000_000L - 10_000 * 21, forecastMonthEnd(1_000_000, items, emptyList(), today))
    }

    @Test fun billsStillDueAreSubtractedAndLoggedBillsDontCountAsHabit() {
        val rent = sub("Rent", 1_800_000, day = 5, start = "2026-08", lastPosted = "2026-10")
        val netflix = sub("Netflix", 64_900, day = 20, start = "2026-08", lastPosted = "2026-09")
        val items = listOf(tx(today.withDayOfMonth(5), 1_800_000, note = "Rent", category = 2))
        // Rent was logged by the subscription, so day-to-day spending is zero; Netflix is still to come.
        assertEquals(500_000L - 64_900, forecastMonthEnd(500_000, items, listOf(rent, netflix), today))
    }

    @Test fun incomeDueThisMonthIsAdded() {
        val salary = sub("Salary", 7_500_000, day = 28, start = "2026-01", type = TxType.INCOME, lastPosted = "2026-09")
        assertEquals(7_500_000L, forecastMonthEnd(0, emptyList(), listOf(salary), today))
    }

    @Test fun yearlyBillOutsideItsMonthIsIgnored() {
        val prime = sub("Prime", 149_900, day = 15, start = "2027-01", every = 12)
        assertEquals(0L, forecastMonthEnd(0, emptyList(), listOf(prime), today))
    }
}

class SubscriptionFinderTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test fun findsAMonthlyRepeat() {
        val items = listOf(
            tx(LocalDate.of(2026, 8, 12), 64_900, "Netflix"),
            tx(LocalDate.of(2026, 9, 12), 64_900, "netflix "),
            tx(LocalDate.of(2026, 10, 13), 64_900, "Netflix"),
        )
        val found = findLikelySubscriptions(items, emptyList(), emptySet(), today)
        assertEquals(1, found.size)
        assertEquals("Netflix", found[0].name)
        assertEquals(13, found[0].dayOfMonth)
    }

    @Test fun ignoresDailyHabitsKnownAndDismissedOnes() {
        val coffee = (1..3).flatMap { m -> listOf(tx(LocalDate.of(2026, 7 + m, 3), 8_000, "Coffee"), tx(LocalDate.of(2026, 7 + m, 4), 8_000, "Coffee")) }
        val gym = (8..10).map { tx(LocalDate.of(2026, it, 1), 150_000, "Gym") }
        val spotify = (8..10).map { tx(LocalDate.of(2026, it, 20), 11_900, "Spotify") }
        val found = findLikelySubscriptions(
            coffee + gym + spotify,
            listOf(sub("gym", 150_000, 1, "2026-08")),
            setOf("sub:spotify"),
            today,
        )
        assertTrue(found.isEmpty())
    }

    @Test fun ignoresChangingAmountsAndStoppedOnes() {
        val electricity = listOf(tx(LocalDate.of(2026, 8, 5), 120_000, "Electricity"), tx(LocalDate.of(2026, 9, 5), 210_000, "Electricity"), tx(LocalDate.of(2026, 10, 5), 90_000, "Electricity"))
        val old = (5..7).map { tx(LocalDate.of(2026, it, 9), 50_000, "Old app") }
        assertTrue(findLikelySubscriptions(electricity + old, emptyList(), emptySet(), today).isEmpty())
    }
}

class TagsAndDebtsTest {
    @Test fun readsHashtags() {
        assertEquals(listOf("goa", "trip-2026"), hashtags("Dinner #Goa with friends #trip-2026 #goa"))
        assertEquals(emptyList<String>(), hashtags("No tags here"))
    }

    @Test fun mostUsedTagsFirst() {
        val items = listOf(tx(LocalDate.of(2026, 10, 1), 1, "#work"), tx(LocalDate.of(2026, 10, 2), 1, "#goa"), tx(LocalDate.of(2026, 10, 3), 1, "#goa lunch"))
        assertEquals(listOf("goa", "work"), items.allTags())
    }

    @Test fun balancesGroupByPersonIgnoringSettled() {
        val debts = listOf(
            Debt(1, "Rahul", 50_000, createdAt = 1),
            Debt(2, "rahul ", -20_000, createdAt = 2),
            Debt(3, "Priya", -10_000, createdAt = 3),
            Debt(4, "Priya", -90_000, settled = true, createdAt = 4),
        )
        val balances = debts.openBalances()
        assertEquals(listOf("rahul", "Priya"), balances.map { it.person })
        assertEquals(30_000L, balances[0].netMinor)
        assertEquals(-10_000L, balances[1].netMinor)
    }
}
