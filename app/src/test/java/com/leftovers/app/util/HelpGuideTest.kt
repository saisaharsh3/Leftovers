package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpGuideTest {
    private fun titles(q: String) = HelpGuide.search(q).map { it.title }

    @Test fun findsFeaturesByAnyWord() {
        assertTrue("Transfer between accounts" in titles("transfer"))
        assertTrue("PDF statement" in titles("pdf"))
        assertTrue("Import entries" in titles("phonepe statement"))
        assertTrue("Envelope budgets" in titles("Trip"))
        assertTrue(titles("zzz nothing").isEmpty())
    }

    @Test fun theAssistantGetsTheClosestAnswer() {
        // Questions in the user's words, not the guide's.
        assertEquals("Transfer between accounts", HelpGuide.forAssistant("how do I move money to my credit card account").first().title)
        assertTrue(HelpGuide.forAssistant("where can I download a PDF").any { it.title == "PDF statement" })
        assertTrue(HelpGuide.forAssistant("qwerty zxcv").isEmpty())
    }

    @Test fun everyEntryIsComplete() {
        assertEquals(HelpGuide.items.size, HelpGuide.search("  ").size)
        HelpGuide.items.forEach { assertTrue(it.title, it.text.isNotBlank() && it.where.isNotBlank()) }
        assertEquals("No two entries share a title", HelpGuide.items.size, HelpGuide.items.map { it.title }.distinct().size)
    }
}
