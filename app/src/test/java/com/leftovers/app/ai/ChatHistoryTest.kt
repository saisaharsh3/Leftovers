package com.leftovers.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A chat carried over to an AI that hasn't seen it, shaped the way every provider accepts it. */
class ChatHistoryTest {
    @Test
    fun `turns alternate, start with the user and end with the AI`() {
        val h = normalised(
            listOf(
                PastTurn(false, "A note from the AI before anything"),
                PastTurn(true, "Who owes me money?"),
                PastTurn(false, "Anil owes you ₹1,550."),
                PastTurn(false, "(Change applied: Settled with Anil)"),
                PastTurn(true, "Thanks"),
                PastTurn(true, "And Rohit?"),
            ),
        )
        assertEquals(listOf(true, false, true, false), h.map { it.user })
        assertEquals("Who owes me money?", h[0].text)
        assertTrue("Settled with Anil" in h[1].text)
        assertEquals("Thanks\n\nAnd Rohit?", h[2].text)
        assertEquals("(No answer was given to that.)", h[3].text)
    }

    @Test
    fun `nothing to carry when there's nothing said`() {
        assertEquals(emptyList<PastTurn>(), normalised(listOf(PastTurn(false, "Only a note"), PastTurn(true, "  "))))
    }

    @Test
    fun `the phone's own AI reads the earlier messages, newest kept when they don't all fit`() {
        val session = OnDeviceSession("You are the assistant. Today is 2026-11-12.", emptyList()) { null }
        session.seed(listOf(PastTurn(true, "Who owes me money?"), PastTurn(false, "Anil owes you ₹1,550.")))
        val prompt = session.prompt(4_000)
        assertTrue("User: Who owes me money?" in prompt)
        assertTrue("Assistant: Anil owes you ₹1,550." in prompt)

        val long = OnDeviceSession("Facts.", emptyList()) { null }
        long.seed((1..400).flatMap { listOf(PastTurn(true, "Question $it about my spending this month"), PastTurn(false, "Answer $it")) })
        val small = long.prompt(1_000)
        assertTrue("Answer 400" in small)
        assertTrue("Question 1 about" !in small)
    }
}
