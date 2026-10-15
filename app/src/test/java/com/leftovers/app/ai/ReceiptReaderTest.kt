package com.leftovers.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptReaderTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test fun readsAPlainReply() {
        val r = ReceiptReader.parse("""{"amount": 1240.5, "date": "2026-10-12", "category": "Groceries", "note": "DMart", "type": "expense"}""", today)!!
        assertEquals(1_240_50L, r.amountMinor)
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
        assertEquals("Groceries", r.category)
        assertEquals("DMart", r.note)
        assertEquals(false, r.isIncome)
    }

    @Test fun toleratesFencesAndExtraText() {
        val r = ReceiptReader.parse("Here you go:\n```json\n{\"amount\": \"2,499\", \"date\": null, \"category\": null, \"note\": null}\n```", today)!!
        assertEquals(2_499_00L, r.amountMinor)
        assertNull(r.date)
        assertNull(r.category)
        assertNull(r.note)
    }

    @Test fun dropsImpossibleDates() {
        assertNull(ReceiptReader.parse("""{"amount": 10, "date": "2026-11-01"}""", today)!!.date)
        assertNull(ReceiptReader.parse("""{"amount": 10, "date": "2019-01-01"}""", today)!!.date)
    }

    @Test fun noTotalNoReading() {
        assertNull(ReceiptReader.parse("""{"amount": null}""", today))
        assertNull(ReceiptReader.parse("""{"amount": 0}""", today))
        assertNull(ReceiptReader.parse("I can't see a bill in this photo.", today))
    }

    @Test fun refundsAreIncome() {
        assertEquals(true, ReceiptReader.parse("""{"amount": 300, "type": "income", "category": "Other"}""", today)!!.isIncome)
    }
}
