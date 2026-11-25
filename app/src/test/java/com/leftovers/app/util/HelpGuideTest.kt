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

    @Test fun theNewerFeaturesAreThere() {
        fun finds(question: String, title: String) = assertTrue(question, HelpGuide.forAssistant(question).any { it.title == title })
        finds("can I hide my balance for a screenshot", "Hide amounts")
        finds("is there a calculator when adding", "Sums on the keypad")
        finds("where is the groceries list", "Shopping list")
        finds("what is my financial health score", "Financial health")
        finds("how do I search for food over 500 last month", "Search and filters")
        finds("add a detected payment from sms", "Detected payments")
        finds("am I spending too fast", "Am I spending too fast?")
        finds("app icon shortcut", "Shortcuts on the app icon")
    }

    @Test fun everyEntryIsComplete() {
        assertEquals(HelpGuide.items.size, HelpGuide.search("  ").size)
        HelpGuide.items.forEach { assertTrue(it.title, it.text.isNotBlank() && it.where.isNotBlank()) }
        assertEquals("No two entries share a title", HelpGuide.items.size, HelpGuide.items.map { it.title }.distinct().size)
    }
}
