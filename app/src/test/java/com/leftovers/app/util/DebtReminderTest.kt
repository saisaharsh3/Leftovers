package com.leftovers.app.util

import com.leftovers.app.data.Debt
import com.leftovers.app.data.PersonBalance
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtReminderTest {
    private val rupees: (Long) -> String = { "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(it / 100) }

    @Test fun saysWhatItWasFor() {
        val b = PersonBalance("Arjun Rao", 250_000, listOf(Debt(person = "Arjun Rao", amountMinor = 250_000, note = "Goa stay share")))
        assertEquals("Hi Arjun, a quick reminder about ₹2,500 for Goa stay share. Thanks!", DebtReminder.message(b, rupees))
    }

    @Test fun severalEntriesAndRepaymentsLeftOut() {
        val entries = listOf(
            Debt(person = "Nick", amountMinor = 60_000, note = "Cab home"),
            Debt(person = "Nick", amountMinor = 50_000, note = "Movie"),
            Debt(person = "Nick", amountMinor = 20_000, note = "Lunch"),
            Debt(person = "Nick", amountMinor = -40_000, note = "Paid back", repayment = true),
        )
        val b = PersonBalance("Nick", 90_000, entries)
        assertEquals("Hi Nick, a quick reminder about ₹900 for Cab home, Movie and 1 more. Thanks!", DebtReminder.message(b, rupees))
    }

    @Test fun noNotes() {
        val b = PersonBalance("Priya", 45_000, listOf(Debt(person = "Priya", amountMinor = 45_000)))
        assertEquals("Hi Priya, a quick reminder about ₹450. Thanks!", DebtReminder.message(b, rupees))
    }
}
