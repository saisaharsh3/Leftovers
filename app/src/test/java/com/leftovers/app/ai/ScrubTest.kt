package com.leftovers.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubTest {
    private fun scrub(s: String) = AssistantTools.scrub(s)

    @Test fun hidesCardAndAccountNumbers() {
        assertEquals("Paid with card [hidden]", scrub("Paid with card 4111 1111 1111 1111"))
        assertEquals("A/c [hidden] debited", scrub("A/c 123456789012 debited"))
    }

    @Test fun hidesPhoneNumbersUpiAndEmail() {
        assertEquals("Call [hidden]", scrub("Call 9876543210"))
        assertEquals("Sent to [hidden]", scrub("Sent to nick.k@okhdfcbank"))
        assertEquals("Mail [hidden] today", scrub("Mail me@example.com today"))
    }

    @Test fun keepsOrdinaryText() {
        assertEquals("Coffee with Priya at 4pm", scrub("Coffee with Priya at 4pm"))
        assertEquals("Rent for Oct 2026", scrub("Rent for Oct 2026"))
        assertTrue(scrub("2 x 250.50 snacks").contains("250.50"))
    }
}
